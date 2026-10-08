package com.routerevive.app

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

data class CloudSettings(val url: String, val publicKey: String) {
    fun valid(): Boolean {
        // Confine user credentials to official Supabase project hosts.
        val host = Regex("^https://[a-z0-9-]{4,80}\\.supabase\\.co$")
        return host.matches(url.trimEnd('/')) &&
            publicKey.length in 30..2500 &&
            !publicKey.contains("service_role", ignoreCase = true) &&
            !publicKey.contains("sb_secret_", ignoreCase = true) &&
            !publicKey.contains(" ") && !publicKey.contains("\n")
    }
    fun normalized() = copy(url = url.trimEnd('/'), publicKey = publicKey.trim())
}

data class CloudSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String
)

data class CloudBackupFile(val name: String, val size: Long, val createdAt: String)

object CloudRules {
    const val BUCKET = "route-revive-backups"
    const val MAX_CLOUD_BYTES = 165L * 1024L * 1024L
    private val uid = Regex("^[a-f0-9-]{36}$")
    private val archive = Regex("^backup-[0-9]{8}T[0-9]{6}-[a-f0-9-]{36}\\.rrb$")
    fun validUser(id: String): Boolean = uid.matches(id)
    fun validName(name: String): Boolean = archive.matches(name)
    fun path(id: String, name: String): String {
        require(validUser(id) && validName(name)) { "Invalid cloud backup identity." }
        return id + "/" + name
    }
    fun newName(): String {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
            .withZone(ZoneOffset.UTC).format(Instant.now())
        return "backup-" + timestamp + "-" + UUID.randomUUID() + ".rrb"
    }
}

/** Only Keystore-encrypted refresh credentials are persisted; never account passwords. */
class CloudVault(context: Context) {
    private val prefs = context.getSharedPreferences("routerevive_cloud_v1", Context.MODE_PRIVATE)
    private val alias = "routerevive_cloud_credentials_v1"

    private fun secret(): javax.crypto.SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    private fun seal(raw: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        return Base64.encodeToString(cipher.iv +
            cipher.doFinal(raw.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun open(raw: String): String {
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        require(bytes.size >= 29) { "Encrypted cloud credentials are incomplete." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }

    fun settings(): CloudSettings = CloudSettings(
        prefs.getString("url", "").orEmpty(), prefs.getString("public_key", "").orEmpty())

    fun saveSettings(config: CloudSettings) {
        require(config.valid()) { "Enter a valid HTTPS Supabase project URL and public key." }
        val previous = settings()
        val normalized = config.normalized()
        val editor = prefs.edit()
            .putString("url", normalized.url).putString("public_key", normalized.publicKey)
        if (previous != normalized) editor.remove("auth")
        check(editor.commit()) { "Failed to save cloud configuration." }
    }

    fun saveSession(session: CloudSession) {
        require(CloudRules.validUser(session.userId) &&
            session.refreshToken.isNotBlank()) { "Invalid cloud login session." }
        val data = JSONObject()
            .put("refresh_token", session.refreshToken)
            .put("user_id", session.userId)
            .put("email", session.email)
        check(prefs.edit().putString("auth", seal(data.toString())).commit()) {
            "Could not store cloud credentials."
        }
    }

    fun storedIdentity(): Pair<String, String>? {
        val value = prefs.getString("auth", null) ?: return null
        return runCatching {
            JSONObject(open(value)).let { j -> j.getString("user_id") to j.optString("email") }
        }.getOrNull()
    }

    fun refreshToken(): String? =
        prefs.getString("auth", null)?.let { encoded ->
            runCatching { JSONObject(open(encoded)).getString("refresh_token") }.getOrNull()
        }

    fun signOut() {
        check(prefs.edit().remove("auth").commit()) { "Sign out could not clear the session." }
    }
}

/** All operations must be performed off the main thread; no implicit network calls. */
class SupabaseCloud(private val settings: CloudSettings) {
    init { require(settings.valid()) { "Connect a valid Supabase project first." } }

    private fun request(
        method: String, route: String, bearer: String? = null,
        json: JSONObject? = null, upload: File? = null, download: File? = null
    ): JSONObject {
        require(route.startsWith("/auth/v1/") || route.startsWith("/storage/v1/")) {
            "Unsupported cloud API route"
        }
        val conn = URL(settings.url.trimEnd('/') + route).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("apikey", settings.publicKey)
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer " + bearer)
            if (json != null) {
                val body = json.toString().toByteArray(Charsets.UTF_8)
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            } else if (upload != null) {
                require(upload.isFile && upload.length() in 1..CloudRules.MAX_CLOUD_BYTES) {
                    "Encrypted backup is missing or exceeds the cloud size limit."
                }
                conn.setRequestProperty("Content-Type", "application/octet-stream")
                conn.setRequestProperty("cache-control", "no-store")
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(upload.length())
                conn.outputStream.use { out -> upload.inputStream().use { it.copyTo(out, 32 * 1024) } }
            }
            val status = conn.responseCode
            if (status !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText().take(4000) } ?: ""
                val body = runCatching { JSONObject(errorBody) }.getOrNull()
                val description = body?.optString("msg")?.ifBlank { null }
                    ?: body?.optString("message")?.ifBlank { null }
                    ?: body?.optString("error_description")?.ifBlank { null }
                    ?: when (status) {
                        400 -> "Check the login or request."
                        401 -> "Cloud login expired; sign in again."
                        403 -> "Storage permission denied; check the setup SQL."
                        404 -> "Private cloud bucket or backup was not found."
                        409 -> "Cloud backup already exists."
                        413 -> "Backup exceeds configured cloud size limits."
                        429 -> "Too many requests; try later."
                        in 500..599 -> "Supabase temporarily unavailable."
                        else -> "Unexpected server response."
                    }
                error("Cloud request failed (" + status + "): " + description)
            }
            if (download != null) {
                val length = conn.contentLengthLong
                require(length <= CloudRules.MAX_CLOUD_BYTES || length < 0) {
                    "Cloud backup is too large."
                }
                var amount = 0L
                conn.inputStream.use { input ->
                    download.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            amount += n
                            require(amount <= CloudRules.MAX_CLOUD_BYTES) {
                                "Cloud backup exceeded size limit."
                            }
                            output.write(buffer, 0, n)
                        }
                    }
                }
                return JSONObject().put("bytes", amount)
            }
            val text = conn.inputStream.bufferedReader().use { it.readText().take(200_000) }
            return if (text.trim().startsWith("[")) JSONObject().put("items", JSONArray(text))
                   else if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally { conn.disconnect() }
    }

    private fun responseSession(j: JSONObject, fallbackEmail: String): CloudSession? {
        val access = j.optString("access_token")
        val refresh = j.optString("refresh_token")
        val user = j.optJSONObject("user") ?: return null
        val id = user.optString("id")
        if (access.isBlank() || refresh.isBlank() || !CloudRules.validUser(id)) return null
        return CloudSession(id, user.optString("email", fallbackEmail), access, refresh)
    }

    fun signUp(email: String, password: String): CloudSession? =
        responseSession(request("POST", "/auth/v1/signup",
            json = JSONObject().put("email", email).put("password", password)), email)

    fun signIn(email: String, password: String): CloudSession {
        val result = request("POST", "/auth/v1/token?grant_type=password",
            json = JSONObject().put("email", email).put("password", password))
        return responseSession(result, email) ?: error("No login session returned.")
    }

    fun refresh(token: String): CloudSession {
        val result = request("POST", "/auth/v1/token?grant_type=refresh_token",
            json = JSONObject().put("refresh_token", token))
        return responseSession(result, "") ?: error("Session expired; sign in again.")
    }

    fun requestPasswordReset(email: String) {
        request("POST", "/auth/v1/recover",
            json = JSONObject().put("email", email))
    }

    fun upload(session: CloudSession, file: File, name: String) {
        val path = CloudRules.path(session.userId, name)
        request("POST", "/storage/v1/object/" + CloudRules.BUCKET + "/" + path,
            bearer = session.accessToken, upload = file)
    }

    fun backups(session: CloudSession): List<CloudBackupFile> {
        val result = request("POST", "/storage/v1/object/list/" + CloudRules.BUCKET,
            bearer = session.accessToken,
            json = JSONObject().put("prefix", session.userId + "/")
                .put("limit", 50).put("offset", 0)
                .put("sortBy", JSONObject().put("column", "created_at").put("order", "desc")))
        val list = result.optJSONArray("items") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val obj = list.optJSONObject(i) ?: return@mapNotNull null
            val name = obj.optString("name")
            if (!CloudRules.validName(name)) return@mapNotNull null
            CloudBackupFile(name, obj.optJSONObject("metadata")?.optLong("size") ?: 0L,
                obj.optString("created_at"))
        }
    }

    fun download(session: CloudSession, name: String, file: File) {
        val path = CloudRules.path(session.userId, name)
        request("GET", "/storage/v1/object/authenticated/" + CloudRules.BUCKET + "/" + path,
            bearer = session.accessToken, download = file)
    }
}

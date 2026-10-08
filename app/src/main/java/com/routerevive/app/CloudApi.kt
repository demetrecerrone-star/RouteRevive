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
        val key = publicKey.trim()
        val acceptableKey = if (key.startsWith("sb_publishable_")) {
            key.length in 30..2500
        } else if (key.startsWith("eyJ") && key.count { it == '.' } == 2) {
            // Refuse legacy service_role JWTs; only legacy anon keys are permitted.
            runCatching {
                val payload = String(java.util.Base64.getUrlDecoder()
                    .decode(key.split('.')[1]), Charsets.UTF_8)
                JSONObject(payload).optString("role") == "anon"
            }.getOrDefault(false)
        } else false
        return host.matches(url.trimEnd('/')) &&
            key.length in 30..2500 && acceptableKey &&
            !key.contains(" ") && !key.contains("\n")
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
    private const val AUTO_MARKER = "a170bac0"
    fun isAutomaticName(name: String): Boolean = validName(name) &&
        name.substringAfterLast("T").substringAfter("-").startsWith(AUTO_MARKER)

    fun newName(automatic: Boolean = false): String {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
            .withZone(ZoneOffset.UTC).format(Instant.now())
        val uuid = UUID.randomUUID().toString()
        val id = if (automatic) AUTO_MARKER + uuid.substring(8) else uuid
        return "backup-" + timestamp + "-" + id + ".rrb"
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
        if (previous != normalized) {
            editor.remove("auth").remove("auto_password").remove("auto_hours")
                .remove("sync_name").remove("sync_hash")
        }
        check(editor.commit()) { "Failed to save cloud configuration." }
    }

    fun saveSession(session: CloudSession) {
        require(CloudRules.validUser(session.userId) &&
            session.refreshToken.isNotBlank()) { "Invalid cloud login session." }
        if (storedIdentity()?.first?.let { it != session.userId } == true) {
            disableAutomatic(clearBaseline = true)
        }
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

    fun automaticEnabled(): Boolean = storedIdentity() != null &&
        prefs.contains("auto_password") && autoHours() > 0

    fun autoHours(): Long = prefs.getLong("auto_hours", 0L).takeIf {
        it == 24L || it == 168L
    } ?: 0L

    fun enableAutomatic(password: CharArray, intervalHours: Long) {
        require(intervalHours == 24L || intervalHours == 168L)
        require(password.size >= 10) { "Choose a backup password with at least 10 characters." }
        require(storedIdentity() != null) { "Sign in first." }
        check(prefs.edit().putString("auto_password", seal(String(password)))
            .putLong("auto_hours", intervalHours).commit()) {
            "Unable to save protected backup credentials."
        }
    }

    fun automaticPassword(): CharArray? {
        if (!automaticEnabled()) return null
        return prefs.getString("auto_password", null)?.let { runCatching {
            open(it).toCharArray()
        }.getOrNull() }
    }

    fun disableAutomatic(clearBaseline: Boolean = false) {
        val editor = prefs.edit().remove("auto_password").remove("auto_hours")
        if (clearBaseline) editor.remove("sync_name").remove("sync_hash")
        check(editor.commit()) { "Unable to disable automatic backups." }
    }

    fun syncBaseline(): Pair<String, String>? {
        val name = prefs.getString("sync_name", null) ?: return null
        val hash = prefs.getString("sync_hash", null) ?: return null
        if (!CloudRules.isAutomaticName(name) || !hash.matches(Regex("[0-9a-f]{64}"))) return null
        return name to hash
    }

    fun recordSynced(name: String, fingerprint: String) {
        require(CloudRules.isAutomaticName(name) && fingerprint.matches(Regex("[0-9a-f]{64}")))
        check(prefs.edit().putString("sync_name", name).putString("sync_hash", fingerprint)
            .putLong("sync_success_at", System.currentTimeMillis())
            .putString("sync_status", "Last cloud synchronization completed.").commit()) {
            "Unable to record successful cloud synchronization."
        }
    }

    fun recordSyncStatus(status: String) {
        check(prefs.edit().putString("sync_status", status.take(180)).commit())
    }

    fun lastSyncStatus(): String = prefs.getString("sync_status", "").orEmpty()
    fun lastSyncTime(): Long = prefs.getLong("sync_success_at", 0L)

    fun clearSyncBaseline() {
        check(prefs.edit().remove("sync_name").remove("sync_hash").commit())
    }

    fun signOut() {
        disableAutomatic(clearBaseline = true)
        check(prefs.edit().remove("auth").commit()) { "Sign out could not clear the session." }
    }
}

/** Prevent simultaneous foreground/background refresh-token rotation in this process. */
object CloudAuth {
    @Synchronized
    fun refresh(vault: CloudVault, settings: CloudSettings): CloudSession {
        val token = vault.refreshToken() ?: error("Sign in to access private cloud backups.")
        return SupabaseCloud(settings).refresh(token).also { vault.saveSession(it) }
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

    /** Paged listing is essential: older automatic snapshots must not be mistaken for none. */
    fun latestAutomatic(session: CloudSession): CloudBackupFile? {
        var offset = 0
        while (offset < 2000) {
            val response = request("POST", "/storage/v1/object/list/" + CloudRules.BUCKET,
                bearer = session.accessToken,
                json = JSONObject().put("prefix", session.userId + "/")
                    .put("limit", 100).put("offset", offset)
                    .put("sortBy", JSONObject().put("column", "created_at")
                        .put("order", "desc")))
            val rows = response.optJSONArray("items") ?: return null
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val name = row.optString("name")
                if (CloudRules.isAutomaticName(name)) {
                    return CloudBackupFile(name,
                        row.optJSONObject("metadata")?.optLong("size") ?: 0L,
                        row.optString("created_at"))
                }
            }
            if (rows.length() < 100) return null
            offset += 100
        }
        error("Cloud contains too many snapshots to determine the latest synchronized version safely.")
    }

    fun download(session: CloudSession, name: String, file: File) {
        val path = CloudRules.path(session.userId, name)
        request("GET", "/storage/v1/object/authenticated/" + CloudRules.BUCKET + "/" + path,
            bearer = session.accessToken, download = file)
    }
}

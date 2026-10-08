package com.routerevive.app

import android.content.Context
import org.json.JSONObject
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** A password-encrypted archive with authenticated AES-256-GCM and PBKDF2-SHA256.
 * Backups can be restored on another phone without transferring AndroidKeyStore keys.
 * The temporary plaintext ZIP is held only in app-private cache and removed after use.
 */
object BackupArchive {
    private val magic = byteArrayOf(0x52, 0x52, 0x42, 0x37) // RRB7
    private val fileName = Regex("[a-f0-9-]{36}\\.jpg")
    private const val maxBytes = 150L * 1024 * 1024
    private const val maxPhotoBytes = 8L * 1024 * 1024
    private const val maxPhotos = 1200
    private const val kdfRounds = 210_000

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size >= 10) { "Backup password must be at least 10 characters." }
        val spec = PBEKeySpec(password, salt, kdfRounds, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    private fun referencedPhotos(json: String): Set<String> {
        val data = JSONObject(json)
        val refs = mutableSetOf<String>()
        val jobs = data.optJSONArray("jobs")
        if (jobs != null) for (i in 0 until jobs.length()) {
            val photos = jobs.getJSONObject(i).optJSONArray("photos") ?: continue
            for (j in 0 until photos.length()) {
                val name = photos.getJSONObject(j).getString("filename")
                require(fileName.matches(name)) { "Invalid photo reference in backup." }
                refs += name
            }
        }
        val logo = data.optJSONObject("businessProfile")?.optString("logoFile") ?: ""
        if (logo.isNotBlank()) {
            require(fileName.matches(logo)) { "Invalid business logo reference." }
            refs += logo
        }
        require(refs.size <= maxPhotos) { "Too many photos in backup." }
        return refs
    }

    private fun temp(context: Context, suffix: String): File =
        File(context.cacheDir, "routerevive-" + UUID.randomUUID() + suffix)

    fun create(context: Context, store: LocalStore, password: CharArray, out: OutputStream) {
        val zipFile = temp(context, ".zip")
        try {
            val json = store.exportJson()
            val refs = referencedPhotos(json)
            require(json.toByteArray(StandardCharsets.UTF_8).size <= 6_000_000) {
                "Records are too large to back up."
            }
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
                zip.putNextEntry(ZipEntry("records.json"))
                zip.write(json.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
                for (name in refs) {
                    val photo = JobMedia.photoFile(context, name)
                        ?.takeIf { it.isFile } ?: error("Missing photo $name. Save a new photo or remove the broken reference.")
                    require(photo.length() in 1..maxPhotoBytes) { "Photo $name is too large or empty." }
                    zip.putNextEntry(ZipEntry("photos/" + name))
                    photo.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            require(zipFile.length() <= maxBytes) { "Backup exceeds 150 MB limit." }

            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            }
            out.write(magic); out.write(salt); out.write(iv)
            // CipherOutputStream.close adds the GCM authentication tag.
            CipherOutputStream(out, cipher).use { cipherOut ->
                zipFile.inputStream().buffered().use { it.copyTo(cipherOut) }
            }
        } finally { zipFile.delete() }
    }

    fun restore(context: Context, store: LocalStore, password: CharArray,
                input: InputStream) {
        val zipFile = temp(context, ".zip")
        val stage = File(context.cacheDir, "rr-restore-" + UUID.randomUUID())
        stage.mkdirs()
        try {
            val header = DataInputStream(input)
            val check = ByteArray(4).also { header.readFully(it) }
            require(check.contentEquals(magic)) { "Not a RouteRevive encrypted backup." }
            val salt = ByteArray(16).also { header.readFully(it) }
            val iv = ByteArray(12).also { header.readFully(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            }
            // Copy all the way to EOF so the GCM authentication tag is verified
            // before touching live data. Wrong passwords cannot alter records.
            CipherInputStream(header, cipher).use { clear ->
                FileOutputStream(zipFile).use { target ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = clear.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= maxBytes) { "Decrypted archive exceeds size limit." }
                        target.write(buffer, 0, read)
                    }
                }
            }
            var json: String? = null
            val received = mutableSetOf<String>()
            var count = 0
            var totalBytes = 0L
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    count++
                    require(count <= maxPhotos + 1) { "Too many archive entries." }
                    val entryName = entry.name
                    require(!entry.isDirectory) { "Unexpected directory in backup." }
                    require(entryName == "records.json" ||
                        (entryName.startsWith("photos/") && fileName.matches(entryName.removePrefix("photos/")))) {
                        "Invalid archive file path."
                    }
                    require(entryName == "records.json" || received.add(entryName.removePrefix("photos/"))) {
                        "Duplicate photo in backup."
                    }
                    require(!(entryName == "records.json" && json != null)) {
                        "Duplicate records file."
                    }
                    val outputFile = if (entryName == "records.json") {
                        File(stage, "records.json")
                    } else {
                        File(stage, entryName.removePrefix("photos/"))
                    }
                    FileOutputStream(outputFile).use { target ->
                        var fileBytes = 0L
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            fileBytes += read
                            totalBytes += read
                            require(fileBytes <= if (entryName == "records.json") 6_000_000L else maxPhotoBytes) {
                                "Archive entry exceeds file size limit."
                            }
                            require(totalBytes <= maxBytes) { "Archive expanded too large." }
                            target.write(buffer, 0, read)
                        }
                    }
                    if (entryName == "records.json") {
                        json = outputFile.readText(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                }
            }
            val payload = json ?: error("Backup records file is missing.")
            require(referencedPhotos(payload) == received) {
                "Photo files and record references do not match."
            }
            // Stage photos into app-private storage first; failures cannot
            // replace the active database. UUID filenames prevent path traversal.
            for (name in received) {
                val source = File(stage, name)
                val dest = JobMedia.photoFile(context, name) ?: error("Bad file name")
                if (!dest.exists()) {
                    source.copyTo(dest)
                }
            }
            // importJson checks all entities and rolls back on validation failures.
            store.importJson(payload)
        } finally {
            zipFile.delete()
            stage.deleteRecursively()
        }
    }
}

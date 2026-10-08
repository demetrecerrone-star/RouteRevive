package com.routerevive.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Snapshot sync is deliberately conservative. The client NEVER chooses a
 * winning device when both have modified records. Background work only pushes
 * encrypted versions; importing remote data always requires user confirmation.
 */
enum class CloudSyncChoice { UPLOAD, CURRENT, DOWNLOAD, CONFLICT }

object CloudSyncRules {
    fun decide(remote: String?, fingerprint: String, baseline: Pair<String, String>?): CloudSyncChoice {
        if (baseline == null) return if (remote == null) CloudSyncChoice.UPLOAD else CloudSyncChoice.DOWNLOAD
        if (remote == baseline.first) {
            return if (fingerprint == baseline.second) CloudSyncChoice.CURRENT else CloudSyncChoice.UPLOAD
        }
        return if (remote != null && fingerprint == baseline.second) CloudSyncChoice.DOWNLOAD
               else CloudSyncChoice.CONFLICT
    }
}

data class CloudSyncOutcome(val choice: CloudSyncChoice, val message: String,
                            val remoteName: String? = null)

object CloudSyncEngine {
    private val lock = Any()

    fun fingerprint(store: LocalStore): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(store.exportJson().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** All delete operations share the sync lock; no foreground cleanup races with a local upload. */
    fun deleteOldSnapshot(vault: CloudVault, expectedName: String): Unit =
        synchronized(lock) {
            require(CloudRules.validName(expectedName)) { "Invalid cloud backup name." }
            val settings = vault.settings()
            require(settings.valid()) { "Configure your private cloud account first." }
            val session = CloudAuth.refresh(vault, settings)
            val cloud = SupabaseCloud(settings)
            val all = cloud.allBackups(session)
            check(CloudRetentionRules.mayDelete(all, expectedName, vault.syncBaseline()?.first)) {
                "This snapshot is protected (latest, sync baseline, or last remaining backup). " +
                    "Only older snapshots can be deleted."
            }
            cloud.delete(session, expectedName)
        }

    fun cleanupNow(vault: CloudVault): Int = synchronized(lock) {
        require(vault.automaticEnabled()) { "Enable automatic backups to use retention." }
        val keep = vault.retentionKeep()
        require(keep > 0) { "Turn on a retention limit first." }
        val session = CloudAuth.refresh(vault, vault.settings())
        prune(SupabaseCloud(vault.settings()), session, vault)
    }

    private fun prune(cloud: SupabaseCloud, session: CloudSession, vault: CloudVault): Int {
        val keep = vault.retentionKeep()
        if (keep == 0) return 0
        val all = cloud.allBackups(session)
        val candidates = CloudRetentionRules.automaticPrune(all, keep, vault.syncBaseline()?.first)
        // Per-file REST calls are slower but preserve a clear failure boundary.
        candidates.forEach { cloud.delete(session, it.name) }
        return candidates.size
    }

    private fun retentionResult(
        cloud: SupabaseCloud, session: CloudSession, vault: CloudVault,
        choice: CloudSyncChoice, message: String, remote: String?
    ): CloudSyncOutcome {
        if (vault.retentionKeep() == 0) return CloudSyncOutcome(choice, message, remote)
        return try {
            val pruned = prune(cloud, session, vault)
            CloudSyncOutcome(choice, message +
                (if (pruned > 0) " Removed $pruned older automatic backup(s)." else ""), remote)
        } catch (e: Exception) {
            CloudSyncOutcome(choice, message + " Cleanup needs attention: " +
                (e.message ?: "Check cloud storage permissions."), remote)
        }
    }

    fun syncOnce(context: Context, store: LocalStore, vault: CloudVault): CloudSyncOutcome =
        synchronized(lock) {
            require(vault.automaticEnabled()) { "Enable automatic backups first." }
            val settings = vault.settings()
            require(settings.valid()) { "Connect the private cloud project first." }
            val password = vault.automaticPassword()
                ?: error("Stored backup password is unavailable. Disable and set up automatic backups again.")
            try {
                val signed = CloudAuth.refresh(vault, settings)
                val cloud = SupabaseCloud(settings)
                val remote = cloud.latestAutomatic(signed)?.name
                val local = fingerprint(store)
                val choice = CloudSyncRules.decide(remote, local, vault.syncBaseline())
                when (choice) {
                    CloudSyncChoice.CURRENT -> retentionResult(
                        cloud, signed, vault, choice, "Device is up to date.", remote)
                    CloudSyncChoice.DOWNLOAD -> CloudSyncOutcome(choice,
                        "Newer encrypted cloud data is available. Open Cloud and confirm import.", remote)
                    CloudSyncChoice.CONFLICT -> CloudSyncOutcome(choice,
                        "Changes exist on multiple devices, or cloud history changed. Review before restoring; nothing was overwritten.", remote)
                    CloudSyncChoice.UPLOAD -> {
                        val file = File(context.cacheDir, "rr-auto-" + UUID.randomUUID() + ".rrb")
                        try {
                            file.outputStream().use { BackupArchive.create(context, store, password, it) }
                            // Never mark a snapshot current if the local database changed during ZIP creation.
                            check(fingerprint(store) == local) {
                                "Records changed during backup; will retry on the next run."
                            }
                            // Narrow the race with another device uploading during encryption.
                            check(cloud.latestAutomatic(signed)?.name == remote) {
                                "Cloud was updated during this backup. Open Cloud to sync safely."
                            }
                            val newName = CloudRules.newName(automatic = true)
                            cloud.upload(signed, file, newName)
                            vault.recordSynced(newName, local)
                            retentionResult(cloud, signed, vault, choice,
                                "Encrypted cloud backup uploaded successfully.", newName)
                        } finally { file.delete() }
                    }
                }
            } finally { password.fill('\u0000') }
        }

    /** Requires a separate, explicit confirmation on the UI before being called. */
    fun receive(context: Context, store: LocalStore, vault: CloudVault, expectedName: String) =
        synchronized(lock) {
            require(vault.automaticEnabled()) { "Automatic cloud backups are disabled." }
            require(CloudRules.isAutomaticName(expectedName)) { "Invalid synchronized snapshot." }
            val password = vault.automaticPassword()
                ?: error("Stored backup password is unavailable.")
            val file = File(context.cacheDir, "rr-sync-receive-" + UUID.randomUUID() + ".rrb")
            try {
                val signed = CloudAuth.refresh(vault, vault.settings())
                val cloud = SupabaseCloud(vault.settings())
                require(cloud.latestAutomatic(signed)?.name == expectedName) {
                    "The cloud changed. Check synchronization again before importing."
                }
                val choice = CloudSyncRules.decide(expectedName, fingerprint(store), vault.syncBaseline())
                require(choice == CloudSyncChoice.DOWNLOAD) {
                    "Local records have changed. The app will not overwrite them automatically."
                }
                cloud.download(signed, expectedName, file)
                file.inputStream().use { BackupArchive.restore(context, store, password, it) }
                vault.recordSynced(expectedName, fingerprint(store))
            } finally {
                password.fill('\u0000')
                file.delete()
            }
        }
}

class CloudBackupWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork() = withContext(Dispatchers.IO) {
        val vault = CloudVault(applicationContext)
        if (!vault.automaticEnabled()) return@withContext Result.success()
        try {
            val outcome = CloudSyncEngine.syncOnce(applicationContext, LocalStore(applicationContext), vault)
            vault.recordSyncStatus(outcome.message)
            Result.success()
        } catch (e: Exception) {
            vault.recordSyncStatus("Backup needs attention: " + (e.message ?: "Try again in the app."))
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object CloudBackupScheduler {
    private const val PERIODIC = "routerevive-automatic-cloud"
    private const val IMMEDIATE = "routerevive-cloud-sync-now"
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context, intervalHours: Long) {
        require(intervalHours == 24L || intervalHours == 168L)
        val manager = WorkManager.getInstance(context.applicationContext)
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(
            intervalHours, TimeUnit.HOURS
        ).setConstraints(constraints).build()
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        runNow(context)
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CloudBackupWorker>().setConstraints(constraints).build()
        )
    }

    fun stop(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        manager.cancelUniqueWork(PERIODIC)
        manager.cancelUniqueWork(IMMEDIATE)
    }
}

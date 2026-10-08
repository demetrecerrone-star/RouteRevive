package com.routerevive.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Conservative cleanup of cloud archives. Only automatic snapshots can be auto-pruned. */
object CloudRetentionRules {
    val choices = listOf(0, 10, 30, 60)
    fun allowed(value: Int): Boolean = value in choices

    /** Files are given newest-first. Protect current cloud head and this device's sync baseline. */
    fun automaticPrune(
        all: List<CloudBackupFile>, keep: Int, baselineName: String?
    ): List<CloudBackupFile> {
        require(allowed(keep)) { "Unsupported retention choice." }
        if (keep == 0) return emptyList()
        val sorted = all.filter { CloudRules.validName(it.name) }
            .sortedByDescending { it.name }
        val autos = sorted.filter { CloudRules.isAutomaticName(it.name) }
        val reserved = (autos.take(keep).map { it.name } +
            listOfNotNull(sorted.firstOrNull()?.name, autos.firstOrNull()?.name, baselineName)).toSet()
        return autos.drop(keep).filterNot { it.name in reserved }
    }

    /** Manual deletion always retains the newest archive, newest synced archive and local baseline. */
    fun mayDelete(
        all: List<CloudBackupFile>, name: String, baselineName: String?
    ): Boolean {
        if (!CloudRules.validName(name) || all.none { it.name == name }) return false
        val sorted = all.filter { CloudRules.validName(it.name) }.sortedByDescending { it.name }
        if (sorted.size < 2 || name == sorted.first().name || name == baselineName) return false
        if (name == sorted.firstOrNull { CloudRules.isAutomaticName(it.name) }?.name) return false
        return true
    }
}

enum class CloudHealthState { DISABLED, WAITING, HEALTHY, ACTION_REQUIRED, STALE }

data class CloudHealth(val state: CloudHealthState, val title: String, val detail: String)

object CloudHealthRules {
    fun evaluate(enabled: Boolean, status: String, lastSync: Long,
                 hours: Long, now: Long = System.currentTimeMillis()): CloudHealth {
        if (!enabled) return CloudHealth(CloudHealthState.DISABLED, "Manual backups only",
            "Turn on automatic backups in More → Private cloud backups.")
        if (status.startsWith("Backup needs attention:", ignoreCase = true) ||
            status.contains("Changes exist on multiple devices", ignoreCase = true) ||
            status.contains("cleanup needs attention", ignoreCase = true)) {
            return CloudHealth(CloudHealthState.ACTION_REQUIRED, "Cloud needs attention",
                status.take(150))
        }
        if (status.contains("Newer encrypted cloud data", ignoreCase = true)) {
            return CloudHealth(CloudHealthState.ACTION_REQUIRED, "Import waiting for approval",
                "Open Private cloud backups to review newer data before replacing this device.")
        }
        if (lastSync <= 0L) return CloudHealth(CloudHealthState.WAITING, "First backup pending",
            "Automatic backups are enabled. Run Check & sync now to start.")
        val permittedHours = if (hours == 168L) 9 * 24L else 3 * 24L
        if (now - lastSync > TimeUnit.HOURS.toMillis(permittedHours)) {
            return CloudHealth(CloudHealthState.STALE, "Backup check overdue",
                "This device has not confirmed a cloud sync recently. Check its connection.")
        }
        return CloudHealth(CloudHealthState.HEALTHY, "Cloud checked",
            "Last confirmed sync: " + DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(lastSync)))
    }
}

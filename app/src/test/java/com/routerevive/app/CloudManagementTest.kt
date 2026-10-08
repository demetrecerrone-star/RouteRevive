package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class CloudManagementTest {
    private fun snapshot(index: Int, automatic: Boolean): CloudBackupFile {
        val day = LocalDate.of(2026, 10, 8).minusDays(index.toLong())
            .format(DateTimeFormatter.BASIC_ISO_DATE)
        val prefix = if (automatic) "a170bac0" else "11111111"
        val name = "backup-${day}T120000-${prefix}-0000-4000-8000-" +
            "%012d".format(index) + ".rrb"
        assertTrue(CloudRules.validName(name))
        return CloudBackupFile(name, 1000, "2026-10-08T12:00:00Z")
    }

    @Test fun cleanupDisabledByDefaultAndSettingsWhitelist() {
        val all = (0..13).map { snapshot(it, true) }
        assertTrue(CloudRetentionRules.automaticPrune(all, 0, null).isEmpty())
        assertTrue(CloudRetentionRules.allowed(0))
        assertTrue(CloudRetentionRules.allowed(10))
        assertFalse(CloudRetentionRules.allowed(1))
        assertThrows(IllegalArgumentException::class.java) {
            CloudRetentionRules.automaticPrune(all, 1, null)
        }
    }

    @Test fun onlyOldAutomaticSnapshotsRemovedAndBaselineProtected() {
        val autos = (0..13).map { snapshot(it, true) }
        val manual = snapshot(14, false)
        val all = autos + manual
        val baseline = autos[12].name
        val pruned = CloudRetentionRules.automaticPrune(all, 10, baseline)
        assertEquals(3, pruned.size)
        assertFalse(pruned.any { it.name == baseline || it.name == autos[0].name })
        assertFalse(pruned.any { it.name == manual.name })
        assertTrue(pruned.all { CloudRules.isAutomaticName(it.name) })
    }

    @Test fun mostRecentOverallAndAutomaticArchiveCannotBeManuallyRemoved() {
        val oldAuto = snapshot(3, true)
        val latestAuto = snapshot(1, true)
        val mostRecentManual = snapshot(0, false)
        val all = listOf(oldAuto, latestAuto, mostRecentManual)
        assertFalse(CloudRetentionRules.mayDelete(all, mostRecentManual.name, null))
        assertFalse(CloudRetentionRules.mayDelete(all, latestAuto.name, null))
        assertTrue(CloudRetentionRules.mayDelete(all, oldAuto.name, null))
        assertFalse(CloudRetentionRules.mayDelete(all, oldAuto.name, oldAuto.name))
        assertFalse(CloudRetentionRules.mayDelete(all, "../bad.rrb", null))
        assertFalse(CloudRetentionRules.mayDelete(all, "missing", null))
        assertFalse(CloudRetentionRules.mayDelete(listOf(latestAuto), latestAuto.name, null))
    }

    @Test fun healthDoesNotImplyCloudIsLiveAndFlagsProblems() {
        val now = TimeUnit.DAYS.toMillis(20000L)
        assertEquals(CloudHealthState.DISABLED,
            CloudHealthRules.evaluate(false, "", 0, 24L, now).state)
        assertEquals(CloudHealthState.WAITING,
            CloudHealthRules.evaluate(true, "", 0, 24L, now).state)
        assertEquals(CloudHealthState.ACTION_REQUIRED,
            CloudHealthRules.evaluate(true, "Backup needs attention: Permission denied",
                now, 24L, now).state)
        assertEquals(CloudHealthState.ACTION_REQUIRED,
            CloudHealthRules.evaluate(true,
                "Encrypted cloud backup uploaded. Cleanup needs attention: 403",
                now, 24L, now).state)
        assertEquals(CloudHealthState.STALE,
            CloudHealthRules.evaluate(true, "Device is up to date.",
                now - TimeUnit.DAYS.toMillis(4), 24L, now).state)
        assertEquals(CloudHealthState.HEALTHY,
            CloudHealthRules.evaluate(true, "Device is up to date.",
                now - TimeUnit.HOURS.toMillis(2), 24L, now).state)
    }
}

package com.routerevive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticCloudSyncRulesTest {
    private val first = "backup-20261008T120000-a170bac0-0000-4000-8000-000000000001.rrb"
    private val next = "backup-20261008T130000-a170bac0-0000-4000-8000-000000000002.rrb"

    @Test fun firstDeviceCreatesInitialEncryptedBackup() {
        assertEquals(CloudSyncChoice.UPLOAD, CloudSyncRules.decide(null, "local-v1", null))
    }

    @Test fun secondDeviceMustConfirmImportInsteadOfUploadingOverCloud() {
        assertEquals(CloudSyncChoice.DOWNLOAD, CloudSyncRules.decide(first, "unlinked-phone", null))
    }

    @Test fun unchangedLocalStateDoesNotCreateDuplicateBackups() {
        assertEquals(CloudSyncChoice.CURRENT, CloudSyncRules.decide(first, "v1", first to "v1"))
    }

    @Test fun localChangeCanUploadWhenRemoteMatchesBaseline() {
        assertEquals(CloudSyncChoice.UPLOAD, CloudSyncRules.decide(first, "v2", first to "v1"))
    }

    @Test fun newerRemoteCanBeSafelyOfferedForExplicitImport() {
        assertEquals(CloudSyncChoice.DOWNLOAD, CloudSyncRules.decide(next, "v1", first to "v1"))
    }

    @Test fun divergentDevicesNeverAutomaticallyReplaceEachOther() {
        assertEquals(CloudSyncChoice.CONFLICT, CloudSyncRules.decide(next, "v2", first to "v1"))
    }

    @Test fun deletionOfRemoteHistoryIsTreatedAsConflict() {
        assertEquals(CloudSyncChoice.CONFLICT, CloudSyncRules.decide(null, "v1", first to "v1"))
    }
}

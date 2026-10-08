package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class BetaIntegrityTest {
    private fun customer(id: String) = Customer(
        id = id, name = "Test $id", phone = "61255501" + id.padStart(2,'0'),
        zip = "55401", service = "Exterior wash", lastService = "2025-01-01"
    )
    private fun appointment(id: String, customerId: String, time: String = "09:00") =
        Appointment(id = id, customerId = customerId, service = "Exterior wash",
            date = "2026-10-20", time = time)

    @Test fun reportsHealthyLocalDataWithoutPrivateDetails() {
        val c = customer("01")
        val a = appointment("apt-1", c.id)
        val job = JobRecord(appointmentId = a.id, customerId = c.id,
            lineItems = listOf(JobLineItem("Wash", 1.0, 200.0)))
        val status = BetaIntegrity.audit(listOf(c), listOf(a), listOf(job),
            emptyList(), emptyList(), BusinessProfile(), { true })
        assertTrue(status.healthy)
        assertEquals(1, status.customerCount)
        assertEquals(1, status.jobCount)
    }

    @Test fun flagsMissingReferencesAndMissingPhotosWithoutExposingCustomerNames() {
        val c = customer("01")
        val ap = appointment("appointment", "missingCustomer")
        val job = JobRecord(appointmentId = ap.id, customerId = c.id,
            photos = listOf(JobPhoto("00000000-0000-0000-0000-000000000000.jpg", "BEFORE")))
        val status = BetaIntegrity.audit(listOf(c), listOf(ap), listOf(job),
            emptyList(), emptyList(), BusinessProfile(), { false })
        assertFalse(status.healthy)
        assertTrue(status.issues.any { it.contains("missing customers") })
        assertTrue(status.issues.any { it.contains("photos") })
        assertFalse(status.issues.toString().contains(c.name))
    }

    @Test fun detectsOverlappingJobsAndDuplicateIds() {
        val c = customer("01")
        val a = appointment("same", c.id)
        val b = appointment("different", c.id, "09:30")
        val status = BetaIntegrity.audit(listOf(c,c),
            listOf(a,b), emptyList(), emptyList(), emptyList(), BusinessProfile())
        assertTrue(status.issues.any { it.contains("Duplicate customer") })
        assertTrue(status.issues.any { it.contains("overlapping") })
    }

    @Test fun backupValidationAcceptsLegacyMinimalShapeAndRejectsBrokenRecords() {
        BackupValidator.check("""{"schemaVersion":2,"customers":[],"campaigns":[]}""")
        BackupValidator.check("""{"schemaVersion":7,"customers":[],"campaigns":[],"jobs":[],"bookingRequests":[]}""")
        assertThrows(IllegalArgumentException::class.java) {
            BackupValidator.check("""{"schemaVersion":7,"customers":[],"campaigns":[],"jobs":{}}""")
        }
        assertThrows(Exception::class.java) {
            BackupValidator.check("""{"schemaVersion":7,"customers":[{"id":"a"}],"campaigns":[]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupValidator.check("""{"schemaVersion":99,"customers":[],"campaigns":[]}""")
        }
    }

    @Test fun existingConsentEvidenceDoesNotInvalidateAnOptOut() {
        val c = customer("01").copy(consent = true,
            optedOut = true, consentEvidence = "Previously allowed")
        val audit = BetaIntegrity.audit(listOf(c), emptyList(), emptyList(),
            emptyList(), emptyList(), BusinessProfile())
        assertTrue(audit.healthy)
        assertTrue(CampaignRules.reasons(c, c.zip, c.service,
            java.time.LocalDate.of(2026,10,8), listOf(c), emptyList())
            .contains("Opted out"))
    }
}

package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BusinessLogicTest {
    @Test fun csvHandlesQuotedCommasAndNeverInheritsMarketingConsent() {
        val csv = "name,phone,zip,service,lastServiceDate,address,notes\n" +
            "\"Doe, Jane\",6125550131,56401,Driveway,2026-01-01,\"7 North St, Unit B\",\"Asked for \"\"light\"\" wash\"\n" +
            "Duplicate,6125550131,56401,Driveway,2026-01-01,,\n" +
            "MissingZip,6125550102,blah,Driveway,2026-01-01,,\n"
        val parsed = BusinessLogic.importCsv(csv, emptyList(),
            LocalDate.of(2026, 10, 8))
        assertEquals(1, parsed.customers.size)
        assertEquals(2, parsed.skipped)
        val customer = parsed.customers.single()
        assertEquals("Doe, Jane", customer.name)
        assertEquals("7 North St, Unit B", customer.address)
        assertEquals("Asked for \"light\" wash", customer.notes)
        assertFalse(customer.consent)
        assertEquals("", customer.consentEvidence)
    }

    @Test fun csvRejectsMissingHeadersAndUnclosedQuotes() {
        assertThrows(IllegalArgumentException::class.java) {
            BusinessLogic.importCsv("animal,phone,zip\nA,5550001010,12345", emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CsvRows.parse("name,zip\n\"unfinished,12345")
        }
    }

    @Test fun reportKeepsLegacyCampaignMoneySeparateFromJobPayments() {
        val a = Appointment(customerId = "c", service = "wash",
            date = "2026-10-08", time = "10:00", status = "COMPLETED")
        val job = JobRecord(appointmentId = a.id, customerId = "c",
            invoiceIssued = true, dueDate = "2026-10-03",
            lineItems = listOf(JobLineItem("Wash", 1.0, 200.0)),
            payments = listOf(JobPayment(amount = 75.0, date = "2026-10-08")))
        val summary = BusinessLogic.financials(listOf(job), listOf(a),
            LocalDate.of(2026, 10, 8))
        assertEquals(200.0, summary.billed, 0.001)
        assertEquals(75.0, summary.received, 0.001)
        assertEquals(125.0, summary.outstanding, 0.001)
        assertEquals(125.0, summary.overdue, 0.001)
        assertEquals(75.0, summary.monthReceived, 0.001)
        assertEquals(1, summary.completedJobs)
    }
}

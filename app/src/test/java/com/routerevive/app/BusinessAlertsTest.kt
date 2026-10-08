package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BusinessAlertsTest {
    private val today = LocalDate.of(2026, 10, 8)
    private val active = Appointment(id = "1", customerId = "c1", service = "Repair",
        date = "2026-10-08", time = "10:00", status = "SCHEDULED")
    private val overdue = JobRecord(
        id = "j1", appointmentId = "1", customerId = "c1",
        invoiceIssued = true, dueDate = "2026-10-05",
        lineItems = listOf(JobLineItem("Labor", 1.0, 80.0))
    )

    @Test fun countsOnlyActiveUpcomingAppointmentsAndIssuedOverdueInvoices() {
        val cancelled = active.copy(id = "2", status = "CANCELLED")
        val future = active.copy(id = "3", date = "2026-10-09")
        val summary = AlertRules.summarize(
            listOf(active, cancelled, future), listOf(overdue),
            true, AlertPreferences(enabled = true), today
        )
        assertEquals(1, summary.today)
        assertEquals(1, summary.tomorrow)
        assertEquals(1, summary.overdue)
        assertTrue(summary.cloudIssue)
        assertFalse(summary.isEmpty())
    }

    @Test fun everyCategoryCanBeIndividuallyDisabled() {
        val summary = AlertRules.summarize(listOf(active), listOf(overdue),
            true, AlertPreferences(enabled = true, appointments = false,
                invoices = false, cloud = false), today)
        assertTrue(summary.isEmpty())
    }

    @Test fun noCustomerPersonalInformationInSummaryText() {
        val summary = AlertRules.summarize(listOf(active), listOf(overdue),
            false, AlertPreferences(enabled = true), today)
        assertFalse(summary.message().contains("Repair"))
        assertFalse(summary.message().contains("c1"))
        assertFalse(summary.message().contains("80"))
    }
}

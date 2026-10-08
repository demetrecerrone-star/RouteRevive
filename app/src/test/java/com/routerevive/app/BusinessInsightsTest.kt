package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BusinessInsightsTest {
    private val today = LocalDate.of(2026, 10, 8)
    private val one = Customer(id = "c1", name = "Client One", phone = "", zip = "10001",
        service = "Cleaning", lastService = "")
    private val two = Customer(id = "c2", name = "Client Two", phone = "", zip = "10001",
        service = "Cleaning", lastService = "")

    @Test fun monthlyChartUsesActualPaymentDatesNotInvoiceIssueDates() {
        val job = JobRecord(
            id = "j", appointmentId = "a1", customerId = "c1", invoiceIssued = true,
            dueDate = "2026-10-01",
            lineItems = listOf(JobLineItem("Cleaning", 1.0, 200.0)),
            payments = listOf(
                JobPayment(amount = 40.0, date = "2026-09-30"),
                JobPayment(amount = 60.0, date = "2026-10-06")
            )
        )
        val report = InsightsRules.calculate(listOf(job), emptyList(), listOf(one), today)
        assertEquals(100.0, report.totalCollected, 0.001)
        assertEquals(200.0, report.invoiced, 0.001)
        assertEquals(100.0, report.outstanding, 0.001)
        assertEquals(100.0, report.overdueAmount, 0.001)
        assertEquals(1, report.overdueCount)
        assertEquals(40.0, report.income[4].collected, 0.001)
        assertEquals(60.0, report.income[5].collected, 0.001)
    }

    @Test fun nonIssuedQuotesAreNotOutstandingAndCampaignTotalsAreNotIncluded() {
        val job = JobRecord(appointmentId = "a1", customerId = "c1",
            invoiceIssued = false, lineItems = listOf(JobLineItem("Quote", 1.0, 500.0)))
        val result = InsightsRules.calculate(listOf(job), emptyList(), listOf(one), today)
        assertEquals(0.0, result.invoiced, 0.001)
        assertEquals(0.0, result.outstanding, 0.001)
        assertEquals(0.0, result.totalCollected, 0.001)
    }

    @Test fun countsReturnBookingsAndCompletedAppointmentsWithoutCancelled() {
        val a1 = Appointment(id = "1", customerId = "c1", service = "Cleaning",
            date = "2026-10-01", time = "09:00", status = "COMPLETED")
        val a2 = a1.copy(id = "2", date = "2026-10-03")
        val a3 = a1.copy(id = "3", customerId = "c2", status = "CANCELLED")
        val result = InsightsRules.calculate(emptyList(), listOf(a1,a2,a3), listOf(one,two), today)
        assertEquals(1, result.returningCustomers)
        assertEquals(2, result.completedAppointments)
        assertEquals(2, result.customers)
        assertEquals(6, result.income.size)
    }
}

package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JobMathTest {
    private fun job() = JobRecord(appointmentId = "a", customerId = "c",
        lineItems = listOf(JobLineItem("Window wash", 2.0, 65.0),
            JobLineItem("Driveway", 1.0, 95.0)))

    @Test fun totalsDepositsAndBalancesAreConsistent() {
        val original = job()
        assertEquals(225.0, JobMath.subtotal(original), 0.001)
        assertEquals(225.0, JobMath.balance(original), 0.001)
        val deposit = original.copy(payments = listOf(JobPayment(amount = 50.0)))
        assertEquals(50.0, JobMath.paid(deposit), 0.001)
        assertEquals(175.0, JobMath.balance(deposit), 0.001)
        assertTrue(JobMath.canRecordPayment(deposit, 175.0))
        assertFalse(JobMath.canRecordPayment(deposit, 176.0))
        assertFalse(JobMath.canRecordPayment(deposit, -5.0))
    }

    @Test fun overdueOnlyWhenInvoiceIsIssuedAndBalanceOutstanding() {
        val issued = job().copy(invoiceIssued = true, dueDate = "2026-10-01")
        assertTrue(JobMath.isOverdue(issued, LocalDate.of(2026, 10, 8)))
        assertFalse(JobMath.isOverdue(issued.copy(invoiceIssued = false), LocalDate.of(2026, 10, 8)))
        assertFalse(JobMath.isOverdue(issued.copy(dueDate = "2026-11-01"), LocalDate.of(2026, 10, 8)))
        val paid = issued.copy(payments = listOf(JobPayment(amount = 225.0)))
        assertFalse(JobMath.isOverdue(paid, LocalDate.of(2026, 10, 8)))
    }

    @Test fun itemValidationRejectsMissingDescriptionsAndNegativePricing() {
        assertTrue(JobMath.validLineItem(JobLineItem("Concrete", 1.0, 120.0)))
        assertFalse(JobMath.validLineItem(JobLineItem("", 1.0, 120.0)))
        assertFalse(JobMath.validLineItem(JobLineItem("Concrete", 0.0, 120.0)))
        assertFalse(JobMath.validLineItem(JobLineItem("Concrete", 1.0, -1.0)))
    }
}

package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CustomerHistoryTest {
    private val person = Customer(id="c1",name="Sam",phone="5551119999",zip="10001",
        service="Painting",lastService="2026-09-01")

    @Test fun timelineIncludesOnlyLinkedCustomerAndPaymentEvents() {
        val a=Appointment(id="a1",customerId="c1",service="Painting",
            date="2026-10-08",time="10:00",lastReminderAt="2026-10-07")
        val other=a.copy(id="a2", customerId="someone-else")
        val job=JobRecord(id="j1",appointmentId="a1",customerId="c1",
            invoiceIssued=true,status="IN_PROGRESS",statusUpdatedAt="2026-10-06",
            lineItems=listOf(JobLineItem("Painting",1.0,100.0)),
            payments=listOf(JobPayment(id="p1",amount=40.0,date="2026-10-08",method="Cash")))
        val wrong=job.copy(id="j2",appointmentId="a2",customerId="someone-else")
        val events=CustomerHistoryRules.events(person,listOf(a,other),listOf(job,wrong))
        assertEquals(6,events.size)
        assertEquals("2026-10-08",events.first().date.toString())
        assertTrue(events.any{it.title=="Payment recorded" && it.detail.contains("40.00")})
        assertFalse(events.any{it.id=="invoice-j2"})
        assertTrue(events.any{it.title=="Job progress: In progress"})
    }

    @Test fun malformedLegacyDatesAreSkippedNotFatal() {
        val a=Appointment(id="a",customerId="c1",service="Cleaning",date="not-a-date",time="10:00")
        val events=CustomerHistoryRules.events(person,listOf(a),emptyList())
        assertEquals(1,events.size)
        assertEquals("Previous service recorded",events.single().title)
    }

    @Test fun jobProgressRecordsDateOnlyWhenChanged() {
        val job=JobRecord(appointmentId="a1",customerId="c1")
        assertSame(job,JobProgress.update(job,"PLANNED",LocalDate.of(2026,10,8)))
        val moved=JobProgress.update(job,"IN_PROGRESS",LocalDate.of(2026,10,8))
        assertEquals("2026-10-08",moved.statusUpdatedAt)
        assertEquals("IN_PROGRESS",moved.status)
        assertThrows(IllegalArgumentException::class.java) { JobProgress.update(job,"BAD") }
    }
}

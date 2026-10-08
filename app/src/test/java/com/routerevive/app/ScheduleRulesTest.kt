package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test

class ScheduleRulesTest {
    private fun ap(time: String, date: String = "2026-10-12", status: String = "SCHEDULED",
                   minutes: Int = 60) =
        Appointment(customerId = "customer-1", service = "House wash", date = date,
            time = time, durationMinutes = minutes, status = status)

    @Test fun overlappingAppointmentsAreRejected() {
        assertTrue(ScheduleRules.overlaps(ap("09:30"), listOf(ap("09:00"))))
    }

    @Test fun adjacentAppointmentsAreAllowed() {
        assertFalse(ScheduleRules.overlaps(ap("10:00"), listOf(ap("09:00"))))
    }

    @Test fun otherDaysAreAllowed() {
        assertFalse(ScheduleRules.overlaps(ap("09:00", "2026-10-13"), listOf(ap("09:00"))))
    }

    @Test fun cancelledAppointmentsReleaseSlot() {
        assertFalse(ScheduleRules.overlaps(ap("09:00"), listOf(ap("09:00", status = "CANCELLED"))))
    }

    @Test fun dateAndTimeValidation() {
        assertTrue(ScheduleRules.validDate("2026-10-12"))
        assertFalse(ScheduleRules.validDate("2026-13-12"))
        assertTrue(ScheduleRules.validTime("09:30"))
        assertFalse(ScheduleRules.validTime("29:30"))
    }
}

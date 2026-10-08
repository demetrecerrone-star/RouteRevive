package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RecurringBookingTest {
    private val date = LocalDate.of(2026, 10, 8)
    private val seed = Appointment(id = "seed", customerId = "c1",
        service = "Cleaning", date = "2026-10-09", time = "09:00",
        durationMinutes = 60, status = "SCHEDULED", price = 120.0)

    @Test fun weeklyCreatesIndependentFutureAppointments() {
        val output = RecurringBookingRules.create(seed, RepeatCadence.WEEKLY, 3,
            listOf(seed), date)
        assertEquals(listOf("2026-10-16","2026-10-23","2026-10-30"),
            output.map { it.date })
        assertTrue(output.all { it.id != seed.id && it.status == "SCHEDULED" })
        assertEquals(3, output.map { it.id }.distinct().size)
        assertTrue(output.all { it.price == 120.0 && it.customerId == seed.customerId })
    }

    @Test fun biweeklySkipsPastAndPreservesClock() {
        val oldSeed = seed.copy(date = "2026-09-01", time = "14:30")
        val output = RecurringBookingRules.create(oldSeed, RepeatCadence.BIWEEKLY,
            2, listOf(oldSeed), date)
        assertEquals(listOf("2026-10-13","2026-10-27"), output.map { it.date })
        assertEquals(listOf("14:30","14:30"), output.map { it.time })
    }

    @Test fun monthlyAnchorsToOriginalDate() {
        val jan = seed.copy(date = "2027-01-31")
        val output = RecurringBookingRules.create(jan, RepeatCadence.MONTHLY,
            3, listOf(jan), date)
        assertEquals(listOf("2027-02-28","2027-03-31","2027-04-30"), output.map { it.date })
    }

    @Test fun anyConflictRejectsEntireBatch() {
        val colliding = seed.copy(id = "taken", date = "2026-10-23")
        assertThrows(IllegalArgumentException::class.java) {
            RecurringBookingRules.create(seed, RepeatCadence.WEEKLY, 3,
                listOf(seed, colliding), date)
        }
    }

    @Test fun invalidCountOrCancelledSeedRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurringBookingRules.create(seed, RepeatCadence.WEEKLY, 13, listOf(seed), date)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecurringBookingRules.create(seed.copy(status = "CANCELLED"),
                RepeatCadence.WEEKLY, 2, emptyList(), date)
        }
    }

    @Test fun calendarDayAndMondayToSundayWeekRespectDate() {
        val a = seed.copy(date = "2026-10-12")
        val b = seed.copy(id = "second", date = "2026-10-18")
        val c = seed.copy(id = "third", date = "2026-10-19")
        assertEquals(2, RecurringBookingRules.view(listOf(a,b,c),
            LocalDate.of(2026,10,14), "Week").size)
        assertEquals(listOf(b), RecurringBookingRules.view(listOf(a,b,c),
            LocalDate.of(2026,10,18), "Day"))
    }
}

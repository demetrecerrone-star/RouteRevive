package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BookingRulesTest {
    private val now = LocalDateTime.of(2026, 10, 8, 12, 0)
    private fun request() = BookingRequest(name = "Jane Doe", phone = "6125550185",
        zip = "55401", service = "Pressure wash", requestedDate = "2026-10-09",
        requestedTime = "09:00", durationMinutes = 60, quotedPrice = 180.0)

    @Test fun validNewRequestAndConflictFreeBooking() {
        assertTrue(BookingRules.valid(request()))
        assertTrue(BookingRules.bookable(request(), emptyList(), now))
    }

    @Test fun rejectsBookedDeclinedPastAndOverlappingRequests() {
        val a = request()
        val busy = Appointment(customerId = "someone",
            service = "Wash", date = a.requestedDate, time = "09:30",
            durationMinutes = 90)
        assertFalse(BookingRules.bookable(a, listOf(busy), now))
        assertFalse(BookingRules.bookable(a.copy(status = "DECLINED"), emptyList(), now))
        assertFalse(BookingRules.bookable(a.copy(status = "BOOKED"), emptyList(), now))
        assertFalse(BookingRules.bookable(a.copy(requestedDate = "2026-10-06"), emptyList(), now))
        assertFalse(BookingRules.bookable(a.copy(requestedTime = "23:30",
            durationMinutes = 120), emptyList(), now))
    }

    @Test fun suppressesInvalidPhonesAndPrices() {
        assertFalse(BookingRules.valid(request().copy(phone = "123")))
        assertFalse(BookingRules.valid(request().copy(quotedPrice = Double.NaN)))
        assertFalse(BookingRules.valid(request().copy(zip = "ABCDE")))
    }

    @Test fun remindersAreOwnerApprovedAndRespectOptOut() {
        val customer = Customer(name = "Jane Doe", phone = "6125550185",
            zip = "55401", service = "Pressure wash",
            lastService = "2025-01-01", consent = false)
        val ap = Appointment(customerId = customer.id, service = "Pressure wash",
            date = "2026-10-09", time = "09:00")
        assertTrue(ReminderRules.canDraft(ap, customer, LocalDate.of(2026, 10, 8)))
        assertFalse(ReminderRules.canDraft(ap, customer.copy(optedOut = true), LocalDate.of(2026, 10, 8)))
        assertFalse(ReminderRules.canDraft(ap.copy(status = "CANCELLED"), customer, LocalDate.of(2026, 10, 8)))
        assertFalse(ReminderRules.sentToday(ap, LocalDate.of(2026, 10, 8)))
        assertTrue(ReminderRules.sentToday(ap.copy(lastReminderAt = "2026-10-08"),
            LocalDate.of(2026, 10, 8)))
        assertTrue(ReminderRules.draft(ap, customer, "Example Co").contains("2026-10-09"))
    }
}

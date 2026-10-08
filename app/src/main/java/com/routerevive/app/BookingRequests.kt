package com.routerevive.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** Owner-recorded inbound customer request; NOT a remote/public booking form. */
data class BookingRequest(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val zip: String,
    val service: String,
    val requestedDate: String,
    val requestedTime: String,
    val durationMinutes: Int = 60,
    val quotedPrice: Double = 0.0,
    val notes: String = "",
    val source: String = "Phone",
    val status: String = "NEW",
    val createdAt: String = LocalDate.now().toString(),
    val bookedAppointmentId: String = ""
)

object BookingRules {
    private val statuses = setOf("NEW", "CONTACTED", "DECLINED", "BOOKED")
    fun valid(request: BookingRequest): Boolean =
        request.name.isNotBlank() && request.name.length <= 150 &&
        CampaignRules.digits(request.phone).length in 10..15 &&
        request.zip.matches(Regex("\\d{5}")) &&
        request.service.isNotBlank() && request.service.length <= 180 &&
        request.notes.length <= 3000 && request.source.length <= 40 &&
        request.status in statuses &&
        runCatching { LocalDate.parse(request.requestedDate) }.isSuccess &&
        runCatching { LocalTime.parse(request.requestedTime) }.isSuccess &&
        request.durationMinutes in 15..480 && request.quotedPrice.isFinite() &&
        request.quotedPrice in 0.0..1_000_000.0

    /** Same-day past starts are never silently accepted. */
    fun bookable(request: BookingRequest, all: List<Appointment>,
                 now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (!valid(request) || request.status !in setOf("NEW", "CONTACTED")) return false
        val start = runCatching {
            LocalDateTime.of(LocalDate.parse(request.requestedDate),
                LocalTime.parse(request.requestedTime))
        }.getOrNull() ?: return false
        if (start.isBefore(now)) return false
        // Current ScheduleRules uses LocalTime; avoid midnight wrap by rejecting
        // cross-midnight requests before calling the shared conflict rule.
        if (start.plusMinutes(request.durationMinutes.toLong()).toLocalDate() != start.toLocalDate()) return false
        val proposed = Appointment(customerId = "request-preview",
            service = request.service, date = request.requestedDate,
            time = request.requestedTime,
            durationMinutes = request.durationMinutes, price = request.quotedPrice)
        return !ScheduleRules.overlaps(proposed, all)
    }
}

object ReminderRules {
    fun canDraft(appointment: Appointment, customer: Customer,
                 today: LocalDate = LocalDate.now()): Boolean {
        val date = runCatching { LocalDate.parse(appointment.date) }.getOrNull() ?: return false
        return appointment.status == "SCHEDULED" &&
            !customer.optedOut && !customer.demo &&
            CampaignRules.digits(customer.phone).length in 10..15 &&
            !date.isBefore(today) && !date.isAfter(today.plusDays(30))
    }

    fun draft(appointment: Appointment, customer: Customer, businessName: String): String {
        val sender = businessName.trim().ifBlank { "your service provider" }
        return "Hi " + customer.name.trim().substringBefore(" ") + ". This is " + sender +
            ". A reminder of your confirmed " + appointment.service + " appointment on " +
            appointment.date + " at " + appointment.time +
            ". Please reply if you need to reschedule. Thank you!"
    }

    fun sentToday(appointment: Appointment, today: LocalDate = LocalDate.now()): Boolean =
        appointment.lastReminderAt == today.toString()
}

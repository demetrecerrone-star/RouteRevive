package com.routerevive.app

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class RepeatCadence(val display: String) {
    WEEKLY("Weekly"), BIWEEKLY("Every 2 weeks"), MONTHLY("Monthly")
}

object RecurringBookingRules {
    /**
     * All-or-nothing generation of standalone appointments.
     * Anchoring months to the seed prevents Jan 31 -> Feb 28 -> Mar 28 drift.
     * Each generated booking receives its own ID and can later be rescheduled
     * or cancelled independently.
     */
    fun create(
        seed: Appointment, cadence: RepeatCadence,
        count: Int, existing: List<Appointment>,
        today: LocalDate = LocalDate.now()
    ): List<Appointment> {
        require(count in 1..12) { "Choose 1 to 12 future bookings." }
        require(seed.status != "CANCELLED") { "Cannot repeat a cancelled booking." }
        require(seed.customerId.isNotBlank() && seed.service.isNotBlank() &&
            ScheduleRules.validDate(seed.date) && ScheduleRules.validTime(seed.time) &&
            seed.durationMinutes in 15..480) { "Original appointment is invalid." }
        val anchor = LocalDate.parse(seed.date)
        val clock = LocalTime.parse(seed.time)
        val generated = mutableListOf<Appointment>()
        for (step in 1..240) {
            val date = when (cadence) {
                RepeatCadence.WEEKLY -> anchor.plusWeeks(step.toLong())
                RepeatCadence.BIWEEKLY -> anchor.plusWeeks(step.toLong() * 2L)
                RepeatCadence.MONTHLY -> anchor.plusMonths(step.toLong())
            }
            if (date.isBefore(today)) continue
            val candidate = seed.copy(id = java.util.UUID.randomUUID().toString(),
                date = date.toString(), time = clock.toString(),
                status = "SCHEDULED", campaignId = "", lastReminderAt = "")
            require(!ScheduleRules.overlaps(candidate, existing + generated)) {
                "Booking conflict on ${date} at ${seed.time}. No recurring bookings were added."
            }
            generated += candidate
            if (generated.size == count) return generated
        }
        error("Unable to schedule this many future occurrences.")
    }

    fun view(
        appointments: List<Appointment>, day: LocalDate, mode: String
    ): List<Appointment> {
        val from = if (mode == "Week") day.minusDays((day.dayOfWeek.value - 1).toLong()) else day
        val until = if (mode == "Week") from.plusDays(6) else from
        return appointments.filter {
            val parsed = runCatching { LocalDate.parse(it.date) }.getOrNull()
            parsed != null && !parsed.isBefore(from) && !parsed.isAfter(until)
        }.sortedWith(compareBy<Appointment> { it.date }.thenBy { it.time })
    }
}

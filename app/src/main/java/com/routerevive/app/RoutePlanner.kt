package com.routerevive.app

/**
 * Local route suggestions. These are straight-line distances, not traffic-aware
 * driving routes. Confirmed appointment times are NEVER changed automatically.
 */
object RoutePlannerLogic {
    fun distanceFirstPreview(
        chronological: List<Pair<Appointment, Customer>>
    ): List<Pair<Appointment, Customer>> {
        if (chronological.size < 3) return chronological
        // Keep the first appointment fixed. Recommend an ordering of later
        // stops for manual review; never automatically reschedule bookings.
        val result = mutableListOf(chronological.first())
        val remaining = chronological.drop(1).toMutableList()
        while (remaining.isNotEmpty()) {
            val from = result.last().second
            val next = remaining.minWithOrNull(
                compareBy<Pair<Appointment, Customer>> { NeighborhoodLogic.distanceMiles(from, it.second) }
                    .thenBy { it.first.time }
                    .thenBy { it.first.id }
            )!!
            result.add(next)
            remaining.remove(next)
        }
        return result
    }

    fun straightLineMiles(stops: List<Pair<Appointment, Customer>>): Double =
        stops.zipWithNext().sumOf { (a, b) ->
            NeighborhoodLogic.distanceMiles(a.second, b.second)
        }

    fun nextStop(stops: List<Pair<Appointment, Customer>>): Pair<Appointment, Customer>? =
        stops.firstOrNull { it.first.status == "IN_PROGRESS" }
            ?: stops.firstOrNull { it.first.status == "SCHEDULED" }

    fun markerStatus(customerId: String, date: String, appointments: List<Appointment>): String {
        val statuses = appointments.filter { it.customerId == customerId && it.date == date }
            .map { it.status }
        return when {
            "IN_PROGRESS" in statuses -> "IN_PROGRESS"
            "SCHEDULED" in statuses -> "SCHEDULED"
            "COMPLETED" in statuses -> "COMPLETED"
            "CANCELLED" in statuses -> "CANCELLED"
            else -> "CUSTOMER"
        }
    }
}

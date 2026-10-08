package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test

class RoutePlannerLogicTest {
    private fun customer(id: String, longitude: Double) = Customer(
        id = id, name = id, phone = "5550001111", zip = "12345",
        service = "Washing", lastService = "2025-01-01",
        latitude = 40.0, longitude = longitude
    )
    private fun pair(id: String, time: String, lon: Double): Pair<Appointment, Customer> =
        Appointment(id = id, customerId = id, service = "Washing",
            date = "2026-10-08", time = time) to customer(id, lon)

    @Test fun distancePreviewNeverMutatesBookingsAndStartsWithFirstScheduledStop() {
        val ordered = listOf(pair("A", "09:00", -75.0),
            pair("B", "10:00", -75.3), pair("C", "11:00", -75.01))
        val preview = RoutePlannerLogic.distanceFirstPreview(ordered)
        assertEquals(listOf("A", "C", "B"), preview.map { it.first.id })
        assertEquals(listOf("A", "B", "C"), ordered.map { it.first.id })
        assertTrue(RoutePlannerLogic.straightLineMiles(preview) <
            RoutePlannerLogic.straightLineMiles(ordered))
    }

    @Test fun fewerThanThreeStopsRemainInBookedOrder() {
        val input = listOf(pair("A", "09:00", -75.0), pair("B", "10:00", -76.0))
        assertEquals(input, RoutePlannerLogic.distanceFirstPreview(input))
        assertEquals(0.0, RoutePlannerLogic.straightLineMiles(input.take(1)), 0.001)
    }

    @Test fun statusAndNextStopFavorActiveJobs() {
        val stops = listOf(pair("A", "09:00", -75.0),
            pair("B", "10:00", -75.1).let { it.first.copy(status = "IN_PROGRESS") to it.second })
        assertEquals("B", RoutePlannerLogic.nextStop(stops)?.first?.id)
        assertEquals("COMPLETED", RoutePlannerLogic.markerStatus("A", "2026-10-08",
            listOf(stops[0].first.copy(status = "COMPLETED"))))
    }
}

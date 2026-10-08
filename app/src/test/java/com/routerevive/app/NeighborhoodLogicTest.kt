package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NeighborhoodLogicTest {
    private val today = LocalDate.of(2026, 10, 7)
    private fun customer(
        name: String,
        phone: String,
        lat: Double? = 41.90,
        lon: Double? = -87.67,
        zip: String = "60618",
        consent: Boolean = true,
        optedOut: Boolean = false
    ) = Customer(
        name = name, phone = phone, latitude = lat, longitude = lon,
        zip = zip, service = "House pressure washing",
        lastService = today.minusDays(210).toString(), consent = consent,
        consentEvidence = if (consent) "Signed permission January 2026" else "",
        optedOut = optedOut
    )

    @Test fun invalidAndMissingCoordinatesDoNotCreateMapPins() {
        assertFalse(NeighborhoodLogic.validPin(customer("Missing", "3125551000", null, null)))
        assertFalse(NeighborhoodLogic.validPin(customer("Bad", "3125551001", 200.0, -87.6)))
        assertTrue(NeighborhoodLogic.validPin(customer("Valid", "3125551002")))
    }

    @Test fun zeroDistanceAndNearbyDistanceAreReasonable() {
        val a = customer("Anchor", "3125551000")
        val b = customer("Nearby", "3125551001", 41.901, -87.671)
        assertEquals(0.0, NeighborhoodLogic.distanceMiles(a, a), 0.00001)
        assertTrue(NeighborhoodLogic.distanceMiles(a, b) < 1.0)
    }

    @Test fun eligibilityExcludesOptOutAndDifferentZipAndMissingPermission() {
        val anchor = customer("Anchor", "3125551000")
        val good = customer("Eligible", "3125551001", 41.901, -87.671)
        val badConsent = customer("NoPermission", "3125551002", 41.902, -87.67, consent = false)
        val optOut = customer("NoContact", "3125551003", 41.903, -87.67, optedOut = true)
        val otherZip = customer("OtherZip", "3125551004", 41.904, -87.67, zip = "60619")
        val source = listOf(anchor, good, badConsent, optOut, otherZip)
        val found = NeighborhoodLogic.nearbyCandidates(anchor, source, emptyList(), emptyList(), 5.0, today)
        assertEquals(listOf(good.id), found.map { it.first.id })
    }

    @Test fun scheduledCustomersAreNotProspectiveLeads() {
        val anchor = customer("Anchor", "3125551000")
        val booked = customer("Booked", "3125551001", 41.901, -87.671)
        val ap = Appointment(customerId = booked.id, service = booked.service,
            date = today.plusDays(1).toString(), time = "09:00")
        val found = NeighborhoodLogic.nearbyCandidates(
            anchor, listOf(anchor, booked), emptyList(), listOf(ap), 5.0, today)
        assertTrue(found.isEmpty())
    }

    @Test fun routePreservesAppointmentClockOrder() {
        val a = customer("One", "3125551000")
        val b = customer("Two", "3125551001", 41.92, -87.68)
        val date = today.plusDays(1).toString()
        val late = Appointment(customerId = b.id, service = b.service, date = date, time = "16:00")
        val early = Appointment(customerId = a.id, service = a.service, date = date, time = "09:00")
        val route = NeighborhoodLogic.scheduledRoute(date, listOf(late, early), listOf(a,b))
        assertEquals(listOf(early.id, late.id), route.map { it.first.id })
    }
}

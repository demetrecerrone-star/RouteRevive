package com.routerevive.app

import java.io.IOException
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfitPlannerTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val day = today.plusDays(1)
    private fun customer(id: String, number: Int, latitude: Double? = 46.35) = Customer(
        id = id, name = "$id Example", phone = "612555${number.toString().padStart(4, '0')}", zip = "56425",
        service = "House pressure washing", lastService = today.minusDays(220).toString(), consent = true,
        consentEvidence = "Fictional permission", consentDate = today.minusDays(220).toString(),
        latitude = latitude, longitude = if (latitude == null) null else -94.25)
    private fun appointment(id: String, customerId: String, time: String, duration: Int = 60) = Appointment(
        id = id, customerId = customerId, service = "House pressure washing", date = day.toString(),
        time = time, durationMinutes = duration, price = 150.0)
    private fun state(): WorkspaceSnapshot {
        val customers = listOf(customer("anchor", 101), customer("near1", 102, 46.351),
            customer("near2", 103, 46.352), customer("far", 104, 47.0), customer("noPin", 105, null),
            customer("optOut", 106).copy(optedOut = true), customer("next", 107),
            customer("noConsent", 108).copy(consent = false), customer("recent", 109).copy(lastService = today.toString()),
            customer("otherZip", 110).copy(zip = "56401"))
        return WorkspaceSnapshot(customers = customers, appointments = listOf(
            appointment("anchor-booking", "anchor", "09:00"), appointment("next-booking", "next", "12:00")),
            business = BusinessProfile(name = "Example Washing"))
    }
    private fun plan() = ProfitPlan("anchor-booking", "10:00", "12:00", 30, 15, 60.0, 10.0, 5.0, 30, 3)
    private fun request(plan: ProfitPlan = plan(), price: Double = 150.0, discount: Int = 10) =
        ProfitOfferRequest("planned-offer", day.toString(), price, discount, plan)
    private fun interested(): WorkspaceSnapshot {
        var state = ProfitPlanner.create(state(), request(), today)
        for (id in listOf("near1", "near2")) {
            val message = state.campaigns.single().recipients.single { it.customerId == id }.message
            state = CampaignWorkflow.approve(state, "planned-offer", id, message, today)
            state = CampaignWorkflow.recordSent(state, "planned-offer", id, today)
            state = CampaignWorkflow.status(state, "planned-offer", id, "INTERESTED", today)
        }
        return state
    }

    @Test fun comparisonIncludesPaidTravelAndTheWholeCrew() {
        val costPlan = plan().copy(serviceMinutes = 60)
        val result = ProfitPlanner.estimate(costPlan, 150.0, 10)
        assertEquals(90.0, result.costPerJob, 0.0001)
        assertEquals(135.0, result.netPrice, 0.0001)
        assertEquals(45.0, result.contributionPerJob, 0.0001)
        assertEquals(60.0, result.fullPriceContribution, 0.0001)
        assertEquals(128.58, result.minimumPrice, 0.0001)
        assertEquals(14, result.maximumDiscountPct)
        assertTrue(result.meetsTarget)
    }

    @Test fun minimumPriceRoundsUpSoOneCentCannotUndercutTarget() {
        val costs = plan().copy(crewHourlyCost = 0.0, materialsCost = 42.01, vehicleCost = 0.0)
        val below = ProfitPlanner.estimate(costs, 60.01, 0)
        assertEquals(60.02, below.minimumPrice, 0.0001)
        assertFalse(below.meetsTarget)
        assertNull(below.maximumDiscountPct)
        assertTrue(ProfitPlanner.estimate(costs, 60.02, 0).meetsTarget)
    }

    @Test fun fractionalLaborCentIsIncludedRatherThanRoundedAway() {
        val result = ProfitPlanner.estimate(plan().copy(serviceMinutes = 15, travelMinutes = 0,
            crewHourlyCost = 0.01, materialsCost = 0.0, vehicleCost = 0.0), 1.0, 0)
        assertEquals(0.01, result.costPerJob, 0.0001)
    }

    @Test fun nonFiniteFractionalCentAndInvalidAssumptionsCannotProduceAnEstimate() {
        for (cost in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 1.001)) {
            assertThrows(IllegalArgumentException::class.java) { ProfitPlanner.estimate(plan().copy(crewHourlyCost = cost), 150.0, 0) }
        }
        for (invalid in listOf(plan().copy(serviceMinutes = 0), plan().copy(travelMinutes = -1),
            plan().copy(serviceMinutes = 480, travelMinutes = 15), plan().copy(targetMarginPct = 100),
            plan().copy(radiusMiles = 0), plan().copy(windowEnd = "09:30"))) {
            assertTrue(ProfitPlanner.planErrors(invalid).isNotEmpty())
        }
        assertThrows(IllegalArgumentException::class.java) { ProfitPlanner.estimate(plan(), 150.001, 0) }
        assertThrows(IllegalArgumentException::class.java) { ProfitPlanner.estimate(plan(), 150.0, 51) }
    }

    @Test fun openingsMergeOverlapsAndReleaseCanceledAppointments() {
        val appointments = listOf(appointment("a", "anchor", "09:00"),
            appointment("b", "next", "10:30"), appointment("c", "next", "11:00"),
            appointment("cancel", "next", "13:00").copy(status = "CANCELLED"))
        assertEquals(listOf(ScheduleOpening("10:00", "10:30"), ScheduleOpening("12:00", "17:00")),
            ProfitPlanner.openings(day, "09:00", "17:00", appointments))
    }

    @Test fun precedingDayWorkCrossingMidnightStillOccupiesTheOpening() {
        val late = appointment("late", "anchor", "23:30", 120).copy(date = today.toString())
        assertEquals(listOf(ScheduleOpening("01:30", "03:00")),
            ProfitPlanner.openings(day, "00:00", "03:00", listOf(late)))
    }

    @Test fun malformedScheduleFailsClosedInsteadOfReportingFalseAvailability() {
        assertThrows(IllegalStateException::class.java) { ProfitPlanner.openings(day, "09:00", "17:00",
            listOf(appointment("bad", "anchor", "25:00"))) }
    }

    @Test fun nearbyAudienceUsesActualEligibilityAndNeverIncludesWholeZip() {
        val opportunity = ProfitPlanner.evaluate(state(), request(), today)
        assertEquals(listOf("near1", "near2"), opportunity.customers.map { it.first.id })
        assertEquals(1, opportunity.missingPins)
        assertEquals(2, opportunity.capacity)
        val created = ProfitPlanner.create(state(), request(), today).campaigns.single()
        assertEquals(setOf("near1", "near2"), created.recipients.map { it.customerId }.toSet())
        assertTrue(created.recipients.all { it.status == "DRAFT" && it.message.contains("STOP") })
        assertEquals(plan(), created.profitPlan)
        assertEquals(today.toString(), created.expiryDate)
    }

    @Test fun draftCapacityDoesNotExceedTheTimeAllowance() {
        val opportunity = ProfitPlanner.evaluate(state(), request(plan().copy(serviceMinutes = 60, travelMinutes = 15)), today)
        assertEquals(1, opportunity.capacity)
        assertThrows(IllegalArgumentException::class.java) {
            ProfitPlanner.create(state(), request(plan().copy(windowEnd = "10:30")), today)
        }
    }

    @Test fun creationRechecksAnOpeningAfterAnotherBookingTakesIt() {
        val changed = state().copy(appointments = state().appointments + appointment("new", "far", "10:30"))
        assertThrows(IllegalArgumentException::class.java) { ProfitPlanner.create(changed, request(), today) }
    }

    @Test fun creationRejectsBelowTargetPriceAndCanceledAnchor() {
        assertThrows(IllegalArgumentException::class.java) { ProfitPlanner.create(state(), request(price = 50.0), today) }
        val changed = state().copy(appointments = state().appointments.map { if (it.id == "anchor-booking") it.copy(status = "CANCELLED") else it })
        assertThrows(IllegalArgumentException::class.java) { ProfitPlanner.create(changed, request(), today) }
        assertThrows(IllegalArgumentException::class.java) { ProfitPlanner.create(state().copy(business = BusinessProfile()), request(), today) }
    }

    @Test fun plannedOfferCannotBypassAudienceChecksViaOrdinaryBuilder() {
        val created = ProfitPlanner.create(state(), request(), today).campaigns.single()
        assertThrows(IllegalArgumentException::class.java) { CampaignWorkflow.create(state(), created, today) }
    }

    @Test fun bookingsHonorMinimumPriceAndReserveTravelInsideTheOpening() {
        val state = interested()
        for ((amount, time, minutes) in listOf(Triple(50.0, "10:00", 45), Triple(135.0, "09:00", 45),
            Triple(135.0, "11:30", 45), Triple(135.0, "10:00", 30))) {
            assertThrows(IllegalArgumentException::class.java) {
                CampaignWorkflow.book(state, "planned-offer", "near1", amount, time, minutes, today.atTime(8, 0))
            }
        }
        val booked = CampaignWorkflow.book(state, "planned-offer", "near1", 135.0, "10:00", 45, today.atTime(8, 0))
        assertEquals("10:45", ProfitPlanner.nextStart(booked.campaigns.single(), booked.appointments))
        val full = CampaignWorkflow.book(booked, "planned-offer", "near2", 135.0, "10:45", 45, today.atTime(8, 0))
        assertNull(ProfitPlanner.nextStart(full.campaigns.single(), full.appointments))
        assertEquals(45, full.appointments.last().durationMinutes)
    }

    @Test fun laterCalendarChangesBlockAConflictingBooking() {
        val state = interested()
        val changed = state.copy(appointments = state.appointments + appointment("unexpected", "far", "10:00", 120))
        assertNull(ProfitPlanner.nextStart(changed.campaigns.single(), changed.appointments))
        assertThrows(IllegalArgumentException::class.java) {
            CampaignWorkflow.book(changed, "planned-offer", "near1", 135.0, "10:00", 45, today.atTime(8, 0))
        }
    }

    @Test fun reschedulingCannotDiscardThePlannedOpeningOrTravelAllowance() {
        val booked = CampaignWorkflow.book(interested(), "planned-offer", "near1", 135.0, "10:00", 45, today.atTime(8, 0))
        val appointment = booked.appointments.last()
        for (revised in listOf(appointment.copy(date = day.plusDays(1).toString()),
            appointment.copy(time = "14:00"), appointment.copy(durationMinutes = 30))) {
            assertThrows(IllegalArgumentException::class.java) { WorkspaceRules.reschedule(booked, revised, today.atTime(8, 0)) }
        }
    }

    @Test fun movedMapPinBlocksApprovalOfAnOldNearbyDraft() {
        val created = ProfitPlanner.create(state(), request(), today)
        val changed = created.copy(customers = created.customers.map { if (it.id == "near1") it.copy(latitude = 47.0) else it })
        val campaign = changed.campaigns.single()
        assertTrue(CampaignWorkflow.contactReasons(changed, campaign, changed.customers.single { it.id == "near1" }, today)
            .any { it.contains("radius") })
        assertThrows(IllegalArgumentException::class.java) {
            CampaignWorkflow.approve(changed, campaign.id, "near1", campaign.recipients.first().message, today)
        }
    }

    @Test fun failedSavePreservesAllRecordsAndAllowsRetry() {
        val initial = state()
        var failing = true
        val controller = WorkspaceController(initial) { if (failing) throw IOException("Disk full") }
        assertThrows(IOException::class.java) { controller.commit { ProfitPlanner.create(it, request(), today) } }
        assertEquals(initial, controller.current)
        failing = false
        controller.commit { ProfitPlanner.create(it, request(), today) }
        assertEquals(1, controller.current.campaigns.size)
        assertEquals(initial.appointments, controller.current.appointments)
    }

    @Test fun savedAssumptionsRoundTripAndBackupRejectsMalformedOptionalPlan() {
        assertEquals(plan(), ProfitPlan.fromJson(JSONObject(plan().toJson().toString())))
        val campaign = JSONObject().put("id", "planned-offer").put("title", "Planned visit")
            .put("businessName", "Example").put("zip", "56425").put("service", "Washing")
            .put("visitDate", day.toString()).put("expiryDate", today.toString()).put("price", 150.0)
            .put("capacity", 2).put("profitPlan", plan().toJson())
        val backup = JSONObject().put("schemaVersion", 7).put("customers", JSONArray())
            .put("campaigns", JSONArray().put(campaign))
        BackupValidator.check(backup.toString())
        campaign.put("price", -1.0)
        assertThrows(IllegalArgumentException::class.java) { BackupValidator.check(backup.toString()) }
        campaign.put("price", 150.0)
        campaign.getJSONObject("profitPlan").put("travelMinutes", -1)
        assertThrows(IllegalArgumentException::class.java) { BackupValidator.check(backup.toString()) }
        campaign.put("profitPlan", "not an object")
        assertThrows(org.json.JSONException::class.java) { BackupValidator.check(backup.toString()) }
        campaign.remove("profitPlan")
        BackupValidator.check(backup.toString())
    }

    @Test fun fractionalMinutesAndStringCostsCannotBeCoercedFromBackup() {
        assertThrows(ArithmeticException::class.java) { ProfitPlan.fromJson(plan().toJson().put("travelMinutes", 15.5)) }
        assertThrows(IllegalStateException::class.java) { ProfitPlan.fromJson(plan().toJson().put("materialsCost", "10.00")) }
    }
}

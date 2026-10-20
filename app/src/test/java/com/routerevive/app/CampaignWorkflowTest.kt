package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

class CampaignWorkflowTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val now = today.atTime(10, 0)
    private fun customer(id: String = "customer", phone: String = "6125550142") = Customer(
        id = id, name = "Taylor Example", phone = phone, zip = "56425", service = "House pressure washing",
        lastService = today.minusDays(220).toString(), lastPrice = 150.0,
        consent = true, consentEvidence = "Documented test permission", consentDate = today.minusDays(220).toString())
    private fun offer(capacity: Int = 4) = Campaign(id = "offer", title = "Neighborhood offer",
        businessName = "Example Washing", zip = "56425", service = "House pressure washing",
        visitDate = today.plusDays(1).toString(), expiryDate = today.plusDays(1).toString(),
        price = 150.0, discountPct = 0, capacity = capacity)
    private fun initial(customers: List<Customer> = listOf(customer())) =
        WorkspaceSnapshot(customers = customers, business = BusinessProfile(name = "Example Washing"))
    private fun interested(): WorkspaceSnapshot {
        var state = CampaignWorkflow.create(initial(), offer(), today)
        state = CampaignWorkflow.approve(state, "offer", "customer", state.campaigns.single().recipients.single().message, today)
        state = CampaignWorkflow.recordSent(state, "offer", "customer", today)
        return CampaignWorkflow.status(state, "offer", "customer", "INTERESTED", today)
    }
    private fun booked() = CampaignWorkflow.book(interested(), "offer", "customer", 150.0, "09:00", 60, now)
    private fun completed(): WorkspaceSnapshot = CampaignWorkflow.status(booked(), "offer", "customer", "COMPLETED", today.plusDays(1))

    @Test fun fullCampaignFlowUsesOneAppointmentAndOnePaymentLedger() {
        val state = booked()
        assertEquals("BOOKED", state.campaigns.single().recipients.single().status)
        assertEquals(1, state.appointments.size)
        assertEquals("offer", state.appointments.single().campaignId)
        val done = CampaignWorkflow.status(state, "offer", "customer", "COMPLETED", today.plusDays(1))
        assertEquals("COMPLETED", done.appointments.single().status)
        assertEquals("COMPLETED", done.jobs.single().status)
        assertEquals(today.plusDays(1).toString(), done.customers.single().lastService)
        assertEquals(150.0, done.customers.single().lastPrice, 0.001)
        val partial = CampaignWorkflow.recordPayment(done, "offer", "customer", 50.0, today.plusDays(1))
        assertEquals("COMPLETED", partial.campaigns.single().recipients.single().status)
        assertEquals(50.0, BusinessLogic.financials(partial.jobs, partial.appointments, today.plusDays(1)).received, 0.001)
        val paid = CampaignWorkflow.recordPayment(partial, "offer", "customer", 100.0, today.plusDays(1))
        assertEquals("PAID", paid.campaigns.single().recipients.single().status)
        assertEquals(150.0, CampaignFinance.received(paid, paid.campaigns.single()), 0.001)
        assertEquals(0.0, CampaignFinance.legacyUnlinked(paid, paid.campaigns.single()), 0.001)
        assertEquals(150.0, BusinessLogic.financials(paid.jobs, paid.appointments, today.plusDays(1)).received, 0.001)
        assertEquals(2, paid.jobs.single().payments.size)
        assertThrows(IllegalArgumentException::class.java) {
            CampaignWorkflow.recordPayment(paid, "offer", "customer", 150.0, today.plusDays(1))
        }
    }

    @Test fun failedSaveLeavesBothContactDateAndCampaignStageUntouchedThenRetryWorks() {
        val ready = CampaignWorkflow.approve(CampaignWorkflow.create(initial(), offer(), today),
            "offer", "customer", "Example Washing test message. Reply STOP to opt out.", today)
        var fail = true
        var persisted = ready
        val controller = WorkspaceController(ready) { proposed ->
            if (fail) throw IOException("Disk full")
            persisted = proposed
        }
        assertThrows(IOException::class.java) {
            controller.commit { CampaignWorkflow.recordSent(it, "offer", "customer", today) }
        }
        assertEquals(ready, controller.current)
        assertEquals(ready, persisted)
        assertEquals("", controller.current.customers.single().lastContact)
        assertEquals("APPROVED", controller.current.campaigns.single().recipients.single().status)
        fail = false
        controller.commit { CampaignWorkflow.recordSent(it, "offer", "customer", today) }
        assertEquals("SENT", persisted.campaigns.single().recipients.single().status)
        assertEquals(today.toString(), persisted.customers.single().lastContact)
    }

    @Test fun failedBookingWriteDoesNotConsumeCapacityOrCreateAnAppointment() {
        val state = interested()
        val controller = WorkspaceController(state) { throw IOException("Write failed") }
        assertThrows(IOException::class.java) {
            controller.commit { CampaignWorkflow.book(it, "offer", "customer", 150.0, "09:00", 60, now) }
        }
        assertTrue(controller.current.appointments.isEmpty())
        assertEquals("INTERESTED", controller.current.campaigns.single().recipients.single().status)
        assertEquals(0, CampaignRules.bookedSlots(controller.current.campaigns.single()))
    }

    @Test fun capacityAndConflictsAreCheckedAtTheMomentOfBooking() {
        val state = interested()
        val first = CampaignWorkflow.book(state, "offer", "customer", 150.0, "09:00", 60, now)
        assertThrows(IllegalArgumentException::class.java) {
            CampaignWorkflow.book(first, "offer", "customer", 150.0, "10:00", 60, now)
        }
        val full = state.copy(campaigns = state.campaigns.map { it.copy(capacity = 1,
            recipients = it.recipients + Recipient("another", status = "BOOKED")) })
        assertThrows(IllegalArgumentException::class.java) { CampaignWorkflow.book(full, "offer", "customer", 150.0, "09:00", 60, now) }
        val conflict = state.copy(appointments = listOf(Appointment(customerId = "other", service = "Other service",
            date = today.plusDays(1).toString(), time = "09:30", durationMinutes = 60)))
        assertThrows(IllegalArgumentException::class.java) { CampaignWorkflow.book(conflict, "offer", "customer", 150.0, "09:00", 60, now) }
    }

    @Test fun invalidMoneyMidnightAndPastStartsCannotBeBooked() {
        val state = interested()
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 1.111, 1_000_001.0).forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) { CampaignWorkflow.book(state, "offer", "customer", amount, "09:00", 60, now) }
        }
        assertThrows(IllegalArgumentException::class.java) { CampaignWorkflow.book(state, "offer", "customer", 150.0, "23:30", 60, now) }
        assertThrows(IllegalArgumentException::class.java) { CampaignWorkflow.book(state, "offer", "customer", 150.0, "09:00", 60, now.plusDays(2)) }
    }

    @Test fun cancellingFromTheCampaignFreesItsLinkedTimeSlot() {
        val state = booked()
        val cancelled = CampaignWorkflow.status(state, "offer", "customer", "CANCELLED", today)
        assertEquals("CANCELLED", cancelled.appointments.single().status)
        assertEquals("CANCELLED", cancelled.campaigns.single().recipients.single().status)
        assertEquals(0, CampaignRules.bookedSlots(cancelled.campaigns.single()))
        val replacement = Appointment(customerId = "customer", service = "House pressure washing",
            date = today.plusDays(1).toString(), time = "09:00", price = 150.0)
        assertEquals(2, WorkspaceRules.addAppointments(cancelled, listOf(replacement), now).appointments.size)
    }

    @Test fun completionCannotBeRecordedBeforeTheServiceDate() {
        assertThrows(IllegalArgumentException::class.java) { CampaignWorkflow.status(booked(), "offer", "customer", "COMPLETED", today) }
    }

    @Test fun completingFromScheduleUpdatesCampaignAndPreventsPrematureReactivation() {
        val state = booked()
        val done = WorkspaceRules.appointmentStatus(state, state.appointments.single().id, "COMPLETED", today.plusDays(1))
        assertEquals("COMPLETED", done.campaigns.single().recipients.single().status)
        assertTrue(ReactivationRules.groups(done, today.plusDays(100)).isEmpty())
        assertEquals(1, ReactivationRules.groups(done, today.plusDays(181)).sumOf { it.customers.size })
    }

    @Test fun anOptOutInvalidatesOtherApprovalsWithoutUndoingThemWithAStaleCampaignCopy() {
        val c = customer()
        val a = offer().copy(recipients = listOf(Recipient(c.id, "APPROVED")))
        val b = offer().copy(id = "second", recipients = listOf(Recipient(c.id, "APPROVED")))
        val state = initial().copy(campaigns = listOf(a, b))
        val optedOut = CampaignWorkflow.status(state, "offer", c.id, "OPTED_OUT", today)
        assertTrue(optedOut.customers.single().optedOut)
        assertEquals("OPTED_OUT", optedOut.campaigns.first().recipients.single().status)
        assertEquals("DRAFT", optedOut.campaigns.last().recipients.single().status)
        assertThrows(IllegalArgumentException::class.java) { CampaignWorkflow.recordSent(optedOut, "second", c.id, today) }
    }

    @Test fun actualInProgressBookingsAndFormattedDuplicatePhonesBlockAudienceSelection() {
        val c = customer()
        val pending = initial().copy(appointments = listOf(Appointment(customerId = c.id,
            service = c.service, date = today.toString(), time = "09:00", status = "IN_PROGRESS")))
        assertTrue(ReactivationRules.groups(pending, today).isEmpty())
        assertTrue(ReactivationRules.groups(initial(listOf(c, customer("duplicate", "(612) 555-0142"))), today).isEmpty())
    }

    @Test fun serviceWhitespaceAndCaseMatchTheSameNeighborhoodAudience() {
        val state = initial(listOf(customer(), customer("second", "6125550143").copy(service = " house PRESSURE washing ")))
        val groups = ReactivationRules.groups(state, today)
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().customers.size)
    }

    @Test fun existingCampaignPaymentsAreNotErasedOrInventedAsDatedReceipts() {
        val state = completed()
        val legacy = state.copy(jobs = emptyList(), campaigns = state.campaigns.map { campaign ->
            campaign.copy(recipients = campaign.recipients.map { it.copy(status = "PAID", paidAmount = 150.0) })
        })
        val job = JobMath.defaultFrom(legacy.appointments.single()).copy(status = "COMPLETED", serviceDetails = "Reviewed")
        val saved = WorkspaceRules.saveJob(legacy, job, today.plusDays(1))
        assertEquals(150.0, saved.campaigns.single().recipients.single().paidAmount, 0.001)
        assertEquals("PAID", saved.campaigns.single().recipients.single().status)
        assertTrue(saved.jobs.single().payments.isEmpty())
        assertEquals(150.0, CampaignFinance.received(saved, saved.campaigns.single()), 0.001)
    }

    @Test fun inboundConfirmationIsOneCommitAndPreservesConsentDefaults() {
        val request = BookingRequest(name = "Inbound Example", phone = "6125550148", zip = "56425", service = "Driveway",
            requestedDate = today.plusDays(1).toString(), requestedTime = "10:00")
        val initial = WorkspaceSnapshot(requests = listOf(request))
        val controller = WorkspaceController(initial) { throw IOException("Disk full") }
        assertThrows(IOException::class.java) { controller.commit { WorkspaceRules.confirmRequest(it, request, now) } }
        assertTrue(controller.current.customers.isEmpty())
        assertTrue(controller.current.appointments.isEmpty())
        assertEquals("NEW", controller.current.requests.single().status)
        val done = WorkspaceRules.confirmRequest(initial, request, now)
        assertFalse(done.customers.single().consent)
        assertEquals(done.appointments.single().id, done.requests.single().bookedAppointmentId)
        assertThrows(IllegalArgumentException::class.java) { WorkspaceRules.confirmRequest(done, request, now) }
    }

    @Test fun changingEligibilityRevokesApprovalAndFuturePaymentsAreRejected() {
        val state = CampaignWorkflow.approve(CampaignWorkflow.create(initial(), offer(), today),
            "offer", "customer", "Example Washing. Reply STOP to opt out.", today)
        val changed = WorkspaceRules.updateCustomer(state, state.customers.single().copy(issueOpen = true))
        assertEquals("DRAFT", changed.campaigns.single().recipients.single().status)
        val done = completed()
        val futurePayment = done.jobs.single().copy(payments = listOf(JobPayment(amount = 10.0, date = today.plusDays(10).toString())))
        assertThrows(IllegalArgumentException::class.java) { WorkspaceRules.saveJob(done, futurePayment, today.plusDays(1)) }
    }
    @Test fun finishingFromJobsAlsoCompletesTheAppointmentAndCannotSilentlyReopenIt() {
        val state = booked()
        val job = JobMath.defaultFrom(state.appointments.single()).copy(status = "COMPLETED")
        val done = WorkspaceRules.saveJob(state, job, today.plusDays(1))
        assertEquals("COMPLETED", done.appointments.single().status)
        assertEquals("COMPLETED", done.campaigns.single().recipients.single().status)
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceRules.saveJob(done, done.jobs.single().copy(status = "PLANNED"), today.plusDays(1))
        }
    }

    @Test fun prepaidServiceReconcilesToPaidWhenCompletedFromSchedule() {
        val state = booked()
        val appointment = state.appointments.single()
        val job = JobMath.defaultFrom(appointment).copy(payments = listOf(JobPayment(amount = 150.0, date = today.toString())))
        val deposited = WorkspaceRules.saveJob(state, job, today)
        val done = WorkspaceRules.appointmentStatus(deposited, appointment.id, "COMPLETED", today.plusDays(1))
        assertEquals("PAID", done.campaigns.single().recipients.single().status)
        assertEquals(150.0, CampaignFinance.received(done, done.campaigns.single()), 0.001)
    }

}

package com.routerevive.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A single immutable record set, written before it becomes visible in the UI. */
data class WorkspaceSnapshot(
    val customers: List<Customer> = emptyList(),
    val campaigns: List<Campaign> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val jobs: List<JobRecord> = emptyList(),
    val business: BusinessProfile = BusinessProfile(),
    val requests: List<BookingRequest> = emptyList()
)

class WorkspaceController(initial: WorkspaceSnapshot, private val persist: (WorkspaceSnapshot) -> Unit) {
    var current: WorkspaceSnapshot = initial
        private set

    fun commit(change: (WorkspaceSnapshot) -> WorkspaceSnapshot): WorkspaceSnapshot {
        val proposed = change(current)
        persist(proposed)
        current = proposed
        return proposed
    }

    /** Used only after a successful, already-persisted backup restore. */
    fun reload(restored: WorkspaceSnapshot): WorkspaceSnapshot {
        current = restored
        return restored
    }
}

fun LocalStore.loadWorkspace() = WorkspaceSnapshot(
    loadCustomers(), loadCampaigns(), loadAppointments(), loadJobs(), loadBusinessProfile(), loadBookingRequests())

fun LocalStore.saveWorkspace(state: WorkspaceSnapshot) = save(
    state.customers, state.campaigns, state.appointments, state.jobs, state.business, state.requests)

object WorkspaceRules {
    fun validMoney(value: Double): Boolean = value.isFinite() && value in 0.0..1_000_000.0 &&
        kotlin.math.round(value * 100.0) / 100.0 == value

    fun updateCustomer(state: WorkspaceSnapshot, customer: Customer): WorkspaceSnapshot {
        require(customer.name.isNotBlank() && customer.name.length <= 150 &&
            CampaignRules.digits(customer.phone).length in 10..15 &&
            customer.zip.matches(Regex("\\d{5}")) && customer.service.isNotBlank() &&
            customer.service.length <= 200 && ScheduleRules.validDate(customer.lastService) &&
            validMoney(customer.lastPrice)) { "Check the customer's name, phone, ZIP, service date and price." }
        val previous = state.customers.firstOrNull { it.id == customer.id }
        val affectsApproval = previous != null && (
            previous.name != customer.name || previous.phone != customer.phone ||
                previous.consent != customer.consent || previous.consentEvidence != customer.consentEvidence ||
                previous.consentDate != customer.consentDate || previous.optedOut != customer.optedOut ||
                previous.service != customer.service || previous.zip != customer.zip ||
                previous.lastService != customer.lastService || previous.issueOpen != customer.issueOpen ||
                previous.futureBooked != customer.futureBooked)
        return state.copy(
            customers = if (previous == null) state.customers + customer
                else state.customers.map { if (it.id == customer.id) customer else it },
            campaigns = if (!affectsApproval) state.campaigns else state.campaigns.map { campaign ->
                campaign.copy(recipients = campaign.recipients.map { recipient ->
                    if (recipient.customerId == customer.id && recipient.status == "APPROVED")
                        recipient.copy(status = "DRAFT", approvedAt = "", message = CampaignRules.message(customer, campaign))
                    else recipient
                })
            })
    }

    fun validAppointment(appointment: Appointment, now: LocalDateTime): Boolean =
        appointment.service.isNotBlank() && appointment.service.length <= 200 &&
            appointment.durationMinutes in 15..480 && validMoney(appointment.price) &&
            appointment.notes.length <= 4000 && runCatching {
                val start = LocalDateTime.of(LocalDate.parse(appointment.date), LocalTime.parse(appointment.time))
                !start.isBefore(now) && start.plusMinutes(appointment.durationMinutes.toLong()).toLocalDate() == start.toLocalDate()
            }.getOrDefault(false)

    fun addAppointments(state: WorkspaceSnapshot, proposed: List<Appointment>,
                        now: LocalDateTime = LocalDateTime.now()): WorkspaceSnapshot {
        require(proposed.isNotEmpty() && proposed.size <= 12) { "Choose 1–12 appointments." }
        val accepted = mutableListOf<Appointment>()
        for (appointment in proposed) {
            require(appointment.status == "SCHEDULED" &&
                state.customers.any { it.id == appointment.customerId } &&
                validAppointment(appointment, now) &&
                (state.appointments + accepted).none { it.id == appointment.id } &&
                !ScheduleRules.overlaps(appointment, state.appointments + accepted)) {
                "Appointment not saved: check its customer, future start, amount, duration and conflicts."
            }
            accepted += appointment
        }
        return state.copy(appointments = state.appointments + accepted)
    }

    fun reschedule(state: WorkspaceSnapshot, revised: Appointment,
                   now: LocalDateTime = LocalDateTime.now()): WorkspaceSnapshot {
        val previous = state.appointments.firstOrNull { it.id == revised.id }
            ?: error("Appointment is missing.")
        require(previous.status == "SCHEDULED" && previous.customerId == revised.customerId &&
            validAppointment(revised, now) && !ScheduleRules.overlaps(revised, state.appointments)) {
            "Booking not moved: choose a valid future time without a conflict."
        }
        state.campaigns.firstOrNull { it.id == previous.campaignId && it.profitPlan != null }?.let { campaign ->
            require(revised.date == campaign.visitDate) { "Keep this planned offer on its service date, or cancel it and create a new offer." }
            val planningErrors = ProfitPlanner.bookingErrors(campaign, revised.price, revised.time, revised.durationMinutes)
            require(planningErrors.isEmpty()) { planningErrors.joinToString(" ") }
        }
        return state.copy(appointments = state.appointments.map { if (it.id == revised.id) revised else it })
    }

    fun confirmRequest(state: WorkspaceSnapshot, incoming: BookingRequest,
                       now: LocalDateTime = LocalDateTime.now()): WorkspaceSnapshot {
        val request = state.requests.firstOrNull { it.id == incoming.id } ?: error("Request is missing.")
        require(request.status in setOf("NEW", "CONTACTED")) { "This request was already resolved." }
        val updated = request.copy(requestedDate = incoming.requestedDate,
            requestedTime = incoming.requestedTime, durationMinutes = incoming.durationMinutes,
            quotedPrice = incoming.quotedPrice)
        require(BookingRules.bookable(updated, state.appointments, now) && validMoney(updated.quotedPrice)) {
            "Request not booked: check the future start, amount, duration and scheduling conflicts."
        }
        val matches = state.customers.filter { CampaignRules.digits(it.phone) == CampaignRules.digits(request.phone) }
        require(matches.size <= 1) { "Multiple customer records use this phone. Resolve the duplicate before booking." }
        val customer = matches.singleOrNull() ?: Customer(name = request.name, phone = request.phone,
            zip = request.zip, service = request.service, lastService = now.toLocalDate().toString(),
            notes = "New inbound inquiry; no earlier completed service verified.", consent = false)
        val appointment = Appointment(customerId = customer.id, service = request.service,
            date = updated.requestedDate, time = updated.requestedTime,
            durationMinutes = updated.durationMinutes, price = updated.quotedPrice, notes = request.notes)
        return state.copy(
            customers = if (matches.isEmpty()) state.customers + customer else state.customers,
            appointments = state.appointments + appointment,
            requests = state.requests.map { if (it.id == request.id)
                updated.copy(status = "BOOKED", bookedAppointmentId = appointment.id) else it })
    }

    fun appointmentStatus(state: WorkspaceSnapshot, id: String, status: String,
                          today: LocalDate = LocalDate.now()): WorkspaceSnapshot {
        val appointment = state.appointments.firstOrNull { it.id == id } ?: error("Appointment is missing.")
        require(appointment.status in setOf("SCHEDULED", "IN_PROGRESS") &&
            status in setOf("IN_PROGRESS", "COMPLETED", "CANCELLED") &&
            (status != "IN_PROGRESS" || appointment.status == "SCHEDULED")) { "This appointment status already changed." }
        if (status == "COMPLETED") require(!LocalDate.parse(appointment.date).isAfter(today)) {
            "This service is scheduled in the future. Move it to the actual service date before completing it."
        }
        var next = state.copy(
            appointments = state.appointments.map { if (it.id == id) it.copy(status = status) else it },
            campaigns = state.campaigns.map { campaign ->
                if (campaign.id != appointment.campaignId || status == "IN_PROGRESS") campaign
                else campaign.copy(recipients = campaign.recipients.map { recipient ->
                    if (recipient.customerId == appointment.customerId && recipient.status == "BOOKED")
                        recipient.copy(status = status) else recipient
                })
            })
        if (status == "IN_PROGRESS") {
            val job = state.jobs.firstOrNull { it.appointmentId == id } ?: JobMath.defaultFrom(appointment)
                .copy(businessName = state.business.name)
            next = next.copy(jobs = state.jobs.filterNot { it.appointmentId == id } +
                JobProgress.update(job, "IN_PROGRESS", today))
        }
        if (status == "COMPLETED") {
            val job = state.jobs.firstOrNull { it.appointmentId == id } ?: JobMath.defaultFrom(appointment)
                .copy(businessName = state.business.name)
            next = next.copy(jobs = state.jobs.filterNot { it.appointmentId == id } +
                JobProgress.update(job, "COMPLETED", today),
                customers = state.customers.map { customer ->
                    if (customer.id == appointment.customerId &&
                        runCatching { !LocalDate.parse(customer.lastService).isAfter(today) }.getOrDefault(true))
                        customer.copy(lastService = today.toString(), service = appointment.service,
                            lastPrice = JobMath.subtotal(job)) else customer
                })
        }
        if (status == "COMPLETED") {
            val completed = next.jobs.first { it.appointmentId == id }
            val paid = JobMath.paid(completed)
            if (paid > 0) next = next.copy(campaigns = next.campaigns.map { campaign ->
                if (campaign.id != appointment.campaignId) campaign else campaign.copy(recipients = campaign.recipients.map { r ->
                    if (r.customerId == appointment.customerId) r.copy(paidAmount = maxOf(r.paidAmount, paid),
                        status = if (JobMath.balance(completed) == 0.0) "PAID" else "COMPLETED") else r
                })
            })
        }
        return next
    }

    fun saveJob(state: WorkspaceSnapshot, job: JobRecord,
                today: LocalDate = LocalDate.now()): WorkspaceSnapshot {
        val appointment = state.appointments.firstOrNull { it.id == job.appointmentId }
        require(appointment != null && appointment.customerId == job.customerId && JobProgress.valid(job.status) &&
            (job.statusUpdatedAt.isBlank() || ScheduleRules.validDate(job.statusUpdatedAt)) &&
            job.lineItems.size <= 20 && job.lineItems.all(JobMath::validLineItem) && job.photos.size <= 16 &&
            job.payments.all { validMoney(it.amount) && it.amount > 0 &&
                runCatching { !LocalDate.parse(it.date).isAfter(today) }.getOrDefault(false) } &&
            job.payments.map { it.id }.distinct().size == job.payments.size &&
            JobMath.paid(job) <= JobMath.subtotal(job)) { "Job not saved: check its amounts, payment dates and appointment." }
        val previous = state.jobs.firstOrNull { it.appointmentId == job.appointmentId }
        require(previous == null || previous.id == job.id) { "The saved job changed. Reopen it before editing." }
        if (appointment.status == "COMPLETED" && job.status != "COMPLETED") {
            require(previous?.status != "COMPLETED") { "A completed service cannot be reopened from progress controls. Invoice details remain editable." }
            return saveJob(state, job.copy(status = "COMPLETED"), today)
        }
        require(appointment.status != "CANCELLED" || job.status == (previous?.status ?: JobMath.defaultFrom(appointment).status)) {
            "A cancelled appointment cannot be started or completed. Its receipts remain editable."
        }
        var next = state.copy(jobs = state.jobs.filterNot { it.appointmentId == job.appointmentId } + job)
        if (job.status == "IN_PROGRESS" && appointment.status == "SCHEDULED")
            next = appointmentStatus(next, appointment.id, "IN_PROGRESS", today)
        if (job.status == "COMPLETED" && previous?.status != "COMPLETED" &&
            appointment.status in setOf("SCHEDULED", "IN_PROGRESS")) {
            next = appointmentStatus(next, appointment.id, "COMPLETED", today)
        }
        // The campaign and the financial report read the same job payment ledger.
        val paid = JobMath.paid(job)
        val previousPaid = previous?.let(JobMath::paid) ?: 0.0
        next = next.copy(campaigns = next.campaigns.map { campaign ->
            if (campaign.id != appointment.campaignId) campaign else campaign.copy(recipients = campaign.recipients.map { r ->
                if (r.customerId != appointment.customerId || r.status !in setOf("BOOKED", "COMPLETED", "PAID")) r
                else {
                    val legacy = r.paidAmount > previousPaid
                    r.copy(paidAmount = if (legacy) maxOf(r.paidAmount, paid) else paid,
                        status = if (legacy && r.status == "PAID") "PAID"
                        else if (appointment.status == "COMPLETED" || job.status == "COMPLETED")
                            if (JobMath.balance(job) == 0.0 && paid > 0.0) "PAID" else "COMPLETED" else r.status)
                }
            })
        })
        return next
    }
}

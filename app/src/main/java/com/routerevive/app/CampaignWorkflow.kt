package com.routerevive.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** Shared eligibility context avoids rescanning every record for every customer. */
class EligibilityIndex(state: WorkspaceSnapshot, val today: LocalDate = LocalDate.now(),
                       ignoredCampaign: String = "") {
    val duplicatePhones = state.customers.groupBy { CampaignRules.digits(it.phone) }
        .filterValues { rows -> rows.map { it.id }.distinct().size > 1 }.keys
    val activeCustomerIds = state.campaigns.filter { campaign -> campaign.id != ignoredCampaign &&
        runCatching { !LocalDate.parse(campaign.expiryDate).isBefore(today) }.getOrDefault(false)
    }.flatMap { campaign -> campaign.recipients.filter {
        it.status in setOf("APPROVED", "SENT", "INTERESTED", "BOOKED", "COMPLETED", "PAID")
    }.map { it.customerId } }.toSet()
    private val futureIds = state.appointments.filter { it.status in setOf("SCHEDULED", "IN_PROGRESS") &&
        runCatching { !LocalDate.parse(it.date).isBefore(today) }.getOrDefault(false)
    }.map { it.customerId }.toSet()
    private val customers = state.customers
    private val campaigns = state.campaigns

    fun reasons(customer: Customer, zip: String, service: String): List<String> = CampaignRules.reasons(
        customer.copy(futureBooked = customer.futureBooked || customer.id in futureIds),
        zip, service, today, customers, campaigns, this)
}

data class ReactivationGroup(val zip: String, val service: String, val customers: List<Customer>)

object ReactivationRules {
    fun groups(state: WorkspaceSnapshot, today: LocalDate = LocalDate.now()): List<ReactivationGroup> {
        val index = EligibilityIndex(state, today)
        return state.customers.filter { index.reasons(it, it.zip, it.service).isEmpty() }
            .groupBy { it.zip to it.service.trim().lowercase(Locale.ROOT) }
            .map { (key, customers) -> ReactivationGroup(key.first, customers.first().service.trim(), customers) }
            .sortedWith(compareByDescending<ReactivationGroup> { it.customers.size }.thenBy { it.zip }.thenBy { it.service })
    }
}

object CampaignWorkflow {
    fun offerErrors(campaign: Campaign, today: LocalDate = LocalDate.now()): List<String> {
        val errors = mutableListOf<String>()
        if (campaign.title.isBlank() || campaign.title.length > 150) errors += "Add a campaign title (up to 150 characters)."
        if (campaign.businessName.isBlank() || campaign.businessName.length > 100) errors += "Add your business name (up to 100 characters)."
        if (!campaign.zip.matches(Regex("\\d{5}"))) errors += "Enter a five-digit ZIP code."
        if (campaign.service.isBlank() || campaign.service.length > 200) errors += "Choose the service to offer."
        val visit = runCatching { LocalDate.parse(campaign.visitDate) }.getOrNull()
        val expiry = runCatching { LocalDate.parse(campaign.expiryDate) }.getOrNull()
        if (visit == null || !visit.isAfter(today) || visit.isAfter(today.plusDays(365)))
            errors += "Choose a service date within the next 365 days."
        if (expiry == null || expiry.isBefore(today) || visit == null || expiry.isAfter(visit))
            errors += "The offer must expire between today and the service date."
        if (!WorkspaceRules.validMoney(campaign.price) || campaign.price <= 0) errors += "Enter a positive price with at most two decimal places."
        if (campaign.discountPct !in 0..50) errors += "Choose a discount from 0% to 50%."
        if (campaign.capacity !in 1..99) errors += "Choose 1–99 booking slots."
        campaign.profitPlan?.let { errors += ProfitPlanner.planErrors(it) }
        return errors
    }

    fun create(state: WorkspaceSnapshot, campaign: Campaign, today: LocalDate = LocalDate.now()): WorkspaceSnapshot {
        require(campaign.profitPlan == null) { "Create a planned offer through Profit planner to verify its opening and nearby audience." }
        require(offerErrors(campaign, today).isEmpty()) { offerErrors(campaign, today).joinToString(" ") }
        require(state.campaigns.none { it.id == campaign.id }) { "This offer was already saved." }
        val index = EligibilityIndex(state, today)
        val eligible = state.customers.filter { index.reasons(it, campaign.zip, campaign.service).isEmpty() }
        require(eligible.isNotEmpty()) { "No eligible customers match this ZIP and service." }
        val ready = campaign.copy(recipients = eligible.map {
            Recipient(customerId = it.id, message = CampaignRules.message(it, campaign))
        })
        return state.copy(campaigns = state.campaigns + ready,
            business = if (state.business.name.isBlank()) state.business.copy(name = campaign.businessName) else state.business)
    }

    fun contactReasons(state: WorkspaceSnapshot, campaign: Campaign, customer: Customer,
                       today: LocalDate = LocalDate.now()): List<String> {
        val errors = EligibilityIndex(state, today, campaign.id).reasons(customer, campaign.zip, campaign.service).toMutableList()
        errors += ProfitPlanner.locationErrors(state, campaign, customer)
        if (runCatching { LocalDate.parse(campaign.expiryDate).isBefore(today) }.getOrDefault(true))
            errors += "The offer has expired."
        if (runCatching { LocalDate.parse(campaign.visitDate).isBefore(today) }.getOrDefault(true))
            errors += "The planned service date has passed."
        return errors
    }

    private fun findCampaign(state: WorkspaceSnapshot, id: String) = state.campaigns.firstOrNull { it.id == id }
        ?: error("Campaign is missing.")
    private fun findRecipient(campaign: Campaign, id: String) = campaign.recipients.firstOrNull { it.customerId == id }
        ?: error("Customer is no longer in this campaign.")
    private fun replace(state: WorkspaceSnapshot, campaign: Campaign, recipient: Recipient) = state.copy(
        campaigns = state.campaigns.map { c -> if (c.id != campaign.id) c else c.copy(
            recipients = c.recipients.map { if (it.customerId == recipient.customerId) recipient else it }) })

    fun approve(state: WorkspaceSnapshot, campaignId: String, customerId: String, message: String,
                today: LocalDate = LocalDate.now()): WorkspaceSnapshot {
        val campaign = findCampaign(state, campaignId)
        val recipient = findRecipient(campaign, customerId)
        val customer = state.customers.firstOrNull { it.id == customerId } ?: error("Customer is missing.")
        require(recipient.status in setOf("DRAFT", "APPROVED")) { "This message has already moved beyond approval." }
        require(contactReasons(state, campaign, customer, today).isEmpty()) { "This customer is no longer eligible for this offer." }
        require(message.isNotBlank() && message.length <= 2000 && Regex("\\bSTOP\\b", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
            "Keep the message under 2,000 characters and include the STOP opt-out instruction."
        }
        return replace(state, campaign, recipient.copy(status = "APPROVED", message = message.trim(), approvedAt = today.toString()))
    }

    fun recordSent(state: WorkspaceSnapshot, campaignId: String, customerId: String,
                   today: LocalDate = LocalDate.now()): WorkspaceSnapshot {
        val campaign = findCampaign(state, campaignId)
        val recipient = findRecipient(campaign, customerId)
        val customer = state.customers.firstOrNull { it.id == customerId } ?: error("Customer is missing.")
        require(recipient.status == "APPROVED" && recipient.message.isNotBlank() &&
            contactReasons(state, campaign, customer, today).isEmpty()) { "Sending is blocked: review this customer's eligibility and approval." }
        return replace(state, campaign, recipient.copy(status = "SENT", sentAt = today.toString())).copy(
            customers = state.customers.map { if (it.id == customerId) it.copy(lastContact = today.toString()) else it })
    }

    fun status(state: WorkspaceSnapshot, campaignId: String, customerId: String, status: String,
               today: LocalDate = LocalDate.now()): WorkspaceSnapshot {
        val campaign = findCampaign(state, campaignId)
        val recipient = findRecipient(campaign, customerId)
        if (status == "OPTED_OUT") {
            require(recipient.status in setOf("DRAFT", "APPROVED", "SENT", "INTERESTED", "DECLINED")) { "Use the customer profile to update contact preferences." }
            val customer = state.customers.firstOrNull { it.id == customerId } ?: error("Customer is missing.")
            val next = WorkspaceRules.updateCustomer(state, customer.copy(optedOut = true))
            val current = findCampaign(next, campaignId)
            return replace(next, current, findRecipient(current, customerId).copy(status = "OPTED_OUT"))
        }
        require(when (recipient.status) {
            "SENT" -> status in setOf("INTERESTED", "DECLINED")
            "INTERESTED" -> status == "DECLINED"
            "BOOKED" -> status in setOf("COMPLETED", "CANCELLED")
            else -> false
        }) { "That campaign action is no longer available. Refresh the current record." }
        if (recipient.status == "BOOKED") {
            val appointment = linkedAppointment(state, campaignId, customerId) ?: error("Open Schedule to resolve the missing appointment.")
            return WorkspaceRules.appointmentStatus(state, appointment.id, status, today)
        }
        return replace(state, campaign, recipient.copy(status = status))
    }

    fun linkedAppointment(state: WorkspaceSnapshot, campaignId: String, customerId: String): Appointment? =
        state.appointments.singleOrNull { it.campaignId == campaignId && it.customerId == customerId && it.status != "CANCELLED" }

    fun book(state: WorkspaceSnapshot, campaignId: String, customerId: String, amount: Double,
             time: String, duration: Int, now: LocalDateTime = LocalDateTime.now()): WorkspaceSnapshot {
        val campaign = findCampaign(state, campaignId)
        val recipient = findRecipient(campaign, customerId)
        require(recipient.status == "INTERESTED" && CampaignRules.bookedSlots(campaign) < campaign.capacity) {
            "This customer must be interested and a booking slot must be available."
        }
        require(linkedAppointment(state, campaignId, customerId) == null) { "This customer already has an appointment for this offer." }
        require(amount > 0 && WorkspaceRules.validMoney(amount)) { "Enter a positive agreed price with at most two decimal places." }
        val planningErrors = ProfitPlanner.bookingErrors(campaign, amount, time, duration)
        require(planningErrors.isEmpty()) { planningErrors.joinToString(" ") }
        val proposed = Appointment(customerId = customerId, service = campaign.service, date = campaign.visitDate,
            time = time, durationMinutes = duration, price = amount, campaignId = campaignId)
        val next = WorkspaceRules.addAppointments(state, listOf(proposed), now)
        return replace(next, campaign, recipient.copy(status = "BOOKED", bookedAmount = amount, paidAmount = 0.0))
    }

    fun recordPayment(state: WorkspaceSnapshot, campaignId: String, customerId: String,
                      amount: Double, today: LocalDate = LocalDate.now()): WorkspaceSnapshot {
        val campaign = findCampaign(state, campaignId)
        val recipient = findRecipient(campaign, customerId)
        require(recipient.status == "COMPLETED") { "Complete this service before recording a campaign payment." }
        val appointment = linkedAppointment(state, campaignId, customerId) ?: error("The linked appointment is missing. Open Jobs to review it.")
        require(appointment.status == "COMPLETED") { "The appointment must be completed first." }
        val job = state.jobs.firstOrNull { it.appointmentId == appointment.id } ?: JobMath.defaultFrom(appointment)
            .copy(businessName = campaign.businessName, status = "COMPLETED", statusUpdatedAt = today.toString())
        require(recipient.paidAmount <= JobMath.paid(job)) { "An earlier campaign payment needs review in Jobs before recording another receipt." }
        require(JobMath.canRecordPayment(job, amount)) { "Enter an actual payment with two decimal places, no greater than the remaining balance." }
        return WorkspaceRules.saveJob(state, job.copy(invoiceIssued = true,
            payments = job.payments + JobPayment(amount = amount, date = today.toString(),
                method = "Other", notes = "Recorded from campaign")), today)
    }
}

object CampaignFinance {
    fun received(state: WorkspaceSnapshot, campaign: Campaign): Double {
        val ids = state.appointments.filter { it.campaignId == campaign.id }.map { it.id }.toSet()
        return state.jobs.filter { it.appointmentId in ids }.sumOf(JobMath::paid) + legacyUnlinked(state, campaign)
    }
    fun legacyUnlinked(state: WorkspaceSnapshot, campaign: Campaign): Double = campaign.recipients.sumOf { recipient ->
        val ids = state.appointments.filter { it.campaignId == campaign.id && it.customerId == recipient.customerId }
            .map { it.id }.toSet()
        (recipient.paidAmount - state.jobs.filter { it.appointmentId in ids }.sumOf(JobMath::paid)).coerceAtLeast(0.0)
    }
}

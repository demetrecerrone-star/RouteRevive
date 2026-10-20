package com.routerevive.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.json.JSONObject

/** Owner-entered planning assumptions, saved with the offer rather than the receipt ledger. */
data class ProfitPlan(
    val anchorAppointmentId: String,
    val windowStart: String,
    val windowEnd: String,
    val serviceMinutes: Int,
    val travelMinutes: Int,
    val crewHourlyCost: Double,
    val materialsCost: Double,
    val vehicleCost: Double,
    val targetMarginPct: Int,
    val radiusMiles: Int
) {
    val blockMinutes: Int get() = serviceMinutes + travelMinutes

    fun toJson() = JSONObject().apply {
        put("anchorAppointmentId", anchorAppointmentId)
        put("windowStart", windowStart); put("windowEnd", windowEnd)
        put("serviceMinutes", serviceMinutes); put("travelMinutes", travelMinutes)
        put("crewHourlyCost", crewHourlyCost); put("materialsCost", materialsCost)
        put("vehicleCost", vehicleCost); put("targetMarginPct", targetMarginPct)
        put("radiusMiles", radiusMiles)
    }

    companion object {
        private fun number(j: JSONObject, key: String): Number = (j.get(key) as? Number)
            ?: error("Invalid numeric planning field: $key")
        private fun whole(j: JSONObject, key: String): Int = number(j, key).toString().toBigDecimal().intValueExact()

        fun fromJson(j: JSONObject): ProfitPlan = ProfitPlan(
            j.getString("anchorAppointmentId"), j.getString("windowStart"), j.getString("windowEnd"),
            whole(j, "serviceMinutes"), whole(j, "travelMinutes"), number(j, "crewHourlyCost").toDouble(),
            number(j, "materialsCost").toDouble(), number(j, "vehicleCost").toDouble(), whole(j, "targetMarginPct"),
            whole(j, "radiusMiles")
        ).also { require(ProfitPlanner.planErrors(it).isEmpty()) { "Invalid profit planning assumptions." } }
    }
}

data class ScheduleOpening(val start: String, val end: String) {
    val minutes: Int get() = LocalTime.parse(end).toSecondOfDay() / 60 - LocalTime.parse(start).toSecondOfDay() / 60
}

data class ProfitEstimate(
    val netPrice: Double,
    val costPerJob: Double,
    val contributionPerJob: Double,
    val marginPct: Double,
    val minimumPrice: Double,
    val maximumDiscountPct: Int?,
    val fullPriceContribution: Double,
    val meetsTarget: Boolean
)

data class ProfitOpportunity(
    val estimate: ProfitEstimate,
    val customers: List<Pair<Customer, Double>>,
    val capacity: Int,
    val missingPins: Int
)

data class ProfitOfferRequest(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val price: Double,
    val discountPct: Int,
    val plan: ProfitPlan
)

object ProfitPlanner {
    private fun time(value: String): LocalTime? = value.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) }
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    fun planErrors(plan: ProfitPlan): List<String> = buildList {
        val start = time(plan.windowStart)
        val end = time(plan.windowEnd)
        if (plan.anchorAppointmentId.isBlank()) add("Choose a mapped appointment to build around.")
        if (start == null || end == null || !end.isAfter(start)) add("Choose an opening that ends later on the same day.")
        if (plan.serviceMinutes !in 15..480 || plan.travelMinutes !in 0..240 || plan.blockMinutes !in 15..480)
            add("Service plus travel/setup must total 15–480 minutes.")
        if (listOf(plan.crewHourlyCost, plan.materialsCost, plan.vehicleCost).any { !WorkspaceRules.validMoney(it) })
            add("Enter each cost with up to two decimal places. Enter 0 only when that cost does not apply.")
        if (plan.targetMarginPct !in 0..90) add("Choose a target contribution margin from 0% to 90%.")
        if (plan.radiusMiles !in 1..25) add("Choose a straight-line search radius from 1 to 25 miles.")
    }

    /** Decimal currency with conservative rounding of labor cost and the minimum selling price. */
    fun estimate(plan: ProfitPlan, price: Double, discountPct: Int): ProfitEstimate {
        require(planErrors(plan).isEmpty()) { planErrors(plan).joinToString(" ") }
        require(WorkspaceRules.validMoney(price) && price > 0 && discountPct in 0..50) {
            "Enter a positive starting price and a discount from 0% to 50%."
        }
        val base = BigDecimal.valueOf(price)
        val labor = BigDecimal.valueOf(plan.crewHourlyCost).multiply(BigDecimal(plan.blockMinutes))
            .divide(BigDecimal(60), 2, RoundingMode.CEILING)
        val cost = labor + BigDecimal.valueOf(plan.materialsCost) + BigDecimal.valueOf(plan.vehicleCost)
        fun discounted(percent: Int) = base.multiply(BigDecimal(100 - percent))
            .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        val net = discounted(discountPct)
        val floor = cost.multiply(BigDecimal(100)).divide(BigDecimal(100 - plan.targetMarginPct), 2, RoundingMode.CEILING)
        val contribution = net - cost
        val margin = if (net.signum() > 0) contribution.multiply(BigDecimal(100))
            .divide(net, 4, RoundingMode.HALF_UP).toDouble() else 0.0
        return ProfitEstimate(net.toDouble(), cost.toDouble(), contribution.toDouble(), margin,
            floor.toDouble(), (0..50).lastOrNull { discounted(it) >= floor && discounted(it).signum() > 0 },
            (base - cost).toDouble(), net >= floor && net.signum() > 0)
    }

    /** Find independent openings; canceled appointments release time, overlapping records are merged. */
    fun openings(day: LocalDate, start: String, end: String, appointments: List<Appointment>): List<ScheduleOpening> {
        val from = time(start) ?: error("Enter the day start as HH:mm.")
        val until = time(end) ?: error("Enter the day end as HH:mm.")
        require(until.isAfter(from)) { "The day must end after it starts, without crossing midnight." }
        val dayStart = day.atTime(from)
        val dayEnd = day.atTime(until)
        val intervals = appointments.filter { it.status != "CANCELLED" }.mapNotNull { appointment ->
            val date = runCatching { LocalDate.parse(appointment.date) }.getOrNull()
                ?: error("An appointment has an invalid date. Correct it in Schedule before planning.")
            if (date != day && date != day.minusDays(1)) return@mapNotNull null
            val at = time(appointment.time) ?: error("An appointment has an invalid time. Correct it in Schedule.")
            require(appointment.durationMinutes in 15..480) { "Correct the appointment duration in Schedule before planning." }
            val a = date.atTime(at)
            val b = a.plusMinutes(appointment.durationMinutes.toLong())
            if (a >= dayEnd || b <= dayStart) null else maxOf(a, dayStart) to minOf(b, dayEnd)
        }.sortedBy { it.first }
        val gaps = mutableListOf<ScheduleOpening>()
        var cursor = dayStart
        for ((a, b) in intervals) {
            if (a > cursor) gaps += ScheduleOpening(cursor.toLocalTime().toString(), a.toLocalTime().toString())
            cursor = maxOf(cursor, b)
        }
        if (cursor < dayEnd) gaps += ScheduleOpening(cursor.toLocalTime().toString(), until.toString())
        return gaps
    }

    fun evaluate(state: WorkspaceSnapshot, request: ProfitOfferRequest,
                 today: LocalDate = LocalDate.now()): ProfitOpportunity {
        val estimate = estimate(request.plan, request.price, request.discountPct)
        val day = LocalDate.parse(request.date)
        require(day.isAfter(today) && !day.isAfter(today.plusDays(365))) { "Choose a service date within the next 365 days." }
        val anchor = state.appointments.singleOrNull { it.id == request.plan.anchorAppointmentId }
            ?: error("The anchor appointment is missing. Choose another appointment.")
        require(anchor.date == request.date && anchor.status in setOf("SCHEDULED", "IN_PROGRESS")) {
            "The anchor appointment changed. Choose an active appointment on this date."
        }
        val customer = state.customers.singleOrNull { it.id == anchor.customerId }
            ?: error("The anchor customer is missing.")
        require(!customer.demo && NeighborhoodLogic.validPin(customer)) { "Add a verified map pin to the anchor customer first." }
        val opening = ScheduleOpening(request.plan.windowStart, request.plan.windowEnd)
        require(openings(day, opening.start, opening.end, state.appointments) == listOf(opening)) {
            "This opening is no longer free. Review the latest schedule."
        }
        val index = EligibilityIndex(state, today)
        val eligible = state.customers.filter { it.id != customer.id && index.reasons(it, customer.zip, anchor.service).isEmpty() }
        val candidates = eligible.filter(NeighborhoodLogic::validPin)
            .map { it to NeighborhoodLogic.distanceMiles(customer, it) }
            .filter { it.second <= request.plan.radiusMiles }
            .sortedWith(compareBy<Pair<Customer, Double>> { it.second }.thenBy { it.first.id })
        return ProfitOpportunity(estimate, candidates,
            minOf(opening.minutes / request.plan.blockMinutes, candidates.size, 99),
            eligible.count { !NeighborhoodLogic.validPin(it) })
    }

    fun create(state: WorkspaceSnapshot, request: ProfitOfferRequest,
               today: LocalDate = LocalDate.now()): WorkspaceSnapshot {
        val opportunity = evaluate(state, request, today)
        require(opportunity.estimate.meetsTarget) { "The discounted price is below your target margin. Adjust price, discount or costs." }
        require(opportunity.capacity > 0) { "No eligible nearby jobs fit this opening with the entered travel allowance." }
        val anchor = state.appointments.single { it.id == request.plan.anchorAppointmentId }
        val customer = state.customers.single { it.id == anchor.customerId }
        val campaign = Campaign(id = request.id, title = "${customer.zip} · ${request.plan.windowStart}–${request.plan.windowEnd}",
            businessName = state.business.name, zip = customer.zip, service = anchor.service,
            visitDate = request.date, expiryDate = LocalDate.parse(request.date).minusDays(1).toString(),
            price = request.price, discountPct = request.discountPct, capacity = opportunity.capacity,
            createdAt = today.toString(), profitPlan = request.plan)
        require(CampaignWorkflow.offerErrors(campaign, today).isEmpty()) { CampaignWorkflow.offerErrors(campaign, today).joinToString(" ") }
        require(state.campaigns.none { it.id == request.id }) { "This offer was already saved." }
        val ready = campaign.copy(recipients = opportunity.customers.map { (recipient, _) ->
            Recipient(recipient.id, message = CampaignRules.message(recipient, campaign))
        })
        return state.copy(campaigns = state.campaigns + ready)
    }

    fun bookingErrors(campaign: Campaign, amount: Double, start: String, duration: Int): List<String> {
        val plan = campaign.profitPlan ?: return emptyList()
        if (planErrors(plan).isNotEmpty()) return listOf("This offer's planning assumptions need review.")
        val errors = mutableListOf<String>()
        if (!WorkspaceRules.validMoney(amount) || amount < estimate(plan, campaign.price, campaign.discountPct).minimumPrice || amount <= 0)
            errors += "The agreed price must meet this offer's minimum price for its target margin."
        if (duration != plan.blockMinutes) errors += "Reserve ${plan.blockMinutes} minutes, including travel and setup."
        val at = time(start)
        if (at == null || at < time(plan.windowStart)!! ||
            at.toSecondOfDay() / 60 + duration > time(plan.windowEnd)!!.toSecondOfDay() / 60)
            errors += "Choose a start that fits the ${plan.windowStart}–${plan.windowEnd} opening."
        return errors
    }

    fun nextStart(campaign: Campaign, appointments: List<Appointment>): String? {
        val plan = campaign.profitPlan ?: return null
        return runCatching { openings(LocalDate.parse(campaign.visitDate), plan.windowStart, plan.windowEnd, appointments)
            .firstOrNull { it.minutes >= plan.blockMinutes }?.start }.getOrNull()
    }

    fun locationErrors(state: WorkspaceSnapshot, campaign: Campaign, customer: Customer): List<String> {
        val plan = campaign.profitPlan ?: return emptyList()
        val anchor = state.appointments.singleOrNull { it.id == plan.anchorAppointmentId }
        val anchorCustomer = state.customers.singleOrNull { it.id == anchor?.customerId }
        if (anchor == null || anchor.date != campaign.visitDate || anchor.status == "CANCELLED" ||
            anchorCustomer == null || !NeighborhoodLogic.validPin(anchorCustomer))
            return listOf("The anchor appointment or map pin changed. Review this neighborhood plan.")
        return if (!NeighborhoodLogic.validPin(customer) ||
            NeighborhoodLogic.distanceMiles(anchorCustomer, customer) > plan.radiusMiles)
            listOf("This customer is outside the plan's mapped radius.") else emptyList()
    }
}

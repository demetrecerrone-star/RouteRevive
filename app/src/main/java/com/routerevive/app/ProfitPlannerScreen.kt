package com.routerevive.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

private fun planMoney(value: Double) = "$" + String.format(Locale.US, "%,.2f", value)
private fun inputMoney(value: Double?) = value?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()

@Composable
fun ProfitPlannerScreen(state: WorkspaceSnapshot, onMap: () -> Unit, onSchedule: () -> Unit,
                        onCreate: (ProfitOfferRequest) -> Boolean) {
    val today = LocalDate.now()
    val futureAnchors = state.appointments.filter { appointment ->
        appointment.status == "SCHEDULED" && runCatching {
            LocalDate.parse(appointment.date) in today.plusDays(1)..today.plusDays(365)
        }.getOrDefault(false) && state.customers.any {
            it.id == appointment.customerId && !it.demo && NeighborhoodLogic.validPin(it)
        }
    }.sortedWith(compareBy<Appointment> { it.date }.thenBy { it.time })
    var date by rememberSaveable { mutableStateOf(futureAnchors.firstOrNull()?.date ?: today.plusDays(1).toString()) }
    var anchorId by rememberSaveable { mutableStateOf("") }
    var dayStart by rememberSaveable { mutableStateOf("09:00") }
    var dayEnd by rememberSaveable { mutableStateOf("17:00") }
    var selectedWindow by rememberSaveable { mutableStateOf("") }
    var anchorMenu by remember { mutableStateOf(false) }
    val anchors = futureAnchors.filter { it.date == date }
    val anchor = anchors.firstOrNull { it.id == anchorId } ?: anchors.firstOrNull()
    val anchorCustomer = state.customers.firstOrNull { it.id == anchor?.customerId }
    val previous = state.campaigns.asReversed().firstOrNull {
        it.profitPlan != null && it.service.trim().equals(anchor?.service?.trim(), true)
    }?.profitPlan
    var price by rememberSaveable(anchor?.id) { mutableStateOf(inputMoney(anchor?.price?.takeIf { it > 0 })) }
    var discount by rememberSaveable { mutableStateOf("0") }
    var serviceMinutes by rememberSaveable(anchor?.id) { mutableStateOf((previous?.serviceMinutes ?: anchor?.durationMinutes ?: 60).toString()) }
    var travelMinutes by rememberSaveable(anchor?.id) { mutableStateOf((previous?.travelMinutes ?: 15).toString()) }
    var crewCost by rememberSaveable(anchor?.id) { mutableStateOf(inputMoney(previous?.crewHourlyCost)) }
    var materials by rememberSaveable(anchor?.id) { mutableStateOf(inputMoney(previous?.materialsCost)) }
    var vehicle by rememberSaveable(anchor?.id) { mutableStateOf(inputMoney(previous?.vehicleCost)) }
    var margin by rememberSaveable(anchor?.id) { mutableStateOf((previous?.targetMarginPct ?: 30).toString()) }
    var radius by rememberSaveable(anchor?.id) { mutableStateOf((previous?.radiusMiles ?: 3).toString()) }
    val id = rememberSaveable { UUID.randomUUID().toString() }
    val gaps = remember(state.appointments, date, dayStart, dayEnd) {
        runCatching { ProfitPlanner.openings(LocalDate.parse(date), dayStart, dayEnd, state.appointments) }
    }
    val block = (serviceMinutes.toIntOrNull() ?: 0).toLong() + (travelMinutes.toIntOrNull() ?: 0).toLong()
    val openings = gaps.getOrDefault(emptyList())
    val opening = openings.firstOrNull { "${it.start}/${it.end}" == selectedWindow }
        ?: openings.firstOrNull { it.minutes >= block } ?: openings.firstOrNull()
    val plan = ProfitPlan(anchor?.id.orEmpty(), opening?.start.orEmpty(), opening?.end.orEmpty(),
        serviceMinutes.toIntOrNull() ?: -1, travelMinutes.toIntOrNull() ?: -1,
        crewCost.toDoubleOrNull() ?: -1.0, materials.toDoubleOrNull() ?: -1.0,
        vehicle.toDoubleOrNull() ?: -1.0, margin.toIntOrNull() ?: -1, radius.toIntOrNull() ?: -1)
    val request = ProfitOfferRequest(id, date, price.toDoubleOrNull() ?: -1.0, discount.toIntOrNull() ?: -1, plan)
    val result = remember(state, request, today) { runCatching { ProfitPlanner.evaluate(state, request, today) } }
    val opportunity = result.getOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Make an opening worth the trip", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Build around a booked visit. Compare the price, cost and time of adding nearby repeat customers.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RouteCard {
            SectionHeading("1. Find an opening")
            DateInput("Planned service date", date, { date = it; anchorId = ""; selectedWindow = "" },
                minDate = today.plusDays(1), maxDate = today.plusDays(365))
            if (anchors.isEmpty()) {
                RouteEmptyState(Icons.AutoMirrored.Filled.TrendingUp, "Start with a mapped booking",
                    "Choose a date with a scheduled visit and a customer map pin. That visit becomes the center of your nearby search.")
                AdaptivePair(first = { m -> OutlinedButton(onClick = onSchedule, modifier = m) { Text("Open schedule") } },
                    second = { m -> OutlinedButton(onClick = onMap, modifier = m) { Text("Open map") } })
            } else {
                Box {
                    OutlinedButton(onClick = { anchorMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${anchor?.time} · ${anchorCustomer?.name}")
                    }
                    DropdownMenu(expanded = anchorMenu, onDismissRequest = { anchorMenu = false }) {
                        anchors.forEach { visit ->
                            val customer = state.customers.first { it.id == visit.customerId }
                            DropdownMenuItem(text = { Text("${visit.time} · ${customer.name}") }, onClick = {
                                anchorId = visit.id; anchorMenu = false
                            })
                        }
                    }
                }
                Text("${anchor?.service} · ZIP ${anchorCustomer?.zip}", style = MaterialTheme.typography.bodyMedium)
                AdaptivePair(first = { m -> TimeInput("Day starts", dayStart, { dayStart = it; selectedWindow = "" }, m) },
                    second = { m -> TimeInput("Day ends", dayEnd, { dayEnd = it; selectedWindow = "" }, m) })
                if (gaps.isFailure) Text(gaps.exceptionOrNull()?.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                else if (openings.isEmpty()) Text("No free openings in this part of the day.")
                openings.forEach { gap ->
                    FilterChip(selected = gap == opening, onClick = { selectedWindow = "${gap.start}/${gap.end}" },
                        label = { Text("${gap.start}–${gap.end} · ${gap.minutes} min") }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (anchor != null) {
            RouteCard {
                SectionHeading("2. Enter the real cost of one added job")
                Text(if (previous == null) "Enter all three costs; use 0 where a cost does not apply."
                    else "Costs start from your last saved plan for this service. Check them for this visit.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PlanField("Whole crew cost per hour (USD)", crewCost, KeyboardType.Decimal) { crewCost = it }
                Text("Total loaded cost for everyone on the crew. Includes paid travel/setup time.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AdaptivePair(
                    first = { m -> PlanField("Materials per job (USD)", materials, KeyboardType.Decimal, m) { materials = it } },
                    second = { m -> PlanField("Vehicle cost per job (USD)", vehicle, KeyboardType.Decimal, m) { vehicle = it } })
                AdaptivePair(
                    first = { m -> PlanField("Service minutes", serviceMinutes, KeyboardType.Number, m) { serviceMinutes = it } },
                    second = { m -> PlanField("Travel/setup minutes", travelMinutes, KeyboardType.Number, m) { travelMinutes = it } })
                Text("Allow enough added travel/setup for each job, including reaching the next booked stop. Distance below is straight-line proximity; check actual driving time before sending an offer.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AdaptivePair(
                    first = { m -> PlanField("Target margin %", margin, KeyboardType.Number, m) { margin = it } },
                    second = { m -> PlanField("Nearby radius (miles)", radius, KeyboardType.Number, m) { radius = it } })
            }
            RouteCard {
                SectionHeading("3. Compare your offer")
                PlanField("Regular starting price (USD)", price, KeyboardType.Decimal) { price = it }
                PlanField("Proposed discount %", discount, KeyboardType.Number) { discount = it }
                if (opportunity == null) {
                    Text(result.exceptionOrNull()?.message ?: "Complete the costs and opening to compare your offer.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                } else {
                    val estimate = opportunity.estimate
                    StatusPill(if (estimate.meetsTarget) "Meets your margin target" else "Below your margin target", !estimate.meetsTarget)
                    Text("${planMoney(estimate.contributionPerJob)} per added job",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Estimated contribution after the entered costs, before overhead and tax.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PlanComparison("Full price: contribution per job", planMoney(estimate.fullPriceContribution))
                    PlanComparison("Your offer: customer price", planMoney(estimate.netPrice))
                    PlanComparison("Entered cost per job", planMoney(estimate.costPerJob))
                    PlanComparison("Contribution margin", String.format(Locale.US, "%.1f%%", estimate.marginPct))
                    HorizontalDivider()
                    PlanComparison("Minimum price for $margin% margin", planMoney(estimate.minimumPrice))
                    Text(estimate.maximumDiscountPct?.let { "Maximum whole-number discount that meets your target: $it% (within the app's 50% limit)." }
                        ?: "Even full price is below your target. Raise the price or revise the work and costs.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (opportunity != null) RouteCard {
                SectionHeading("4. Review nearby customers")
                Text("${opportunity.customers.size} eligible nearby · ${opportunity.capacity} ${if (opportunity.capacity == 1) "slot" else "slots"} fit",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Each booking reserves ${plan.blockMinutes} minutes: ${plan.serviceMinutes} service + ${plan.travelMinutes} travel/setup.",
                    style = MaterialTheme.typography.bodyMedium)
                if (opportunity.missingPins > 0) Text("${opportunity.missingPins} eligible ${if (opportunity.missingPins == 1) "customer has" else "customers have"} no map pin and cannot be included.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                opportunity.customers.take(12).forEach { (customer, miles) ->
                    PlanComparison(customer.name, String.format(Locale.US, "%.1f mi", miles))
                }
                if (opportunity.customers.size > 12) Text("And ${opportunity.customers.size - 12} more eligible nearby customers.")
                if (opportunity.capacity > 0) {
                    PlanComparison("If every slot books at your offer price", planMoney(opportunity.estimate.contributionPerJob * opportunity.capacity))
                    Text("A full-booking scenario, not a sales prediction. Openings are checked again when you book.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val example = Campaign(title = "Neighborhood visit", businessName = state.business.name.ifBlank { "Your business" },
                        zip = anchorCustomer!!.zip, service = anchor.service, visitDate = date,
                        expiryDate = date, price = request.price, discountPct = request.discountPct, capacity = opportunity.capacity)
                    SectionHeading("Message preview")
                    Text(CampaignRules.message(opportunity.customers.first().first, example), style = MaterialTheme.typography.bodyMedium)
                }
                if (state.business.name.isBlank()) Text("Save your business name in Business & finances before creating an offer.",
                    color = MaterialTheme.colorScheme.error)
                Button(enabled = opportunity.capacity > 0 && opportunity.estimate.meetsTarget && state.business.name.isNotBlank(),
                    onClick = { onCreate(request) }, modifier = Modifier.fillMaxWidth()) { Text("Create draft neighborhood offer") }
                Text("Only the nearby eligible customers above enter this offer. Review and approve each message before sending it through your SMS app.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PlanField(label: String, value: String, type: KeyboardType,
                      modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = modifier,
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type))
}

@Composable
private fun PlanComparison(label: String, value: String) {
    // Values get their own line on large text / compact screens instead of truncating names or currency.
    AdaptivePair(first = { m -> Text(label, modifier = m, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant) },
        second = { m -> Text(value, modifier = m, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) })
}

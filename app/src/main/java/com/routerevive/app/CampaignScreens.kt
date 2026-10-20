package com.routerevive.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

private fun dollars(value: Double) = "$" + String.format(Locale.US, "%,.2f", value)

@Composable
fun CampaignScreen(state: WorkspaceSnapshot, onNew: () -> Unit, onSelect: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val today = LocalDate.now()
    fun active(c: Campaign) = runCatching { !LocalDate.parse(c.expiryDate).isBefore(today) }.getOrDefault(false)
    val visible = state.campaigns.filter { when (filter) {
        "Active" -> active(it)
        "Expired" -> !active(it)
        else -> true
    } }.reversed()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Turn past service into your next neighborhood booking.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Create neighborhood offer")
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Active", "Expired").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                }
            }
        }
        if (visible.isEmpty()) item {
            RouteCard { RouteEmptyState(Icons.Default.Campaign,
                if (state.campaigns.isEmpty()) "Your next repeat booking starts here" else "No offers in this view",
                "Choose a ZIP and service. Review eligible customers, approve each message, then track the replies and bookings.") }
        }
        items(visible, key = { it.id }) { campaign ->
            Card(onClick = { onSelect(campaign.id) }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusPill(if (active(campaign)) "Active offer" else "Expired", !active(campaign))
                    Text(campaign.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${campaign.service} · ZIP ${campaign.zip}", style = MaterialTheme.typography.bodyMedium)
                    Text("Service ${campaign.visitDate}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val booked = CampaignRules.bookedSlots(campaign)
                    LinearProgressIndicator(progress = { (booked.toFloat() / campaign.capacity.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                    Text("$booked / ${campaign.capacity} booked · ${dollars(CampaignFinance.received(state, campaign))} collected",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    val drafts = campaign.recipients.count { it.status == "DRAFT" }
                    Text("${campaign.recipients.size} customers · $drafts awaiting approval",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun NewCampaignScreen(state: WorkspaceSnapshot, initialZip: String, initialService: String,
                      onSave: (Campaign) -> Unit) {
    val today = LocalDate.now()
    val id = rememberSaveable { UUID.randomUUID().toString() }
    var title by rememberSaveable { mutableStateOf("Neighborhood repeat-service offer") }
    var business by rememberSaveable { mutableStateOf(state.business.name) }
    var zip by rememberSaveable { mutableStateOf(initialZip) }
    var service by rememberSaveable { mutableStateOf(initialService) }
    var visit by rememberSaveable { mutableStateOf(today.plusDays(7).toString()) }
    var expiry by rememberSaveable { mutableStateOf(today.plusDays(5).toString()) }
    var price by rememberSaveable { mutableStateOf("150") }
    var discount by rememberSaveable { mutableStateOf("0") }
    var capacity by rememberSaveable { mutableStateOf("4") }
    var previewFilter by rememberSaveable { mutableStateOf("Eligible") }
    val proposal = Campaign(id = id, title = title.trim(), businessName = business.trim(), zip = zip.trim(),
        service = service.trim(), visitDate = visit, expiryDate = expiry, price = price.toDoubleOrNull() ?: -1.0,
        discountPct = discount.toIntOrNull() ?: -1, capacity = capacity.toIntOrNull() ?: 0)
    val index = remember(state, today) { EligibilityIndex(state, today) }
    val target = state.customers.filter { it.zip == zip.trim() && it.service.trim().equals(service.trim(), true) }
    val evaluations = target.map { it to index.reasons(it, zip.trim(), service.trim()) }
    val eligible = evaluations.filter { it.second.isEmpty() }.map { it.first }
    val errors = CampaignWorkflow.offerErrors(proposal, today)
    val preview = evaluations.filter { if (previewFilter == "Eligible") it.second.isEmpty() else it.second.isNotEmpty() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Build a neighborhood offer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("You choose the offer. Every customer message needs your review before you send it.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RouteCard {
            SectionHeading("1. Choose your neighborhood")
            CampaignField("Offer title", title) { title = it }
            CampaignField("Business name in messages", business) { business = it }
            CampaignField("Neighborhood ZIP", zip, KeyboardType.Number) { zip = it }
            CampaignField("Service offered", service) { service = it }
        }
        RouteCard {
            SectionHeading("2. Set the offer")
            DateInput("Service date", visit, { visit = it }, minDate = today.plusDays(1), maxDate = today.plusDays(365))
            DateInput("Offer expires", expiry, { expiry = it }, minDate = today)
            CampaignField("Starting price (USD)", price, KeyboardType.Decimal) { price = it }
            AdaptivePair(
                first = { modifier -> OutlinedTextField(discount, { discount = it }, label = { Text("Discount %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = modifier) },
                second = { modifier -> OutlinedTextField(capacity, { capacity = it }, label = { Text("Booking slots") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = modifier) })
            errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        RouteCard {
            SectionHeading("3. Review your audience")
            Text("${eligible.size} eligible · ${target.size - eligible.size} need attention",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("180 days since service · 60 days between promotional contacts",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Eligible", "Needs attention").forEach { option ->
                    FilterChip(selected = previewFilter == option, onClick = { previewFilter = option }, label = { Text(option) })
                }
            }
            if (target.isEmpty()) Text("No customer records match this ZIP and service.", style = MaterialTheme.typography.bodyMedium)
            else if (preview.isEmpty()) Text(if (previewFilter == "Eligible")
                "No eligible customers yet. Review the attention list to see why." else "Every matching customer is eligible.")
            preview.take(20).forEach { (customer, reasons) ->
                Text(customer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (reasons.isEmpty()) "Ready for your message review" else reasons.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reasons.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            }
            if (preview.size > 20) Text("${preview.size - 20} more matching records; all eligible customers are included.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (eligible.isNotEmpty() && errors.isEmpty()) RouteCard {
            SectionHeading("Message preview")
            Text(CampaignRules.message(eligible.first(), proposal), style = MaterialTheme.typography.bodyMedium)
            Text("A separate draft is created for each eligible customer. Creating an offer sends nothing.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(enabled = errors.isEmpty() && eligible.isNotEmpty(), onClick = { onSave(proposal) },
            modifier = Modifier.fillMaxWidth()) { Text("Create offer for ${eligible.size} ${if (eligible.size == 1) "customer" else "customers"}") }
    }
}

@Composable
fun CampaignDetailScreen(
    state: WorkspaceSnapshot, campaign: Campaign,
    onEdit: (Recipient) -> Unit, onSend: (Recipient) -> Unit, onMarkSent: (Recipient) -> Boolean,
    onStatus: (Recipient, String) -> Boolean, onOpenJob: (Recipient) -> Unit,
    onMoney: (Recipient, String) -> Unit
) {
    var filter by rememberSaveable(campaign.id) { mutableStateOf("All") }
    var confirmation by remember { mutableStateOf<Pair<Recipient, String>?>(null) }
    val today = LocalDate.now()
    val expired = runCatching { LocalDate.parse(campaign.expiryDate).isBefore(today) }.getOrDefault(true)
    val index = remember(state, campaign.id, today) { EligibilityIndex(state, today, campaign.id) }
    val displayed = campaign.recipients.filter { when (filter) {
        "Review" -> it.status in setOf("DRAFT", "APPROVED")
        "Replies" -> it.status in setOf("SENT", "INTERESTED")
        "Booked" -> it.status in setOf("BOOKED", "COMPLETED", "PAID")
        else -> true
    } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            RouteCard {
                StatusPill(if (expired) "Offer expired" else "Active offer", expired)
                Text("${campaign.service} · ZIP ${campaign.zip}", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text("Service ${campaign.visitDate} · Expires ${campaign.expiryDate}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${CampaignRules.bookedSlots(campaign)} / ${campaign.capacity} booked",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${dollars(CampaignFinance.received(state, campaign))} collected",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (CampaignFinance.legacyUnlinked(state, campaign) > 0) Text(
                    "Includes earlier campaign payment records. Review their receipts in Jobs before recording more money.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Review", "Replies", "Booked").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                }
            }
        }
        if (displayed.isEmpty()) item { RouteCard { RouteEmptyState(Icons.Default.Inbox,
            "No customers in this stage", "Choose another view to see the rest of this offer.") } }
        items(displayed, key = { it.customerId }) { recipient ->
            val customer = state.customers.firstOrNull { it.id == recipient.customerId }
            var showMessage by rememberSaveable(campaign.id, recipient.customerId) { mutableStateOf(false) }
            val reasons = if (customer == null) listOf("Customer record is missing.") else
                index.reasons(customer, campaign.zip, campaign.service) + if (expired) listOf("This offer has expired.") else emptyList()
            RouteCard {
                Text(customer?.name ?: "Customer unavailable", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                StatusPill(recipientLabel(recipient.status), recipient.status !in setOf("APPROVED", "BOOKED", "COMPLETED", "PAID"))
                if (recipient.status in setOf("DRAFT", "APPROVED") || showMessage)
                    Text(recipient.message, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                else TextButton(onClick = { showMessage = true }) { Text("View message") }
                if (recipient.status in setOf("DRAFT", "APPROVED") && reasons.isNotEmpty())
                    Text(reasons.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary)
                when (recipient.status) {
                    "DRAFT" -> Button(enabled = reasons.isEmpty(), onClick = { onEdit(recipient) }) { Text("Review message") }
                    "APPROVED" -> {
                        Button(enabled = reasons.isEmpty(), onClick = { onSend(recipient) }) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open SMS draft")
                        }
                        OutlinedButton(enabled = reasons.isEmpty(), onClick = { confirmation = recipient to "SENT" }) { Text("I sent this message") }
                        TextButton(onClick = { onEdit(recipient) }, enabled = reasons.isEmpty()) { Text("Revise message") }
                    }
                    "SENT" -> {
                        Text("Marked sent ${recipient.sentAt}", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onStatus(recipient, "INTERESTED") }) { Text("Customer interested") }
                        TextButton(onClick = { confirmation = recipient to "DECLINED" }) { Text("Declined") }
                    }
                    "INTERESTED" -> {
                        Button(enabled = CampaignRules.bookedSlots(campaign) < campaign.capacity,
                            onClick = { onMoney(recipient, "book") }) { Text("Confirm appointment") }
                        if (CampaignRules.bookedSlots(campaign) >= campaign.capacity) Text("All booking slots are filled.")
                        TextButton(onClick = { confirmation = recipient to "DECLINED" }) { Text("Declined") }
                    }
                    "BOOKED" -> {
                        val appointment = CampaignWorkflow.linkedAppointment(state, campaign.id, recipient.customerId)
                        Text("Agreed ${dollars(recipient.bookedAmount)}", style = MaterialTheme.typography.titleSmall)
                        if (appointment != null) Text("${appointment.date} · ${appointment.time}", style = MaterialTheme.typography.bodyMedium)
                        val canComplete = appointment != null && runCatching { !LocalDate.parse(appointment.date).isAfter(today) }.getOrDefault(false)
                        Button(enabled = canComplete, onClick = { confirmation = recipient to "COMPLETED" }) { Text("Complete service") }
                        if (!canComplete) Text("Complete on the service date, or move the appointment to its actual date in Schedule.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { confirmation = recipient to "CANCELLED" }) { Text("Cancel booking") }
                    }
                    "COMPLETED" -> {
                        Text("Received ${dollars(recipient.paidAmount)} · Agreed ${dollars(recipient.bookedAmount)}",
                            style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { onMoney(recipient, "paid") }) { Text("Record payment") }
                    }
                    "PAID" -> Text("Received ${dollars(recipient.paidAmount)}", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium)
                }
                if (recipient.status in setOf("BOOKED", "COMPLETED", "PAID"))
                    OutlinedButton(onClick = { onOpenJob(recipient) }) { Text("Open job & invoice") }
                if (recipient.status in setOf("DRAFT", "APPROVED", "SENT", "INTERESTED", "DECLINED") && customer?.optedOut == false)
                    TextButton(onClick = { confirmation = recipient to "OPTED_OUT" }) { Text("Record customer opt-out", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    confirmation?.let { (recipient, action) ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(when (action) {
            "SENT" -> "Did you send this message?"
            "COMPLETED" -> "Record completed service?"
            "CANCELLED" -> "Cancel this booking?"
            "OPTED_OUT" -> "Record promotional opt-out?"
            else -> "Record this reply?"
        }) }, text = { Text(when (action) {
            "SENT" -> "Only confirm after you sent the message yourself. This records today's contact date; it sends nothing."
            "COMPLETED" -> "This completes the linked appointment and job, and records today's service date for the customer."
            "CANCELLED" -> "This cancels the linked appointment and frees its time slot. Existing payment receipts are retained."
            "OPTED_OUT" -> "Further promotional drafts for this customer will be blocked across your campaigns."
            else -> "The customer will be marked as having declined this offer."
        }) }, confirmButton = { TextButton(onClick = {
            val saved = if (action == "SENT") onMarkSent(recipient) else onStatus(recipient, action)
            if (saved) confirmation = null
        }) { Text(if (action == "SENT") "Record sent" else "Confirm") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
    }
}

private fun recipientLabel(status: String) = when (status) {
    "DRAFT" -> "Needs approval"
    "APPROVED" -> "Approved"
    "SENT" -> "Awaiting reply"
    "INTERESTED" -> "Interested"
    "BOOKED" -> "Booked"
    "COMPLETED" -> "Completed"
    "PAID" -> "Paid"
    "OPTED_OUT" -> "Opted out"
    "DECLINED" -> "Declined"
    "CANCELLED" -> "Cancelled"
    else -> status
}

@Composable
private fun CampaignField(label: String, value: String, type: KeyboardType = KeyboardType.Text,
                          onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth())
}

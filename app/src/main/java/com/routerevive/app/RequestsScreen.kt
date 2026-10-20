package com.routerevive.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestsScreen(
    requests: List<BookingRequest>,
    appointments: List<Appointment>,
    errorMessage: String,
    onAdd: (BookingRequest) -> Boolean,
    onStatus: (BookingRequest, String) -> Boolean,
    onBook: (BookingRequest) -> Boolean
) {
    var showNew by remember { mutableStateOf(false) }
    var booking by remember { mutableStateOf<BookingRequest?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var zip by rememberSaveable { mutableStateOf("") }
    var service by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by rememberSaveable { mutableStateOf("09:00") }
    var duration by rememberSaveable { mutableStateOf("60") }
    var quote by rememberSaveable { mutableStateOf("0") }
    var notes by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("Phone") }
    var requestFilter by rememberSaveable { mutableStateOf("Open") }
    val candidate = BookingRequest(name = name.trim(), phone = phone.trim(), zip = zip.trim(),
        service = service.trim(), requestedDate = date, requestedTime = time,
        durationMinutes = duration.toIntOrNull() ?: 0, quotedPrice = quote.toDoubleOrNull() ?: -1.0,
        notes = notes.trim(), source = source.trim())
    val open = requests.count { it.status == "NEW" || it.status == "CONTACTED" }
    val displayed = requests.filter {
        if (requestFilter == "Open") it.status in setOf("NEW", "CONTACTED") else true
    }.sortedWith(compareBy<BookingRequest> { it.status !in setOf("NEW", "CONTACTED") }
        .thenBy { it.requestedDate }.thenBy { it.requestedTime })

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("$open open ${if (open == 1) "inquiry" else "inquiries"} · recorded from your calls, texts and emails",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                name = ""; phone = ""; zip = ""; service = ""; notes = "";
                date = LocalDate.now().plusDays(1).toString(); time = "09:00";
                quote = "0"; duration = "60"; source = "Phone"; showNew = true
            }) { Text("Add request") }
            FilterChip(selected = requestFilter == "Open",
                onClick = { requestFilter = "Open" }, label = { Text("Open") })
            FilterChip(selected = requestFilter == "All",
                onClick = { requestFilter = "All" }, label = { Text("All") })
        }
        if (displayed.isEmpty()) RouteCard {
            RouteEmptyState(Icons.Default.Inbox, if (requests.isEmpty()) "Your inquiries will land here" else "All caught up",
                "Record an inquiry, agree on the details, then confirm it into your schedule.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 20.dp)) {
            items(displayed, key = { it.id }) { request ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusPill(request.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                            request.status in setOf("NEW", "CONTACTED"))
                        Text(request.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(request.phone + " · " + request.zip + " · " + request.source,
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        Text(request.service, fontSize = 13.sp)
                        Text("Requested " + request.requestedDate + " at " + request.requestedTime +
                            " · " + request.status.replace('_', ' '), fontSize = 12.sp)
                        if (request.notes.isNotBlank())
                            Text(request.notes, fontSize = 12.sp, maxLines = 3)
                        if (request.status == "NEW" || request.status == "CONTACTED") {
                            if (request.status == "NEW") TextButton(
                                onClick = { onStatus(request, "CONTACTED") }) { Text("Mark contacted") }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Button(onClick = { booking = request }) { Text("Confirm booking") }
                                TextButton(onClick = { onStatus(request, "DECLINED") }) {
                                    Text("Decline")
                                }
                            }
                        }
                        if (request.status == "BOOKED")
                            Text("Confirmed appointment " + request.bookedAppointmentId.take(8),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showNew) AlertDialog(onDismissRequest = { showNew = false },
        title = { Text("Record inbound request") },
        text = {
            Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter information from an actual customer inquiry. This does not send anything.",
                    color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                RequestField("Customer name", name) { name = it }
                RequestField("Phone", phone, KeyboardType.Phone) { phone = it }
                RequestField("ZIP", zip, KeyboardType.Number) { zip = it }
                RequestField("Service requested", service) { service = it }
                DateInput("Requested date", date, { date = it })
                TimeInput("Requested time", time, { time = it })
                RequestField("Duration minutes", duration, KeyboardType.Number) { duration = it }
                RequestField("Quoted price ($)", quote, KeyboardType.Decimal) { quote = it }
                RequestField("Received via (Phone, Text, Email, In person)", source) { source = it }
                OutlinedTextField(notes, { if (it.length <= 3000) notes = it },
                    label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
                if (!BookingRules.valid(candidate) || !WorkspaceRules.validMoney(candidate.quotedPrice))
                    Text("Complete the required name, valid phone/ZIP, service, date/time, duration and price.",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(enabled = BookingRules.valid(candidate) && WorkspaceRules.validMoney(candidate.quotedPrice), onClick = {
            if (onAdd(candidate)) showNew = false
        }) { Text("Save request") } },
        dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } })

    booking?.let { request ->
        var whenDate by remember(request.id) { mutableStateOf(request.requestedDate) }
        var whenTime by remember(request.id) { mutableStateOf(request.requestedTime) }
        var whenDuration by remember(request.id) { mutableStateOf(request.durationMinutes.toString()) }
        var whenPrice by remember(request.id) { mutableStateOf(request.quotedPrice.toString()) }
        val updated = request.copy(requestedDate = whenDate, requestedTime = whenTime,
            durationMinutes = whenDuration.toIntOrNull() ?: 0,
            quotedPrice = whenPrice.toDoubleOrNull() ?: -1.0)
        val valid = BookingRules.bookable(updated, appointments) && WorkspaceRules.validMoney(updated.quotedPrice)
        AlertDialog(onDismissRequest = { booking = null }, title = { Text("Confirm appointment") },
            text = {
                Column(Modifier.heightIn(max = 365.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Confirm with the customer before converting this request. A new customer record will have marketing consent disabled.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    DateInput("Confirmed date", whenDate, { whenDate = it }, minDate = LocalDate.now())
                    TimeInput("Confirmed time", whenTime, { whenTime = it })
                    RequestField("Duration (minutes)", whenDuration, KeyboardType.Number) {
                        whenDuration = it
                    }
                    RequestField("Confirmed quoted price", whenPrice, KeyboardType.Decimal) {
                        whenPrice = it
                    }
                    if (errorMessage.isNotBlank()) Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    if (!valid) Text("Choose a future start time, valid duration, and an available slot with no conflicting appointments. Jobs cannot cross midnight.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = {
                if (onBook(updated)) booking = null
            }) { Text("Confirm and schedule") } },
            dismissButton = { TextButton(onClick = { booking = null }) { Text("Cancel") } })
    }
}

@Composable
private fun RequestField(label: String, value: String,
                         type: KeyboardType = KeyboardType.Text,
                         update: (String) -> Unit) {
    OutlinedTextField(value, update, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth())
}

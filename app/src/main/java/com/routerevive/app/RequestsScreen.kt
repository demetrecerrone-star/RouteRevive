package com.routerevive.app

import androidx.compose.foundation.layout.*
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

@Composable
fun RequestsScreen(
    requests: List<BookingRequest>,
    appointments: List<Appointment>,
    onAdd: (BookingRequest) -> Unit,
    onStatus: (BookingRequest, String) -> Unit,
    onBook: (BookingRequest) -> Unit
) {
    var showNew by remember { mutableStateOf(false) }
    var booking by remember { mutableStateOf<BookingRequest?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by remember { mutableStateOf("09:00") }
    var duration by remember { mutableStateOf("60") }
    var quote by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("Phone") }
    var requestFilter by remember { mutableStateOf("Open") }
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
        Text("BOOKING REQUESTS", color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("$open open request(s) · entered by your business, not submitted online",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        if (displayed.isEmpty()) Text(
            if (requests.isEmpty()) "No requests yet. Add requests received by phone, email, text, or in person."
            else "No open requests. Switch to All to see earlier requests.",
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(vertical = 20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 20.dp)) {
            items(displayed, key = { it.id }) { request ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(request.name, fontWeight = FontWeight.Bold)
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
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
                RequestField("Date YYYY-MM-DD", date) { date = it }
                RequestField("Time HH:mm", time) { time = it }
                RequestField("Duration minutes", duration, KeyboardType.Number) { duration = it }
                RequestField("Quoted price ($)", quote, KeyboardType.Decimal) { quote = it }
                RequestField("Received via (Phone, Text, Email, In person)", source) { source = it }
                OutlinedTextField(notes, { if (it.length <= 3000) notes = it },
                    label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (!BookingRules.valid(candidate))
                    Text("Complete the required name, valid phone/ZIP, service, date/time, duration and price.",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(enabled = BookingRules.valid(candidate), onClick = {
            onAdd(candidate)
            showNew = false
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
        val valid = BookingRules.bookable(updated, appointments)
        AlertDialog(onDismissRequest = { booking = null }, title = { Text("Confirm appointment") },
            text = {
                Column(Modifier.heightIn(max = 365.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Confirm with the customer before converting this request. A new customer record will have marketing consent disabled.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    RequestField("Confirmed date YYYY-MM-DD", whenDate) { whenDate = it }
                    RequestField("Confirmed time HH:mm", whenTime) { whenTime = it }
                    RequestField("Duration (minutes)", whenDuration, KeyboardType.Number) {
                        whenDuration = it
                    }
                    RequestField("Confirmed quoted price", whenPrice, KeyboardType.Decimal) {
                        whenPrice = it
                    }
                    if (!valid) Text("Choose a future start time, valid duration, and an available slot with no conflicting appointments. Jobs cannot cross midnight.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = {
                onBook(updated)
                booking = null
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

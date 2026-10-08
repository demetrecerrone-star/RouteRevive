package com.routerevive.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

object ScheduleRules {
    fun validDate(date: String): Boolean =
        runCatching { LocalDate.parse(date) }.isSuccess
    fun validTime(time: String): Boolean =
        runCatching { LocalTime.parse(time) }.isSuccess
    fun overlaps(candidate: Appointment, all: List<Appointment>): Boolean {
        val start = runCatching { java.time.LocalDateTime.of(
            LocalDate.parse(candidate.date), LocalTime.parse(candidate.time)) }.getOrNull() ?: return true
        if (candidate.durationMinutes !in 15..480) return true
        val end = start.plusMinutes(candidate.durationMinutes.toLong())
        // Service sessions crossing midnight need a deliberate multi-day scheduler.
        if (end.toLocalDate() != start.toLocalDate()) return true
        return all.any { other ->
            if (other.id == candidate.id || other.status == "CANCELLED") false
            else {
                val beginning = runCatching { java.time.LocalDateTime.of(
                    LocalDate.parse(other.date), LocalTime.parse(other.time)) }.getOrNull()
                if (beginning == null || other.durationMinutes <= 0) true
                else start.isBefore(beginning.plusMinutes(other.durationMinutes.toLong())) &&
                    beginning.isBefore(end)
            }
        }
    }
}

@Composable
fun CustomerEditor(
    customer: Customer,
    onSave: (Customer) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(customer.id) { mutableStateOf(customer.name) }
    var phone by remember(customer.id) { mutableStateOf(customer.phone) }
    var zip by remember(customer.id) { mutableStateOf(customer.zip) }
    var service by remember(customer.id) { mutableStateOf(customer.service) }
    var address by remember(customer.id) { mutableStateOf(customer.address) }
    var latitude by remember(customer.id) { mutableStateOf(customer.latitude?.toString() ?: "") }
    var longitude by remember(customer.id) { mutableStateOf(customer.longitude?.toString() ?: "") }
    var notes by remember(customer.id) { mutableStateOf(customer.notes) }
    var date by remember(customer.id) { mutableStateOf(customer.lastService) }
    var price by remember(customer.id) { mutableStateOf(customer.lastPrice.toString()) }
    var consent by remember(customer.id) { mutableStateOf(customer.consent) }
    var evidence by remember(customer.id) { mutableStateOf(customer.consentEvidence) }
    var consentDate by remember(customer.id) { mutableStateOf(customer.consentDate) }
    var markOptOut by remember(customer.id) { mutableStateOf(customer.optedOut) }
    var issue by remember(customer.id) { mutableStateOf(customer.issueOpen) }
    var futureBooked by remember(customer.id) { mutableStateOf(customer.futureBooked) }
    val valid = name.isNotBlank() && zip.matches(Regex("\\d{5}")) &&
        CampaignRules.digits(phone).length in 10..15 && service.isNotBlank() &&
        ScheduleRules.validDate(date) && (price.toDoubleOrNull() ?: -1.0) >= 0 &&
        (!consent || (evidence.isNotBlank() && ScheduleRules.validDate(consentDate))) &&
        address.length < 500 && notes.length < 4000 &&
        ((latitude.isBlank() && longitude.isBlank()) ||
         (latitude.toDoubleOrNull()?.let { it.isFinite() && it in -90.0..90.0 } == true &&
          longitude.toDoubleOrNull()?.let { it.isFinite() && it in -180.0..180.0 } == true))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExtraField("Name", name) { name = it }
        ExtraField("Phone", phone, KeyboardType.Phone) { phone = it }
        ExtraField("ZIP", zip, KeyboardType.Number) { zip = it }
        ExtraField("Service", service) { service = it }
        ExtraField("Street address (optional)", address) { address = it }
        Text("Map location (optional). Use Neighborhoods → Locate to search an address, or enter coordinates manually.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        ExtraField("Latitude (optional, -90 to 90)", latitude, KeyboardType.Decimal) { latitude = it }
        ExtraField("Longitude (optional, -180 to 180)", longitude, KeyboardType.Decimal) { longitude = it }
        ExtraField("Last completed service (YYYY-MM-DD)", date) { date = it }
        ExtraField("Previous service price ($)", price, KeyboardType.Decimal) { price = it }
        OutlinedTextField(value = notes, onValueChange = { if (it.length <= 4000) notes = it },
            label = { Text("Customer notes") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        HorizontalDivider()
        Text("Marketing preferences", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it })
            Text("Documented permission for promotional SMS", fontSize = 13.sp)
        }
        if (consent) {
            ExtraField("Consent evidence / source", evidence) { evidence = it }
            ExtraField("Consent date (YYYY-MM-DD)", consentDate) { consentDate = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = markOptOut, onCheckedChange = { if (it) markOptOut = true },
                enabled = !markOptOut)
            Text(if (markOptOut) "Opted out — contact blocked" else "Mark opted out", fontSize = 13.sp)
        }
        Text("For safety, an existing opt-out cannot be removed through this editor.",
            color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = issue, onCheckedChange = { issue = it })
            Text("Unresolved issue")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = futureBooked, onCheckedChange = { futureBooked = it })
            Text("Upcoming booking outside this calendar")
        }
        Button(onClick = {
            val oldPinUnchanged = latitude == (customer.latitude?.toString() ?: "") &&
                longitude == (customer.longitude?.toString() ?: "")
            val clearPin = address.trim() != customer.address && oldPinUnchanged
            onSave(customer.copy(name = name.trim(), phone = phone.trim(), zip = zip.trim(),
                service = service.trim(), address = address.trim(), notes = notes.trim(),
                lastService = date, lastPrice = price.toDouble(),
                consent = consent, consentEvidence = if (consent) evidence.trim() else "",
                consentDate = if (consent) consentDate.trim() else "",
                latitude = if (clearPin) null else latitude.toDoubleOrNull(),
                longitude = if (clearPin) null else longitude.toDoubleOrNull(),
                optedOut = markOptOut, issueOpen = issue, futureBooked = futureBooked))
        }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("Save changes") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
fun ScheduleScreen(
    customers: List<Customer>,
    appointments: List<Appointment>,
    onNew: (Appointment) -> Unit,
    onStatus: (Appointment, String) -> Unit,
    onReschedule: (Appointment) -> Unit,
    onRemind: (Appointment) -> Unit,
    onMarkReminded: (Appointment) -> Unit
) {
    var showNew by remember { mutableStateOf(false) }
    var rescheduling by remember { mutableStateOf<Appointment?>(null) }
    var newDate by remember { mutableStateOf("") }
    var newTime by remember { mutableStateOf("") }
    var newDuration by remember { mutableStateOf("") }
    var customerId by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by remember { mutableStateOf("09:00") }
    var duration by remember { mutableStateOf("60") }
    var price by remember { mutableStateOf("150") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val selected = customers.firstOrNull { it.id == customerId }
    val candidate = Appointment(customerId = customerId, service = service, date = date, time = time,
        durationMinutes = duration.toIntOrNull() ?: 60, price = price.toDoubleOrNull() ?: 0.0,
        notes = notes)
    val valid = selected != null && service.isNotBlank() &&
        ScheduleRules.validDate(date) && ScheduleRules.validTime(time) &&
        (duration.toIntOrNull() ?: 0) in 15..480 && (price.toDoubleOrNull() ?: -1.0) >= 0.0 &&
        !ScheduleRules.overlaps(candidate, appointments)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Appointments", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { showNew = true }) { Icon(Icons.Default.Add, null); Text(" New") }
        }
        if (appointments.isEmpty()) {
            Text("No appointments. Add one to start planning your service schedule.",
                color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 24.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)) {
            items(appointments.sortedWith(compareBy<Appointment> { it.date }.thenBy { it.time }),
                key = { it.id }) { a ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val c = customers.firstOrNull { it.id == a.customerId }
                        Text("${a.date}  ·  ${a.time}", fontWeight = FontWeight.Bold)
                        Text(c?.name ?: "Customer unavailable", color = MaterialTheme.colorScheme.primary)
                        Text(a.service, fontSize = 13.sp)
                        Text("${a.durationMinutes} minutes · $${"%.2f".format(a.price)} · ${a.status}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        if (a.notes.isNotBlank()) Text(a.notes, fontSize = 12.sp)
                        val reminderCustomer = customers.firstOrNull { it.id == a.customerId }
                        if (a.status == "SCHEDULED" && reminderCustomer != null) {
                            val canRemind = ReminderRules.canDraft(a, reminderCustomer)
                            if (canRemind) {
                                OutlinedButton(onClick = { onRemind(a) }) {
                                    Text("Open SMS reminder draft")
                                }
                                if (!ReminderRules.sentToday(a)) {
                                    TextButton(onClick = { onMarkReminded(a) }) {
                                        Text("I sent the reminder")
                                    }
                                } else {
                                    Text("Reminder manually recorded today",
                                        color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                }
                            } else if (reminderCustomer.optedOut) {
                                Text("SMS reminders blocked: customer opted out",
                                    color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                        if (a.status in setOf("SCHEDULED", "IN_PROGRESS")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (a.status == "SCHEDULED") {
                                    TextButton(onClick = { onStatus(a, "IN_PROGRESS") }) { Text("Start") }
                                }
                                TextButton(onClick = { onStatus(a, "COMPLETED") }) { Text("Complete") }
                                TextButton(onClick = { onStatus(a, "CANCELLED") }) { Text("Cancel") }
                            }
                            if (a.status == "SCHEDULED") {
                                TextButton(onClick = {
                                    rescheduling = a
                                    newDate = a.date
                                    newTime = a.time
                                    newDuration = a.durationMinutes.toString()
                                }) { Text("Reschedule") }
                            }
                        }
                    }
                }
            }
        }
    }
    val editing = rescheduling
    if (editing != null) {
        val revised = editing.copy(date = newDate, time = newTime,
            durationMinutes = newDuration.toIntOrNull() ?: 0)
        val validMove = ScheduleRules.validDate(newDate) &&
            ScheduleRules.validTime(newTime) &&
            (newDuration.toIntOrNull() ?: 0) in 15..480 &&
            !ScheduleRules.overlaps(revised, appointments)
        AlertDialog(onDismissRequest = { rescheduling = null },
            title = { Text("Reschedule appointment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Changing this booking does not automatically change the date of its original marketing campaign.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    ExtraField("New date YYYY-MM-DD", newDate) { newDate = it }
                    ExtraField("New time HH:mm", newTime) { newTime = it }
                    ExtraField("Duration (minutes)", newDuration, KeyboardType.Number) { newDuration = it }
                    if (!validMove) Text("Enter a valid date, time and duration with no other overlapping appointment.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(enabled = validMove, onClick = {
                onReschedule(revised)
                rescheduling = null
            }) { Text("Save booking") } },
            dismissButton = { TextButton(onClick = { rescheduling = null }) { Text("Cancel") } })
    }
    if (showNew) {
        AlertDialog(onDismissRequest = { showNew = false }, title = { Text("New appointment") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose a customer", fontSize = 12.sp)
                    customers.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = c.id == customerId, onClick = {
                                customerId = c.id; service = c.service
                            })
                            Text(c.name, fontSize = 13.sp)
                        }
                    }
                    ExtraField("Service", service) { service = it }
                    ExtraField("Date YYYY-MM-DD", date) { date = it }
                    ExtraField("Time HH:mm (24-hour)", time) { time = it }
                    ExtraField("Duration (minutes)", duration, KeyboardType.Number) { duration = it }
                    ExtraField("Price ($)", price, KeyboardType.Decimal) { price = it }
                    ExtraField("Notes", notes) { notes = it }
                    if (selected != null && ScheduleRules.validTime(time) && ScheduleRules.validDate(date) &&
                        ScheduleRules.overlaps(candidate, appointments)) {
                        Text("This slot overlaps an existing appointment.",
                            color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    if (!ScheduleRules.overlaps(candidate, appointments)) {
                        onNew(candidate); showNew = false; error = ""
                    } else error = "Slot already taken"
                }) { Text("Schedule") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } })
    }
}

@Composable
private fun ExtraField(label: String, value: String, type: KeyboardType = KeyboardType.Text,
                       onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth())
}

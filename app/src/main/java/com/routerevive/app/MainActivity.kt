package com.routerevive.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale

private val Ink = Color(0xFF0B121C)
private val Panel = Color(0xFF172335)
private val Highlight = Color(0xFF4ADE80)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 18, 28)
        window.navigationBarColor = android.graphics.Color.rgb(11, 18, 28)
        val store = LocalStore(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Highlight, onPrimary = Ink, background = Ink,
                    surface = Panel, onSurface = Color.White,
                    secondary = Color(0xFF9CB0C9), onBackground = Color.White
                )
            ) {
                RouteApp(store, openSms = { phone, message ->
                    try {
                        startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null)).apply {
                            putExtra("sms_body", message)
                        })
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this, "No SMS app installed", Toast.LENGTH_LONG).show()
                    }
                })
            }
        }
    }
}

@Composable
private fun RouteApp(store: LocalStore, openSms: (String, String) -> Unit) {
    val customers = remember { mutableStateListOf<Customer>().apply { addAll(store.loadCustomers()) } }
    val campaigns = remember { mutableStateListOf<Campaign>().apply { addAll(store.loadCampaigns()) } }
    val appointments = remember { mutableStateListOf<Appointment>().apply { addAll(store.loadAppointments()) } }
    val context = LocalContext.current
    var page by remember { mutableStateOf("home") }
    var selectedId by remember { mutableStateOf("") }
    var editingCustomerId by remember { mutableStateOf("") }
    var bookingTime by remember { mutableStateOf("09:00") }
    var bookingDuration by remember { mutableStateOf("60") }
    var importCandidate by remember { mutableStateOf<String?>(null) }
    var editorCustomer by remember { mutableStateOf<String?>(null) }
    var editorRecipient by remember { mutableStateOf<String?>(null) }
    var messageDraft by remember { mutableStateOf("") }
    var moneyDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var moneyInput by remember { mutableStateOf("") }
    var appError by remember { mutableStateOf("") }
    var lastBackupMessage by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(store.exportJson().toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open destination")
            }.onSuccess { lastBackupMessage = "Backup saved. Treat the JSON as confidential." }
             .onFailure { appError = "Backup export failed: ${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Cannot open backup")
            }.onSuccess { importCandidate = it }
             .onFailure { appError = "Backup could not be read: ${it.message}" }
        }
    }

    fun save() {
        try { store.save(customers.toList(), campaigns.toList(), appointments.toList()); appError = "" }
        catch (e: Exception) { appError = "Unable to save changes: ${e.message}" }
    }
    fun updateCustomer(c: Customer) {
        val index = customers.indexOfFirst { it.id == c.id }
        if (index >= 0) {
            val previous = customers[index]
            customers[index] = c
            if (previous.phone != c.phone || previous.name != c.name ||
                previous.consent != c.consent || previous.consentEvidence != c.consentEvidence ||
                previous.optedOut != c.optedOut) {
                campaigns.indices.forEach { campaignIndex ->
                    val campaign = campaigns[campaignIndex]
                    campaigns[campaignIndex] = campaign.copy(recipients = campaign.recipients.map { r ->
                        if (r.customerId == c.id && r.status == "APPROVED") {
                            r.copy(status = "DRAFT", approvedAt = "", message = CampaignRules.message(c, campaign))
                        } else r
                    })
                }
            }
            save()
        }
    }
    fun updateCampaign(c: Campaign) {
        val index = campaigns.indexOfFirst { it.id == c.id }
        if (index >= 0) { campaigns[index] = c; save() }
    }
    fun updateRecipient(c: Campaign, r: Recipient) {
        updateCampaign(c.copy(recipients = c.recipients.map { if (it.customerId == r.customerId) r else it }))
    }
    fun hasFutureAppointment(customerId: String): Boolean =
        appointments.any { a -> a.customerId == customerId && a.status == "SCHEDULED" &&
            runCatching { !LocalDate.parse(a.date).isBefore(LocalDate.now()) }.getOrDefault(false) }

    fun allowed(customer: Customer, campaign: Campaign): Boolean =
        CampaignRules.reasons(
            customer.copy(futureBooked = customer.futureBooked || hasFutureAppointment(customer.id)),
            campaign.zip, campaign.service, LocalDate.now(),
            customers.toList(), campaigns.filterNot { it.id == campaign.id }
        ).isEmpty() && !LocalDate.parse(campaign.expiryDate).isBefore(LocalDate.now())

    val current = campaigns.firstOrNull { it.id == selectedId }
    BackHandler(page !in setOf("home", "customers", "campaigns", "schedule")) {
        page = when (page) {
            "campaign_detail", "new_campaign" -> "campaigns"
            else -> "customers"
        }
    }

    Scaffold(containerColor = Ink, bottomBar = {
        NavigationBar(containerColor = Panel) {
            listOf(Triple("home", "Overview", Icons.Default.Home),
                Triple("customers", "Customers", Icons.Default.People),
                Triple("schedule", "Schedule", Icons.Default.DateRange),
                Triple("campaigns", "Campaigns", Icons.Default.LocationOn)).forEach { (id, title, icon) ->
                NavigationBarItem(
                    selected = page == id || (page == "campaign_detail" && id == "campaigns"),
                    onClick = { page = id },
                    icon = { Icon(icon, contentDescription = title) }, label = { Text(title, fontSize = 11.sp) }
                )
            }
        }
    }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("ROUTEREVIVE", color = Highlight, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp)
                    Text(when (page) {
                        "home" -> "Your business, reconnected"
                        "customers" -> "Customer records"
                        "new_customer" -> "Add customer"
                        "edit_customer" -> "Edit customer"
                        "schedule" -> "Appointments"
                        "campaigns" -> "Neighborhood campaigns"
                        "new_campaign" -> "New campaign"
                        "campaign_detail" -> current?.title ?: "Campaign"
                        else -> "RouteRevive"
                    }, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                if (page == "customers" || page == "campaigns") {
                    IconButton(onClick = { page = if (page == "customers") "new_customer" else "new_campaign" }) {
                        Icon(Icons.Default.AddCircle, "Add", tint = Highlight)
                    }
                } else if (page !in listOf("home")) {
                    IconButton(onClick = { page = if (page == "campaign_detail" || page == "new_campaign") "campaigns" else "customers" }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                }
            }
            if (appError.isNotBlank()) Text(appError, color = Color(0xFFFF9D9D),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            when (page) {
                "home" -> HomeScreen(customers, campaigns,
                    onCustomers = { page = "customers" }, onCampaigns = { page = "campaigns" },
                    onNew = { page = "new_campaign" },
                    onExport = { exportLauncher.launch("RouteRevive-backup-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    backupMessage = lastBackupMessage)
                "customers" -> CustomerScreen(customers.toList(),
                    onAdd = { page = "new_customer" },
                    onEdit = { editingCustomerId = it; page = "edit_customer" },
                    onOptOut = { id -> customers.firstOrNull { it.id == id }?.let { updateCustomer(it.copy(optedOut = true)) } },
                    onIssue = { id -> customers.firstOrNull { it.id == id }?.let { updateCustomer(it.copy(issueOpen = !it.issueOpen)) } },
                    onBooking = { id -> customers.firstOrNull { it.id == id }?.let { updateCustomer(it.copy(futureBooked = !it.futureBooked)) } })
                "new_customer" -> NewCustomerScreen(onSave = {
                    customers.add(it); save(); page = "customers"
                })
                "edit_customer" -> customers.firstOrNull { it.id == editingCustomerId }?.let { customer ->
                    CustomerEditor(customer, onSave = {
                        updateCustomer(it); page = "customers"
                    }, onCancel = { page = "customers" })
                }
                "schedule" -> ScheduleScreen(customers.toList(), appointments.toList(),
                    onNew = { appointment ->
                        if (!ScheduleRules.overlaps(appointment, appointments.toList())) {
                            appointments.add(appointment); save()
                        } else appError = "Time slot overlaps another booking."
                    }, onStatus = { a, status ->
                        val i = appointments.indexOfFirst { it.id == a.id }
                        if (i >= 0) {
                            appointments[i] = a.copy(status = status)
                            if (a.campaignId.isNotBlank()) {
                                val campaignIndex = campaigns.indexOfFirst { it.id == a.campaignId }
                                if (campaignIndex >= 0) {
                                    val campaign = campaigns[campaignIndex]
                                    campaigns[campaignIndex] = campaign.copy(
                                        recipients = campaign.recipients.map { r ->
                                            if (r.customerId == a.customerId && r.status == "BOOKED") {
                                                r.copy(status = if (status == "CANCELLED") "CANCELLED" else "COMPLETED")
                                            } else r
                                        })
                                }
                            }
                            save()
                        }
                    })
                "campaigns" -> CampaignScreen(campaigns.toList(),
                    onNew = { page = "new_campaign" },
                    onSelect = { selectedId = it; page = "campaign_detail" })
                "new_campaign" -> NewCampaignScreen(customers.toList(), campaigns.toList(),
                    appointments.toList(), onSave = {
                    campaigns.add(it); save(); selectedId = it.id; page = "campaign_detail"
                })
                "campaign_detail" -> if (current == null) {
                    Text("Campaign not found.", modifier = Modifier.padding(18.dp))
                } else CampaignDetailScreen(
                    campaign = current, customers = customers.toList(),
                    onEdit = { r ->
                        editorCustomer = current.id; editorRecipient = r.customerId
                        messageDraft = r.message
                    },
                    onSend = { r ->
                        val customer = customers.firstOrNull { it.id == r.customerId }
                        if (customer != null && allowed(customer, current) && !customer.demo &&
                            r.status == "APPROVED" && r.message.isNotBlank()) {
                            openSms(customer.phone, r.message)
                        } else appError = "Cannot open SMS: approval, permission, or eligibility is missing."
                    },
                    onMarkSent = { r ->
                        val customer = customers.firstOrNull { it.id == r.customerId }
                        if (customer != null && !customer.demo && allowed(customer, current) &&
                            r.status == "APPROVED") {
                            updateRecipient(current, r.copy(status = "SENT", sentAt = LocalDate.now().toString()))
                            updateCustomer(customer.copy(lastContact = LocalDate.now().toString()))
                        } else appError = "Cannot mark sent: verify eligibility and approval."
                    },
                    onStatus = { r, status ->
                        if (status == "OPTED_OUT") {
                            customers.firstOrNull { it.id == r.customerId }?.let { updateCustomer(it.copy(optedOut = true)) }
                        }
                        updateRecipient(current, r.copy(status = status))
                        if (status == "CANCELLED") {
                            appointments.indices.forEach { index ->
                                val a = appointments[index]
                                if (a.campaignId == current.id && a.customerId == r.customerId &&
                                    a.status == "SCHEDULED") appointments[index] = a.copy(status = "CANCELLED")
                            }
                            save()
                        }
                    },
                    onMoney = { r, mode ->
                        moneyDialog = r.customerId to mode
                        moneyInput = if (mode == "book") String.format(Locale.US, "%.2f", current.price)
                        else String.format(Locale.US, "%.2f", r.bookedAmount)
                        bookingTime = "09:00"
                        bookingDuration = "60"
                    }
                )
            }
        }
    }

    if (editorRecipient != null && editorCustomer != null) {
        val c = campaigns.firstOrNull { it.id == editorCustomer }
        val r = c?.recipients?.firstOrNull { it.customerId == editorRecipient }
        val customer = customers.firstOrNull { it.id == editorRecipient }
        AlertDialog(
            onDismissRequest = { editorRecipient = null; editorCustomer = null },
            title = { Text("Review message") },
            text = {
                Column {
                    Text("Approval is required for each customer. Do not send marketing messages without documented consent.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = messageDraft, onValueChange = { messageDraft = it },
                        label = { Text("SMS text") }, minLines = 5, maxLines = 9)
                }
            },
            confirmButton = {
                TextButton(enabled = c != null && r != null && customer != null &&
                    c != null && customer != null && allowed(customer, c) && messageDraft.isNotBlank(),
                    onClick = {
                        if (c != null && r != null && customer != null && allowed(customer, c)) {
                            updateRecipient(c, r.copy(status = "APPROVED", message = messageDraft.trim(),
                                approvedAt = LocalDate.now().toString()))
                            editorRecipient = null; editorCustomer = null
                        }
                    }) { Text("Approve") }
            },
            dismissButton = { TextButton(onClick = { editorRecipient = null; editorCustomer = null }) { Text("Cancel") } }
        )
    }

    if (moneyDialog != null) {
        val (recipientId, mode) = moneyDialog!!
        val c = current
        val r = c?.recipients?.firstOrNull { it.customerId == recipientId }
        val amount = moneyInput.toDoubleOrNull()
        AlertDialog(
            onDismissRequest = { moneyDialog = null },
            title = { Text(if (mode == "book") "Confirm booking" else "Record actual payment") },
            text = {
                Column {
                    Text(if (mode == "book") "Enter the agreed booking amount (USD)."
                         else "Enter the amount actually collected (USD).")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(moneyInput, { moneyInput = it }, label = { Text("Amount (USD)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (mode == "book") {
                        Text("Appointment date: ${c?.visitDate ?: ""}", fontSize = 12.sp)
                        OutlinedTextField(bookingTime, { bookingTime = it }, label = { Text("Start HH:mm") })
                        OutlinedTextField(bookingDuration, { bookingDuration = it },
                            label = { Text("Duration (minutes)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        val candidate = if (c != null) Appointment(customerId = recipientId,
                            service = c.service, date = c.visitDate, time = bookingTime,
                            durationMinutes = bookingDuration.toIntOrNull() ?: 60) else null
                        if (candidate != null && ScheduleRules.overlaps(candidate, appointments.toList())) {
                            Text("Appointment overlaps an existing booking.",
                                color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = c != null && r != null && amount != null && amount > 0 &&
                    (mode != "book" || (
                        CampaignRules.bookedSlots(c) < c.capacity &&
                        ScheduleRules.validDate(c.visitDate) &&
                        ScheduleRules.validTime(bookingTime) &&
                        (bookingDuration.toIntOrNull() ?: 0) in 15..480 &&
                        !ScheduleRules.overlaps(Appointment(customerId = recipientId,
                            service = c.service, date = c.visitDate, time = bookingTime,
                            durationMinutes = bookingDuration.toIntOrNull() ?: 60), appointments.toList())
                    )),
                    onClick = {
                        if (c != null && r != null && amount != null && amount > 0) {
                            if (mode == "book" && CampaignRules.bookedSlots(c) < c.capacity &&
                                ScheduleRules.validTime(bookingTime) &&
                                (bookingDuration.toIntOrNull() ?: 0) in 15..480) {
                                val appointment = Appointment(customerId = recipientId,
                                    service = c.service, date = c.visitDate, time = bookingTime,
                                    durationMinutes = bookingDuration.toInt(), price = amount,
                                    campaignId = c.id)
                                if (!ScheduleRules.overlaps(appointment, appointments.toList())) {
                                    appointments.add(appointment)
                                    updateRecipient(c, r.copy(status = "BOOKED", bookedAmount = amount))
                                    save()
                                }
                            }
                            if (mode == "paid") updateRecipient(c, r.copy(status = "PAID", paidAmount = amount))
                        }
                        moneyDialog = null
                    }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { moneyDialog = null }) { Text("Cancel") } }
        )
    }

    if (importCandidate != null) {
        AlertDialog(onDismissRequest = { importCandidate = null },
            title = { Text("Replace local data from backup?") },
            text = { Text("This will replace all customers, campaigns and appointments on this phone. The selected file must be a RouteRevive JSON backup. Store backups privately.") },
            confirmButton = {
                TextButton(onClick = {
                    val content = importCandidate ?: ""
                    runCatching {
                        store.importJson(content)
                        customers.clear(); customers.addAll(store.loadCustomers())
                        campaigns.clear(); campaigns.addAll(store.loadCampaigns())
                        appointments.clear(); appointments.addAll(store.loadAppointments())
                    }.onSuccess { lastBackupMessage = "Backup imported successfully"; appError = "" }
                     .onFailure { appError = "Import rejected: ${it.message}" }
                    importCandidate = null
                }) { Text("Replace data") }
            }, dismissButton = {
                TextButton(onClick = { importCandidate = null }) { Text("Cancel") }
            })
    }
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
        Text(label, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Highlight)
    }
}

@Composable
private fun HomeScreen(customers: List<Customer>, campaigns: List<Campaign>,
                       onCustomers: () -> Unit, onCampaigns: () -> Unit, onNew: () -> Unit,
                       onExport: () -> Unit, onImport: () -> Unit, backupMessage: String) {
    val paid = campaigns.sumOf { campaign -> campaign.recipients.sumOf { it.paidAmount } }
    val booked = campaigns.sumOf { CampaignRules.bookedSlots(it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InfoCard {
            Text("Turn old customers into new bookings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Build repeat-service campaigns by ZIP code, approve every message, and record actual revenue.",
                color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNew) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Create campaign") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Customers", customers.size.toString(), Modifier.weight(1f))
            Stat("Booked jobs", booked.toString(), Modifier.weight(1f))
        }
        Stat("Recorded collected revenue", usd(paid), Modifier.fillMaxWidth())
        InfoCard {
            Text("Start here", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text("1. Add actual customer records and their last service dates.")
            Text("2. Record verifiable permission for promotional messages.")
            Text("3. Create an offer for a ZIP code and review eligible customers.")
            Text("4. Approve messages individually, then send using your own SMS app.")
            Text("5. Track bookings and completed payments.")
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCustomers) { Text("Manage customers") }
            TextButton(onClick = onCampaigns) { Text("View campaigns") }
        }
        InfoCard {
            Text("Back up your data", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text("Export a local JSON backup before changing phones or uninstalling. The file contains customer phone numbers and other private information; store it securely.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            Row {
                OutlinedButton(onClick = onExport) { Text("Export") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onImport) { Text("Import") }
            }
            if (backupMessage.isNotBlank()) Text(backupMessage, fontSize = 12.sp, color = Highlight)
        }
        Text("V0.0.2 · Local-only MVP · No automatic texting, cloud sync or payments",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun CustomerScreen(customers: List<Customer>, onAdd: () -> Unit,
                           onEdit: (String) -> Unit,
                           onOptOut: (String) -> Unit, onIssue: (String) -> Unit,
                           onBooking: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = customers.filter {
        it.name.contains(query, ignoreCase = true) ||
        it.phone.contains(query) || it.zip.contains(query) ||
        it.service.contains(query, ignoreCase = true)
    }.sortedBy { it.name.lowercase() }
    if (customers.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.People, null, modifier = Modifier.size(52.dp), tint = Highlight)
            Text("No customer records yet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Add a customer to begin evaluating repeat service opportunities.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) { Text("Add customer") }
        }
    } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(value = query, onValueChange = { query = it },
                label = { Text("Search names, phone, ZIP or service") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Text(" Add customer") }
            Text("${matches.size} of ${customers.size} customers",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }
        items(matches, key = { it.id }) { c ->
            InfoCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(c.zip, color = Highlight)
                }
                Text("${c.service} · Last job ${c.lastService}", color = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp)
                Text(if (c.consent && c.consentEvidence.isNotBlank()) "SMS permission documented" else "No SMS marketing permission",
                    color = if (c.consent && c.consentEvidence.isNotBlank()) Highlight else Color(0xFFFBBF24),
                    fontSize = 12.sp)
                if (c.address.isNotBlank()) Text(c.address, fontSize = 12.sp)
                if (c.notes.isNotBlank()) Text(c.notes, fontSize = 12.sp, maxLines = 2)
                OutlinedButton(onClick = { onEdit(c.id) }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Text(" Edit profile")
                }
                if (c.optedOut) Text("OPTED OUT — promotional contact blocked", color = Color(0xFFFF9D9D), fontSize = 12.sp)
                if (c.issueOpen) Text("Unresolved issue — campaign blocked", color = Color(0xFFFBBF24), fontSize = 12.sp)
                if (c.futureBooked) Text("Upcoming appointment recorded", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onIssue(c.id) }) { Text(if (c.issueOpen) "Resolve issue" else "Flag issue", fontSize = 12.sp) }
                    TextButton(onClick = { onBooking(c.id) }) { Text(if (c.futureBooked) "Clear booked" else "Has booking", fontSize = 12.sp) }
                }
                if (!c.optedOut) {
                    TextButton(onClick = { onOptOut(c.id) }) { Text("Record opt-out", color = Color(0xFFFF9D9D), fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun NewCustomerScreen(onSave: (Customer) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("House pressure washing") }
    var days by remember { mutableStateOf("220") }
    var price by remember { mutableStateOf("150") }
    var consent by remember { mutableStateOf(false) }
    var evidence by remember { mutableStateOf("") }
    val good = name.isNotBlank() && zip.matches(Regex("\\d{5}")) &&
        CampaignRules.digits(phone).length in 10..15 && service.isNotBlank() &&
        (days.toLongOrNull() ?: -1) in 0..36500 &&
        (price.toDoubleOrNull() ?: -1.0) >= 0.0 &&
        (!consent || evidence.isNotBlank())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add an actual customer record. No contact is sent from this form.",
            color = MaterialTheme.colorScheme.secondary)
        Field("Customer name", name) { name = it }
        Field("Phone number", phone, KeyboardType.Phone) { phone = it }
        Field("5-digit ZIP code", zip, KeyboardType.Number) { zip = it }
        Field("Previous service", service) { service = it }
        Field("Days since last service", days, KeyboardType.Number) { days = it }
        Field("Previous job amount (USD)", price, KeyboardType.Decimal) { price = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it })
            Text("I have documented permission to send promotional SMS to this customer.", fontSize = 13.sp)
        }
        if (consent) Field("Permission source, date, and evidence", evidence) { evidence = it }
        Text("Do not claim permission unless you can substantiate it. Mark opt-outs in the customer list.",
            color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
        Button(onClick = {
            onSave(Customer(
                name = name.trim(), phone = phone.trim(), zip = zip.trim(), service = service.trim(),
                lastService = LocalDate.now().minusDays(days.toLong()).toString(),
                consent = consent, consentEvidence = if (consent) evidence.trim() else "",
                lastPrice = price.toDouble()
            ))
        }, enabled = good, modifier = Modifier.fillMaxWidth()) { Text("Save customer") }
    }
}

@Composable
private fun CampaignScreen(campaigns: List<Campaign>, onNew: () -> Unit, onSelect: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Button(onClick = onNew) { Icon(Icons.Default.Add, null); Text(" New neighborhood campaign") }
        }
        if (campaigns.isEmpty()) item {
            InfoCard {
                Text("Your campaigns will appear here.", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Create a campaign after adding some customers.", color = MaterialTheme.colorScheme.secondary)
            }
        }
        items(campaigns.reversed(), key = { it.id }) { c ->
            InfoCard(Modifier.fillMaxWidth()) {
                Text(c.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${c.service} · ZIP ${c.zip} · ${c.visitDate}",
                    color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${c.recipients.size} recipients", color = Highlight)
                    Text("${CampaignRules.bookedSlots(c)}/${c.capacity} slots", color = Color.White)
                }
                Text("Collected: ${usd(c.recipients.sumOf { it.paidAmount })}", color = Highlight)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { onSelect(c.id) }) { Text("Open campaign") }
            }
        }
    }
}

@Composable
private fun NewCampaignScreen(customers: List<Customer>, campaigns: List<Campaign>,
                              appointments: List<Appointment>, onSave: (Campaign) -> Unit) {
    var title by remember { mutableStateOf("Neighborhood repeat-service offer") }
    var business by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("House pressure washing") }
    var dateDays by remember { mutableStateOf("7") }
    var expiryDays by remember { mutableStateOf("5") }
    var price by remember { mutableStateOf("150") }
    var discount by remember { mutableStateOf("0") }
    var capacity by remember { mutableStateOf("4") }

    val eligible = customers.filter { c ->
        val upcoming = appointments.any { a -> a.customerId == c.id && a.status == "SCHEDULED" &&
            runCatching { !LocalDate.parse(a.date).isBefore(LocalDate.now()) }.getOrDefault(false) }
        CampaignRules.reasons(c.copy(futureBooked = c.futureBooked || upcoming),
            zip, service, LocalDate.now(), customers, campaigns).isEmpty()
    }
    val good = title.isNotBlank() && business.isNotBlank() && zip.matches(Regex("\\d{5}")) &&
        service.isNotBlank() && (dateDays.toLongOrNull() ?: -1) in 1..365 &&
        (expiryDays.toLongOrNull() ?: -1) in 1..(dateDays.toLongOrNull() ?: 0) &&
        (price.toDoubleOrNull() ?: -1.0) > 0.0 &&
        (discount.toIntOrNull() ?: -1) in 0..50 &&
        (capacity.toIntOrNull() ?: -1) in 1..99 && eligible.isNotEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Create an offer for previous customers. Only people with documented permission and no exclusions can be included.",
            color = MaterialTheme.colorScheme.secondary)
        Field("Campaign title", title) { title = it }
        Field("Your business name (shown in messages)", business) { business = it }
        Field("Neighborhood 5-digit ZIP", zip, KeyboardType.Number) { zip = it }
        Field("Service offered (matches customer service)", service) { service = it }
        Field("Service date, days from today", dateDays, KeyboardType.Number) { dateDays = it }
        Field("Offer expiration, days from today", expiryDays, KeyboardType.Number) { expiryDays = it }
        Field("Starting price in USD", price, KeyboardType.Decimal) { price = it }
        Field("Discount percentage (0–50)", discount, KeyboardType.Number) { discount = it }
        Field("Maximum confirmed bookings", capacity, KeyboardType.Number) { capacity = it }
        InfoCard {
            Text("Eligibility preview", fontWeight = FontWeight.Bold)
            Text("${eligible.size} of ${customers.size} records eligible", color = Highlight, fontSize = 21.sp)
            Text("Repeat interval: 180 days · Contact cooldown: 60 days",
                color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            if (customers.isNotEmpty() && eligible.isEmpty())
                Text("No qualifying customers. Check ZIP, service, service dates, and permission evidence.",
                    color = Color(0xFFFBBF24), fontSize = 12.sp)
        }
        Button(enabled = good, modifier = Modifier.fillMaxWidth(), onClick = {
            val base = Campaign(title = title.trim(), businessName = business.trim(),
                zip = zip, service = service.trim(),
                visitDate = LocalDate.now().plusDays(dateDays.toLong()).toString(),
                expiryDate = LocalDate.now().plusDays(expiryDays.toLong()).toString(),
                price = price.toDouble(), discountPct = discount.toInt(), capacity = capacity.toInt())
            onSave(base.copy(recipients = eligible.map {
                Recipient(customerId = it.id, message = CampaignRules.message(it, base))
            }))
        }) { Text("Create campaign with ${eligible.size} customers") }
    }
}

@Composable
private fun CampaignDetailScreen(campaign: Campaign, customers: List<Customer>,
    onEdit: (Recipient) -> Unit, onSend: (Recipient) -> Unit, onMarkSent: (Recipient) -> Unit,
    onStatus: (Recipient, String) -> Unit, onMoney: (Recipient, String) -> Unit) {
    val booked = CampaignRules.bookedSlots(campaign)
    val expired = LocalDate.parse(campaign.expiryDate).isBefore(LocalDate.now())
    val paid = campaign.recipients.sumOf { it.paidAmount }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            InfoCard {
                Text("${campaign.service} · ZIP ${campaign.zip}", fontWeight = FontWeight.Bold)
                Text("Service ${campaign.visitDate} · Offer expires ${campaign.expiryDate}",
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))
                Text("${booked}/${campaign.capacity} bookings · Collected ${usd(paid)}",
                    color = Highlight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (expired) Text("Expired: promotional sends disabled", color = Color(0xFFFBBF24))
            }
        }
        item {
            Text("CUSTOMER APPROVAL QUEUE", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
        }
        items(campaign.recipients, key = { it.customerId }) { r ->
            val customer = customers.firstOrNull { it.id == r.customerId }
            if (customer != null) {
                InfoCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.status.replace("_", " "), color = if (r.status == "PAID") Highlight else Color(0xFFFBBF24),
                            fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(r.message, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    if (r.status == "DRAFT") Button(onClick = { onEdit(r) },
                        enabled = !customer.optedOut && !expired && !customer.demo) { Text("Edit & approve message") }
                    if (r.status == "APPROVED") {
                        Button(onClick = { onSend(r) }, enabled = !customer.optedOut && !expired && !customer.demo) {
                            Icon(Icons.Default.Send, null); Text(" Open SMS app")
                        }
                        OutlinedButton(onClick = { onMarkSent(r) }, enabled = !customer.optedOut && !expired) {
                            Text("I sent this message")
                        }
                        TextButton(onClick = { onEdit(r) }) { Text("Revise & reapprove") }
                    }
                    if (r.status == "SENT") {
                        Button(onClick = { onStatus(r, "INTERESTED") }) { Text("Customer interested") }
                        TextButton(onClick = { onStatus(r, "DECLINED") }) { Text("Declined") }
                    }
                    if (r.status == "INTERESTED") {
                        Button(onClick = { onMoney(r, "book") }, enabled = booked < campaign.capacity) {
                            Text("Confirm appointment")
                        }
                        TextButton(onClick = { onStatus(r, "DECLINED") }) { Text("Declined") }
                    }
                    if (r.status == "BOOKED") {
                        Text("Booking value: ${usd(r.bookedAmount)}", fontSize = 12.sp)
                        Button(onClick = { onStatus(r, "COMPLETED") }) { Text("Mark job completed") }
                        TextButton(onClick = { onStatus(r, "CANCELLED") }) { Text("Cancel booking") }
                    }
                    if (r.status == "COMPLETED") {
                        Text("Booking value: ${usd(r.bookedAmount)}", fontSize = 12.sp)
                        Button(onClick = { onMoney(r, "paid") }) { Text("Record paid amount") }
                    }
                    if (r.status == "PAID") {
                        Text("Collected: ${usd(r.paidAmount)}", color = Highlight, fontWeight = FontWeight.Bold)
                    }
                    if (r.status in listOf("APPROVED", "SENT", "INTERESTED", "DECLINED", "DRAFT") && !customer.optedOut) {
                        TextButton(onClick = { onStatus(r, "OPTED_OUT") }) {
                            Text("Record customer opt-out", color = Color(0xFFFF9D9D), fontSize = 12.sp)
                        }
                    }
                    if (customer.optedOut) Text("Promotional contact blocked by opt-out.",
                        color = Color(0xFFFF9D9D), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text,
                  onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth())
}

private fun usd(value: Double): String = "$" + String.format(Locale.US, "%,.2f", value)

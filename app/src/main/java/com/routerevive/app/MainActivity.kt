package com.routerevive.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
                SecurityGate(store) { RouteApp(store, openSms = { phone, message ->
                    try {
                        startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null)).apply {
                            putExtra("sms_body", message)
                        })
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this, "No SMS app installed", Toast.LENGTH_LONG).show()
                    }
                }) }
            }
        }
    }
}

@Composable
private fun RouteApp(store: LocalStore, openSms: (String, String) -> Unit) {
    val customers = remember { mutableStateListOf<Customer>().apply { addAll(store.loadCustomers()) } }
    val campaigns = remember { mutableStateListOf<Campaign>().apply { addAll(store.loadCampaigns()) } }
    val appointments = remember { mutableStateListOf<Appointment>().apply { addAll(store.loadAppointments()) } }
    val jobs = remember { mutableStateListOf<JobRecord>().apply { addAll(store.loadJobs()) } }
    val bookingRequests = remember { mutableStateListOf<BookingRequest>().apply { addAll(store.loadBookingRequests()) } }
    var business by remember { mutableStateOf(store.loadBusinessProfile()) }
    var launchLock by remember { mutableStateOf(store.isAppLockEnabled()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var page by remember { mutableStateOf("home") }
    var selectedId by remember { mutableStateOf("") }
    var campaignZipFromMap by remember { mutableStateOf("") }
    var campaignServiceFromMap by remember { mutableStateOf("House pressure washing") }
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
    var backupAction by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var backupBusy by remember { mutableStateOf(false) }

    val encryptedExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val chars = backupPassword.toCharArray()
            backupPassword = ""
            backupBusy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            BackupArchive.create(context, store, chars, it)
                        } ?: error("Cannot open destination")
                    }.also { chars.fill(0.toChar()) }
                }
                backupBusy = false
                result.onSuccess { lastBackupMessage = "Encrypted photo-inclusive backup saved. Keep the password safe." }
                    .onFailure { appError = "Encrypted backup failed: " + it.message }
            }
        } else backupPassword = ""
    }
    val encryptedImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val chars = backupPassword.toCharArray()
            backupPassword = ""
            backupBusy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use {
                            BackupArchive.restore(context, store, chars, it)
                        } ?: error("Cannot open backup file")
                    }.also { chars.fill(0.toChar()) }
                }
                backupBusy = false
                result.onSuccess {
                    customers.clear(); customers.addAll(store.loadCustomers())
                    campaigns.clear(); campaigns.addAll(store.loadCampaigns())
                    appointments.clear(); appointments.addAll(store.loadAppointments())
                    jobs.clear(); jobs.addAll(store.loadJobs())
                    bookingRequests.clear(); bookingRequests.addAll(store.loadBookingRequests())
                    business = store.loadBusinessProfile()
                    lastBackupMessage = "Encrypted backup restored with photos and financial records."
                    appError = ""
                }.onFailure { appError = "Backup restore failed: " + (it.message ?: "Incorrect password or damaged file") }
            }
        } else backupPassword = ""
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Cannot open legacy backup")
            }.onSuccess { importCandidate = it }
             .onFailure { appError = "Legacy backup could not be read: " + it.message }
        }
    }

    fun save() {
        try { store.save(customers.toList(), campaigns.toList(), appointments.toList(), jobs.toList(), business, bookingRequests.toList()); appError = "" }
        catch (e: Exception) { appError = "Unable to save changes: ${e.message}" }
    }
    fun confirmBookingRequest(incoming: BookingRequest) {
        val index = bookingRequests.indexOfFirst { it.id == incoming.id }
        if (index < 0) { appError = "Booking request is missing."; return }
        val current = bookingRequests[index]
        if (current.status !in setOf("NEW", "CONTACTED")) {
            appError = "This request was already resolved."
            return
        }
        val proposal = current.copy(
            requestedDate = incoming.requestedDate,
            requestedTime = incoming.requestedTime,
            durationMinutes = incoming.durationMinutes,
            quotedPrice = incoming.quotedPrice)
        if (!BookingRules.bookable(proposal, appointments.toList())) {
            appError = "Cannot confirm: invalid date/time or another booking occupies this slot."
            return
        }
        val phoneDigits = CampaignRules.digits(proposal.phone)
        val known = customers.firstOrNull { CampaignRules.digits(it.phone) == phoneDigits }
        val customer = known ?: Customer(
            name = proposal.name, phone = proposal.phone, zip = proposal.zip,
            service = proposal.service, lastService = LocalDate.now().toString(),
            consent = false,
            notes = "New booking inquiry; no earlier completed service verified."
        )
        val appointment = Appointment(
            customerId = customer.id, service = proposal.service,
            date = proposal.requestedDate, time = proposal.requestedTime,
            durationMinutes = proposal.durationMinutes, price = proposal.quotedPrice,
            notes = proposal.notes)
        // One commit covers the new customer, appointment and request status.
        if (known == null) customers.add(customer)
        appointments.add(appointment)
        bookingRequests[index] = proposal.copy(status = "BOOKED",
            bookedAppointmentId = appointment.id)
        save()
    }
    fun updateJob(job: JobRecord) {
        val appointment = appointments.firstOrNull { it.id == job.appointmentId }
        if (appointment == null || appointment.customerId != job.customerId ||
            job.lineItems.size > 20 || job.lineItems.any { !JobMath.validLineItem(it) } ||
            job.photos.size > 16 || job.payments.any { it.amount <= 0 || !it.amount.isFinite() } ||
            JobMath.paid(job) > JobMath.subtotal(job) + 0.001) {
            appError = "Job details not saved: invalid amount or appointment."
            return
        }
        val index = jobs.indexOfFirst { it.appointmentId == job.appointmentId }
        if (index < 0) jobs.add(job) else jobs[index] = job
        save()
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
        appointments.any { a -> a.customerId == customerId && a.status in setOf("SCHEDULED", "IN_PROGRESS") &&
            runCatching { !LocalDate.parse(a.date).isBefore(LocalDate.now()) }.getOrDefault(false) }

    fun updateAppointmentStatus(appointment: Appointment, status: String) {
        val i = appointments.indexOfFirst { it.id == appointment.id }
        if (i < 0) return
        val currentAppointment = appointments[i]
        if (currentAppointment.status !in setOf("SCHEDULED", "IN_PROGRESS")) return
        if (status !in setOf("IN_PROGRESS", "COMPLETED", "CANCELLED")) return
        if (status == "IN_PROGRESS" && currentAppointment.status != "SCHEDULED") return
        appointments[i] = currentAppointment.copy(status = status)
        if (currentAppointment.campaignId.isNotBlank() && status in setOf("COMPLETED", "CANCELLED")) {
            val campaignIndex = campaigns.indexOfFirst { it.id == currentAppointment.campaignId }
            if (campaignIndex >= 0) {
                val campaign = campaigns[campaignIndex]
                campaigns[campaignIndex] = campaign.copy(recipients = campaign.recipients.map { r ->
                    if (r.customerId == currentAppointment.customerId && r.status == "BOOKED") {
                        r.copy(status = status)
                    } else r
                })
            }
        }
        save()
    }

    fun rescheduleAppointment(revised: Appointment) {
        val i = appointments.indexOfFirst { it.id == revised.id }
        if (i < 0 || appointments[i].status != "SCHEDULED" ||
            !ScheduleRules.validDate(revised.date) || !ScheduleRules.validTime(revised.time) ||
            revised.durationMinutes !in 15..480 ||
            ScheduleRules.overlaps(revised, appointments.toList())) {
            appError = "Could not reschedule: check the booking and time slot."
            return
        }
        appointments[i] = revised
        save()
    }

    fun allowed(customer: Customer, campaign: Campaign): Boolean =
        CampaignRules.reasons(
            customer.copy(futureBooked = customer.futureBooked || hasFutureAppointment(customer.id)),
            campaign.zip, campaign.service, LocalDate.now(),
            customers.toList(), campaigns.filterNot { it.id == campaign.id }
        ).isEmpty() && !LocalDate.parse(campaign.expiryDate).isBefore(LocalDate.now())

    val current = campaigns.firstOrNull { it.id == selectedId }
    BackHandler(page !in setOf("home", "customers", "schedule", "jobs", "more")) {
        page = when (page) {
            "campaign_detail", "new_campaign" -> "campaigns"
            "new_customer", "edit_customer" -> "customers"
            else -> "more"
        }
    }

    Scaffold(containerColor = Ink, bottomBar = {
        NavigationBar(containerColor = Panel) {
            listOf(Triple("home", "Overview", Icons.Default.Home),
                Triple("customers", "Customers", Icons.Default.People),
                Triple("schedule", "Schedule", Icons.Default.DateRange),
                Triple("jobs", "Jobs", Icons.Default.Build),
                Triple("more", "More", Icons.Default.MoreHoriz)).forEach { (id, title, icon) ->
                NavigationBarItem(
                    selected = page == id || (id == "more" &&
                        page in setOf("map", "campaigns", "campaign_detail",
                            "new_campaign", "requests", "business", "booking_page")),
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
                        "jobs" -> "Jobs & invoices"
                        "business" -> "Business essentials"
                        "requests" -> "Booking requests"
                        "more" -> "More tools"
                        "booking_page" -> "Customer booking page"
                        "map" -> "Neighborhood map"
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
                    IconButton(onClick = { page = when (page) {
                        "campaign_detail", "new_campaign" -> "campaigns"
                        "new_customer", "edit_customer" -> "customers"
                        else -> "more"
                    } }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                }
            }
            if (appError.isNotBlank()) Text(appError, color = Color(0xFFFF9D9D),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            when (page) {
                "home" -> DashboardScreen(
                    customers = customers.toList(), campaigns = campaigns.toList(),
                    appointments = appointments.toList(), jobs = jobs.toList(),
                    requests = bookingRequests.toList(), business = business,
                    onCustomers = { page = "customers" },
                    onJobs = { page = "jobs" },
                    onSchedule = { page = "schedule" },
                    onRequests = { page = "requests" },
                    onCampaigns = { page = "campaigns" },
                    onBusiness = { page = "business" },
                    onExport = { backupAction = "export"; backupPassword = "" },
                    onImport = { backupAction = "import"; backupPassword = "" },
                    onLegacyImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    backupBusy = backupBusy, backupMessage = lastBackupMessage)
                "more" -> MoreScreen(
                    business = business,
                    onMap = { page = "map" },
                    onRequests = { page = "requests" },
                    onCampaigns = { page = "campaigns" },
                    onBusiness = { page = "business" },
                    onBookingPage = { page = "booking_page" })
                "booking_page" -> BookingPageScreen(
                    profile = business, onBack = { page = "more" })
                "requests" -> RequestsScreen(
                    requests = bookingRequests.toList(),
                    appointments = appointments.toList(),
                    onAdd = { request ->
                        if (BookingRules.valid(request)) {
                            bookingRequests.add(request); save()
                        } else appError = "Invalid booking request."
                    },
                    onStatus = { request, status ->
                        val i = bookingRequests.indexOfFirst { it.id == request.id }
                        if (i >= 0 && bookingRequests[i].status in setOf("NEW", "CONTACTED") &&
                            status in setOf("CONTACTED", "DECLINED")) {
                            bookingRequests[i] = bookingRequests[i].copy(status = status); save()
                        }
                    },
                    onBook = { confirmBookingRequest(it) })
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
                "map" -> NeighborhoodMapScreen(
                    customers = customers.toList(), campaigns = campaigns.toList(),
                    appointments = appointments.toList(),
                    onSavePin = { id, latitude, longitude ->
                        customers.firstOrNull { it.id == id }?.let {
                            updateCustomer(it.copy(latitude = latitude, longitude = longitude))
                        }
                    },
                    onStartCampaign = { zip, service ->
                        campaignZipFromMap = zip
                        campaignServiceFromMap = service
                        page = "new_campaign"
                    },
                    onAppointmentStatus = { a, status -> updateAppointmentStatus(a, status) },
                    onOpenSchedule = { page = "schedule" }
                )
                "business" -> BusinessScreen(business, customers.toList(), jobs.toList(),
                    appointments.toList(), campaigns.toList(), launchLock,
                    onSave = { business = it; save() },
                    onAddCustomers = { list ->
                        val known = customers.map { CampaignRules.digits(it.phone) }.toMutableSet()
                        val safe = list.filter { !it.consent && known.add(CampaignRules.digits(it.phone)) }
                        customers.addAll(safe); save()
                    },
                    onAppLock = { enabled -> store.setAppLockEnabled(enabled); launchLock = enabled })
                "jobs" -> JobsScreen(appointments.toList(), customers.toList(), jobs.toList(),
                    business = business,
                    onSaveJob = { updateJob(it) }, onOpenSchedule = { page = "schedule" })
                "schedule" -> ScheduleScreen(customers.toList(), appointments.toList(),
                    onNew = { appointment ->
                        if (!ScheduleRules.overlaps(appointment, appointments.toList())) {
                            appointments.add(appointment); save()
                        } else appError = "Time slot overlaps another booking."
                    }, onStatus = { a, status -> updateAppointmentStatus(a, status) },
                    onReschedule = { revised -> rescheduleAppointment(revised) },
                    onRemind = { appointment ->
                        val currentAppointment = appointments.firstOrNull { it.id == appointment.id }
                        val customer = customers.firstOrNull { it.id == currentAppointment?.customerId }
                        if (currentAppointment != null && customer != null &&
                            ReminderRules.canDraft(currentAppointment, customer)) {
                            openSms(customer.phone, ReminderRules.draft(
                                currentAppointment, customer, business.name))
                        } else appError = "Reminder blocked: check customer preferences and booking date."
                    },
                    onMarkReminded = { appointment ->
                        val i = appointments.indexOfFirst { it.id == appointment.id }
                        val customer = customers.firstOrNull { it.id == appointment.customerId }
                        if (i >= 0 && customer != null &&
                            ReminderRules.canDraft(appointments[i], customer)) {
                            appointments[i] = appointments[i].copy(
                                lastReminderAt = LocalDate.now().toString())
                            save()
                        }
                    })

                "campaigns" -> CampaignScreen(campaigns.toList(),
                    onNew = {
                        campaignZipFromMap = ""
                        campaignServiceFromMap = "House pressure washing"
                        page = "new_campaign"
                    },
                    onSelect = { selectedId = it; page = "campaign_detail" })
                "new_campaign" -> NewCampaignScreen(customers.toList(), campaigns.toList(),
                    appointments.toList(), campaignZipFromMap, campaignServiceFromMap, onSave = {
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
                                    a.status in setOf("SCHEDULED", "IN_PROGRESS")) appointments[index] = a.copy(status = "CANCELLED")
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

    if (backupAction.isNotBlank()) {
        AlertDialog(onDismissRequest = { backupAction = ""; backupPassword = "" },
            title = { Text(if (backupAction == "export") "Create full encrypted backup"
                else "Restore full encrypted backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (backupAction == "export")
                        "Includes all records and actual photo files. Use a strong password (10 or more characters). Keep it safe; it cannot be recovered."
                    else "WARNING: Restoring will replace all customers, appointments, campaigns, jobs, financial records and business settings on this phone. Enter the archive's password to continue.")
                    OutlinedTextField(value = backupPassword, onValueChange = { backupPassword = it },
                        label = { Text("Backup password (minimum 10 characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = backupPassword.length >= 10, onClick = {
                    val action = backupAction
                    backupAction = ""
                    if (action == "export") encryptedExport.launch("RouteRevive-full-" +
                        LocalDate.now().toString() + ".rrb")
                    else encryptedImport.launch(arrayOf("application/octet-stream", "*/*"))
                }) { Text(if (backupAction == "export") "Choose destination" else "Select backup & replace") }
            },
            dismissButton = { TextButton(onClick = { backupAction = ""; backupPassword = "" }) {
                Text("Cancel")
            } }
        )
    }

    if (importCandidate != null) {
        AlertDialog(onDismissRequest = { importCandidate = null },
            title = { Text("Replace local data from backup?") },
            text = { Text("This replaces customers, campaigns, appointments and job records. Photo images are NOT included in JSON backups. The selected file must be a RouteRevive JSON backup. Store backups privately.") },
            confirmButton = {
                TextButton(onClick = {
                    val content = importCandidate ?: ""
                    runCatching {
                        store.importJson(content)
                        customers.clear(); customers.addAll(store.loadCustomers())
                        campaigns.clear(); campaigns.addAll(store.loadCampaigns())
                        appointments.clear(); appointments.addAll(store.loadAppointments())
                        jobs.clear(); jobs.addAll(store.loadJobs())
                        business = store.loadBusinessProfile()
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
                       jobs: List<JobRecord>,
                       onCustomers: () -> Unit, onCampaigns: () -> Unit, onNew: () -> Unit,
                       onJobs: () -> Unit, onSchedule: () -> Unit,
                       onExport: () -> Unit, onImport: () -> Unit, onLegacyImport: () -> Unit,
                       onBusiness: () -> Unit, backupBusy: Boolean, backupMessage: String) {
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
        Stat("Campaign revenue recorded", usd(paid), Modifier.fillMaxWidth())
        Stat("Payments received on job invoices", usd(jobs.sumOf { JobMath.paid(it) }), Modifier.fillMaxWidth())
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
            OutlinedButton(onClick = onJobs) { Text("Open jobs & invoices") }
            OutlinedButton(onClick = onBusiness) { Text("Business profile, reporting & imports") }
            OutlinedButton(onClick = onSchedule) { Text("Manage appointment schedule") }
            TextButton(onClick = onCampaigns) { Text("View campaigns") }
        }
        InfoCard {
            Text("Back up your data", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text("Create a password-encrypted full backup with records, before/after photos, logo, invoices and payments. Remember your password: it cannot be recovered. An existing legacy JSON backup can still be imported separately.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            Row {
                OutlinedButton(onClick = onExport, enabled = !backupBusy) { Text("Encrypted backup") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onImport, enabled = !backupBusy) { Text("Restore full backup") }
            }
            TextButton(onClick = onLegacyImport, enabled = !backupBusy) { Text("Import older JSON (photos not included)") }
            if (backupBusy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            if (backupMessage.isNotBlank()) Text(backupMessage, fontSize = 12.sp, color = Highlight)
        }
        Text("V0.0.7 · Encrypted local records · No automatic texting or cloud payments",
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
    var address by remember { mutableStateOf("") }
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
        Field("Street address (optional; for the map)", address) { address = it }
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
                lastPrice = price.toDouble(), address = address.trim()
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
                              appointments: List<Appointment>,
                              initialZip: String, initialService: String, onSave: (Campaign) -> Unit) {
    var title by remember { mutableStateOf("Neighborhood repeat-service offer") }
    var business by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf(initialZip) }
    var service by remember { mutableStateOf(initialService) }
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

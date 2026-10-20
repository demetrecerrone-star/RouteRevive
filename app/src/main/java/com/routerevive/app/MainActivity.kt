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
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val store = LocalStore(this)
        setContent {
            RouteReviveTheme {
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
    val controller = remember { WorkspaceController(store.loadWorkspace(), store::saveWorkspace) }
    var workspace by remember { mutableStateOf(controller.current) }
    val customers = workspace.customers
    val campaigns = workspace.campaigns
    val appointments = workspace.appointments
    val jobs = workspace.jobs
    val bookingRequests = workspace.requests
    val business = workspace.business
    fun reloadRecords() { workspace = controller.reload(store.loadWorkspace()) }
    var launchLock by remember { mutableStateOf(store.isAppLockEnabled()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf("home") }
    var selectedJobId by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var campaignZipFromMap by rememberSaveable { mutableStateOf("") }
    var campaignServiceFromMap by rememberSaveable { mutableStateOf("House pressure washing") }
    var editingCustomerId by rememberSaveable { mutableStateOf("") }
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
    var lastFullBackup by remember { mutableStateOf(store.lastFullBackupDate()) }
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
                result.onSuccess {
                    runCatching { store.recordFullBackupCreated() }
                        .onSuccess { lastFullBackup = store.lastFullBackupDate() }
                    lastBackupMessage = "Encrypted photo-inclusive backup saved. Keep the password safe."
                    appError = ""
                }
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
                    reloadRecords()
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

    fun change(action: (WorkspaceSnapshot) -> WorkspaceSnapshot): Boolean = try {
        workspace = controller.commit(action)
        appError = ""
        true
    } catch (error: Exception) {
        appError = error.message ?: "Changes could not be saved. Please try again."
        false
    }
    fun updateCustomer(customer: Customer): Boolean = change { WorkspaceRules.updateCustomer(it, customer) }
    fun updateJob(job: JobRecord): Boolean = change { WorkspaceRules.saveJob(it, job) }
    fun updateAppointmentStatus(appointment: Appointment, status: String): Boolean =
        change { WorkspaceRules.appointmentStatus(it, appointment.id, status) }
    fun allowed(customer: Customer, campaign: Campaign): Boolean =
        CampaignWorkflow.contactReasons(workspace, campaign, customer).isEmpty()
    fun openJobs() { selectedJobId = ""; page = "jobs" }

    val current = campaigns.firstOrNull { it.id == selectedId }
    val backTarget = AppNavigation.backTarget(page)
    BackHandler(enabled = backTarget != null) {
        backTarget?.let { page = it }
    }

    RouteAppShell(
        page = page,
        title = AppNavigation.title(page, current?.title),
        openRequests = bookingRequests.count { it.status in setOf("NEW", "CONTACTED") },
        onNavigate = { if (it == "jobs") selectedJobId = ""; page = it },
        onBack = { backTarget?.let { page = it } },
        onAdd = when (page) {
            "customers" -> ({ page = "new_customer" })
            "campaigns" -> ({ page = "new_campaign" })
            else -> null
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
            if (appError.isNotBlank()) Text(appError, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            when (page) {
                "home" -> DashboardScreen(
                    state = workspace,
                    onCustomers = { page = "customers" },
                    onJobs = { openJobs() },
                    onSchedule = { page = "schedule" },
                    onRequests = { page = "requests" },
                    onCampaigns = { page = "campaigns" },
                    onBusiness = { page = "business" },
                    onMap = { page = "map" },
                    onOpportunity = { zip, service ->
                        campaignZipFromMap = zip; campaignServiceFromMap = service; page = "new_campaign"
                    },
                    onCloud = { page = "cloud" },
                    onInsights = { page = "insights" },
                    onExport = { backupAction = "export"; backupPassword = "" },
                    onImport = { backupAction = "import"; backupPassword = "" },
                    onLegacyImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    backupBusy = backupBusy, backupMessage = lastBackupMessage)
                "more" -> MoreScreen(
                    onMap = { page = "map" },
                    onRequests = { page = "requests" },
                    onCampaigns = { page = "campaigns" },
                    onBusiness = { page = "business" },
                    onBookingPage = { page = "booking_page" },
                    onBeta = { page = "beta" },
                    onCloud = { page = "cloud" },
                    onInsights = { page = "insights" },
                    onAlerts = { page = "alerts" })
                "insights" -> BusinessInsightsScreen(
                    customers = customers.toList(),
                    appointments = appointments.toList(),
                    jobs = jobs.toList())
                "alerts" -> BusinessAlertsScreen()
                "cloud" -> CloudScreen(store = store, onRestoreComplete = {
                    reloadRecords()
                    appError = ""
                    lastBackupMessage = "Cloud backup restored. Local data reloaded."
                })
                "beta" -> BetaReadinessScreen(
                    customers = customers.toList(), appointments = appointments.toList(),
                    jobs = jobs.toList(), requests = bookingRequests.toList(),
                    campaigns = campaigns.toList(), business = business,
                    appLocked = launchLock, lastFullBackup = lastFullBackup,
                    onBusiness = { page = "business" },
                    onSchedule = { page = "schedule" },
                    onJobs = { openJobs() },
                    onRequests = { page = "requests" },
                    onBackup = { backupAction = "export"; backupPassword = "" })
                "booking_page" -> BookingPageScreen(
                    profile = business)
                "requests" -> RequestsScreen(
                    requests = bookingRequests.toList(),
                    appointments = appointments.toList(), errorMessage = appError,
                    onAdd = { request -> change { state ->
                        require(BookingRules.valid(request)) { "Check the request details." }
                        state.copy(requests = state.requests + request)
                    } },
                    onStatus = { request, status -> change { state ->
                        val currentRequest = state.requests.firstOrNull { it.id == request.id }
                        require(currentRequest?.status in setOf("NEW", "CONTACTED") &&
                            status in setOf("CONTACTED", "DECLINED")) { "This request was already resolved." }
                        state.copy(requests = state.requests.map { if (it.id == request.id) it.copy(status = status) else it })
                    } },
                    onBook = { incoming -> change { WorkspaceRules.confirmRequest(it, incoming) } })
                "customers" -> CustomerScreen(customers.toList(),
                    onAdd = { page = "new_customer" },
                    onEdit = { editingCustomerId = it; page = "edit_customer" },
                    onHistory = { editingCustomerId = it; page = "customer_history" },
                    onOptOut = { id -> customers.firstOrNull { it.id == id }?.let { updateCustomer(it.copy(optedOut = true)) } },
                    onIssue = { id -> customers.firstOrNull { it.id == id }?.let { updateCustomer(it.copy(issueOpen = !it.issueOpen)) } },
                    onBooking = { id -> customers.firstOrNull { it.id == id }?.let { updateCustomer(it.copy(futureBooked = !it.futureBooked)) } })
                "customer_history" ->
                    customers.firstOrNull { it.id == editingCustomerId }?.let { customer ->
                        CustomerHistoryScreen(customer, appointments.toList(), jobs.toList(),
                            onEdit = { page = "edit_customer" },
                            onSchedule = { page = "schedule" })
                    }
                "new_customer" -> NewCustomerScreen(onSave = {
                    if (updateCustomer(it)) page = "customers"
                })
                "edit_customer" -> customers.firstOrNull { it.id == editingCustomerId }?.let { customer ->
                    CustomerEditor(customer, onSave = {
                        if (updateCustomer(it)) page = "customers"
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
                    onSave = { profile -> change { it.copy(business = profile) } },
                    onAddCustomers = { list -> change { state ->
                        val known = state.customers.map { CampaignRules.digits(it.phone) }.toMutableSet()
                        list.filter { !it.consent && known.add(CampaignRules.digits(it.phone)) }
                            .fold(state) { next, customer -> WorkspaceRules.updateCustomer(next, customer) }
                    } },
                    onAppLock = { enabled ->
                        runCatching { store.setAppLockEnabled(enabled) }
                            .onSuccess { launchLock = enabled }
                            .onFailure { appError = it.message ?: "Could not update device lock." }
                    })
                "jobs" -> JobsScreen(appointments.toList(), customers.toList(), jobs.toList(),
                    business = business, initialAppointmentId = selectedJobId,
                    onSaveJob = { updateJob(it) }, onOpenSchedule = { page = "schedule" })
                "schedule" -> ScheduleScreen(customers.toList(), appointments.toList(), errorMessage = appError,
                    onNew = { appointment -> change { WorkspaceRules.addAppointments(it, listOf(appointment)) } },
                    onRepeat = { series -> change { WorkspaceRules.addAppointments(it, series) } },
                    onStatus = { a, status -> updateAppointmentStatus(a, status) },
                    onReschedule = { revised -> change { WorkspaceRules.reschedule(it, revised) } },
                    onRemind = { appointment ->
                        val currentAppointment = appointments.firstOrNull { it.id == appointment.id }
                        val customer = customers.firstOrNull { it.id == currentAppointment?.customerId }
                        if (currentAppointment != null && customer != null &&
                            ReminderRules.canDraft(currentAppointment, customer)) {
                            openSms(customer.phone, ReminderRules.draft(
                                currentAppointment, customer, business.name))
                        } else appError = "Reminder blocked: check customer preferences and booking date."
                    },
                    onMarkReminded = { appointment -> change { state ->
                        val currentAppointment = state.appointments.firstOrNull { it.id == appointment.id }
                        val customer = state.customers.firstOrNull { it.id == currentAppointment?.customerId }
                        require(currentAppointment != null && customer != null &&
                            ReminderRules.canDraft(currentAppointment, customer)) { "This reminder is no longer available." }
                        state.copy(appointments = state.appointments.map { if (it.id == appointment.id)
                            it.copy(lastReminderAt = LocalDate.now().toString()) else it })
                    } })

                "campaigns" -> CampaignScreen(workspace,
                    onNew = {
                        campaignZipFromMap = ""; campaignServiceFromMap = "House pressure washing"
                        page = "new_campaign"
                    }, onSelect = { selectedId = it; page = "campaign_detail" })
                "new_campaign" -> NewCampaignScreen(workspace,
                    campaignZipFromMap, campaignServiceFromMap, onSave = { campaign ->
                        if (change { CampaignWorkflow.create(it, campaign) }) {
                            selectedId = campaign.id; page = "campaign_detail"
                        }
                    })
                "campaign_detail" -> if (current == null) {
                    RouteEmptyState(Icons.Default.Campaign, "Campaign unavailable", "Return to Campaigns and choose another offer.")
                } else CampaignDetailScreen(
                    state = workspace, campaign = current,
                    onEdit = { r -> editorCustomer = current.id; editorRecipient = r.customerId; messageDraft = r.message },
                    onSend = { r ->
                        val customer = customers.firstOrNull { it.id == r.customerId }
                        val stored = current.recipients.firstOrNull { it.customerId == r.customerId }
                        if (customer != null && stored?.status == "APPROVED" && allowed(customer, current) && stored.message.isNotBlank())
                            openSms(customer.phone, stored.message)
                        else appError = "Opening SMS is blocked: review current eligibility and approval."
                    },
                    onMarkSent = { r -> change { CampaignWorkflow.recordSent(it, current.id, r.customerId) } },
                    onStatus = { r, status -> change { CampaignWorkflow.status(it, current.id, r.customerId, status) } },
                    onOpenJob = { r ->
                        selectedJobId = CampaignWorkflow.linkedAppointment(workspace, current.id, r.customerId)?.id.orEmpty()
                        page = "jobs"
                    },
                    onMoney = { r, mode ->
                        moneyDialog = r.customerId to mode
                        val appointment = CampaignWorkflow.linkedAppointment(workspace, current.id, r.customerId)
                        val job = jobs.firstOrNull { it.appointmentId == appointment?.id }
                        moneyInput = String.format(Locale.US, "%.2f", if (mode == "book") current.price
                            else job?.let(JobMath::balance) ?: r.bookedAmount)
                        bookingTime = "09:00"; bookingDuration = "60"
                    })
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
                    Text("Review this message before approving it. Keep the STOP opt-out instruction. Nothing is sent from this screen.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = messageDraft, onValueChange = { messageDraft = it },
                        label = { Text("SMS text") }, minLines = 5, maxLines = 9)
                }
            },
            confirmButton = {
                TextButton(enabled = c != null && r != null && customer != null && allowed(customer, c) &&
                    messageDraft.isNotBlank() && messageDraft.length <= 2000 &&
                    Regex("\\bSTOP\\b", RegexOption.IGNORE_CASE).containsMatchIn(messageDraft),
                    onClick = {
                        if (c != null && r != null && customer != null && allowed(customer, c)) {
                            if (change { CampaignWorkflow.approve(it, c.id, r.customerId, messageDraft) }) {
                                editorRecipient = null; editorCustomer = null
                            }
                        }
                    }) { Text("Approve") }
            },
            dismissButton = { TextButton(onClick = { editorRecipient = null; editorCustomer = null }) { Text("Cancel") } }
        )
    }

    if (moneyDialog != null) {
        val (recipientId, mode) = moneyDialog!!
        val campaign = current
        val amount = moneyInput.toDoubleOrNull() ?: -1.0
        val proposal = runCatching {
            require(campaign != null) { "Campaign is missing." }
            if (mode == "book") CampaignWorkflow.book(workspace, campaign.id, recipientId, amount,
                bookingTime, bookingDuration.toIntOrNull() ?: 0)
            else CampaignWorkflow.recordPayment(workspace, campaign.id, recipientId, amount)
        }
        AlertDialog(onDismissRequest = { moneyDialog = null },
            title = { Text(if (mode == "book") "Confirm appointment" else "Record payment") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (mode == "book") "Enter the agreed amount and time. The appointment is saved together with this campaign booking."
                        else "Record money already received. The same receipt appears in Jobs and your financial report.")
                    OutlinedTextField(moneyInput, { moneyInput = it }, label = { Text("Amount received / agreed (USD)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (mode == "book") {
                        Text("Service date: ${campaign?.visitDate.orEmpty()}")
                        OutlinedTextField(bookingTime, { bookingTime = it }, label = { Text("Start time HH:mm") }, singleLine = true)
                        OutlinedTextField(bookingDuration, { bookingDuration = it }, label = { Text("Duration in minutes") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    proposal.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(enabled = proposal.isSuccess, onClick = {
                if (campaign != null && change {
                    if (mode == "book") CampaignWorkflow.book(it, campaign.id, recipientId, amount,
                        bookingTime, bookingDuration.toIntOrNull() ?: 0)
                    else CampaignWorkflow.recordPayment(it, campaign.id, recipientId, amount)
                }) moneyDialog = null
            }) { Text(if (mode == "book") "Save appointment" else "Save receipt") } },
            dismissButton = { TextButton(onClick = { moneyDialog = null }) { Text("Cancel") } })
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
                        reloadRecords()
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
private fun NewCustomerScreen(onSave: (Customer) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var zip by rememberSaveable { mutableStateOf("") }
    var service by rememberSaveable { mutableStateOf("House pressure washing") }
    var address by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("220") }
    var price by rememberSaveable { mutableStateOf("150") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var evidence by rememberSaveable { mutableStateOf("") }
    val good = name.isNotBlank() && zip.matches(Regex("\\d{5}")) &&
        CampaignRules.digits(phone).length in 10..15 && service.isNotBlank() &&
        (days.toLongOrNull() ?: -1) in 0..36500 &&
        WorkspaceRules.validMoney(price.toDoubleOrNull() ?: -1.0) &&
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
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text,
                  onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth())
}

private fun usd(value: Double): String = "$" + String.format(Locale.US, "%,.2f", value)

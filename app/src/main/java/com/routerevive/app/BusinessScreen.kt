package com.routerevive.app

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import java.util.Locale

@Composable
fun BusinessScreen(profile: BusinessProfile, customers: List<Customer>,
                   jobs: List<JobRecord>, appointments: List<Appointment>,
                   campaigns: List<Campaign>, appLock: Boolean,
                   onSave: (BusinessProfile) -> Unit,
                   onAddCustomers: (List<Customer>) -> Unit,
                   onAppLock: (Boolean) -> Unit) {
    val context = LocalContext.current
    val secure = remember {
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
    }
    var section by remember { mutableStateOf("Profile") }
    var name by remember(profile) { mutableStateOf(profile.name) }
    var phone by remember(profile) { mutableStateOf(profile.phone) }
    var email by remember(profile) { mutableStateOf(profile.email) }
    var address by remember(profile) { mutableStateOf(profile.address) }
    var website by remember(profile) { mutableStateOf(profile.website) }
    var terms by remember(profile) { mutableStateOf(profile.paymentTerms) }
    var logo by remember(profile) { mutableStateOf(profile.logoFile) }
    var message by remember { mutableStateOf("") }
    var proposedImport by remember { mutableStateOf<CsvImportResult?>(null) }
    val chooseLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            logo = JobMedia.importPhoto(context, uri)
        }.onFailure { message = "Logo not imported: " + it.message }
    }
    val selectCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val csv = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArrayOutputStream()
                val bytes = ByteArray(8192)
                while (true) {
                    val n = stream.read(bytes)
                    if (n < 0) break
                    require(buffer.size() + n <= 2_000_000) { "CSV file exceeds 2 MB." }
                    buffer.write(bytes, 0, n)
                }
                buffer.toString("UTF-8")
            } ?: error("Unable to open CSV")
            BusinessLogic.importCsv(csv, customers)
        }.onSuccess { proposedImport = it; message = "" }
            .onFailure { message = "Import preview failed: " + it.message }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("BUSINESS ESSENTIALS", color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Profile", "Finances", "Import").forEach { item ->
                FilterChip(selected = section == item, onClick = { section = item },
                    label = { Text(item, fontSize = 12.sp) })
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(11.dp)) {
            if (section == "Profile") {
                Text("Your business identity", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Used by default on estimates and invoices.",
                    color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                ProfileField("Business name", name) { name = it }
                ProfileField("Business phone", phone) { phone = it }
                ProfileField("Business email", email) { email = it }
                ProfileField("Business address", address) { address = it }
                ProfileField("Website", website) { website = it }
                OutlinedTextField(value = terms, onValueChange = { terms = it },
                    label = { Text("Invoice payment terms (optional)") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
                val bitmap = remember(logo) { JobMedia.bitmap(context, logo) }
                if (bitmap != null) Image(bitmap.asImageBitmap(),
                    contentDescription = "Business logo",
                    modifier = Modifier.fillMaxWidth().height(115.dp),
                    contentScale = ContentScale.Fit)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { chooseLogo.launch("image/*") }) {
                        Text("Choose logo")
                    }
                    if (logo.isNotBlank()) TextButton(onClick = { logo = "" }) {
                        Text("Remove")
                    }
                }
                Text("Logos are private app files, included in encrypted full backups.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                val valid = name.length <= 120 && phone.length <= 40 &&
                    email.length <= 150 && address.length <= 250 &&
                    website.length <= 180 && terms.length <= 400
                Button(enabled = valid, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSave(BusinessProfile(name.trim(), phone.trim(), email.trim(),
                            address.trim(), website.trim(), terms.trim(), logo, profile.workspaceId))
                        message = "Business profile saved."
                    }) { Text("Save business profile") }
                HorizontalDivider()
                Text("App launch lock", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Optional: require your phone's PIN, password or pattern when starting RouteRevive. This setting stays on this phone.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = appLock, enabled = secure || appLock,
                        onCheckedChange = { onAppLock(it) })
                    Spacer(Modifier.width(10.dp))
                    Text(if (appLock) "Require device unlock on launch" else
                        if (secure) "Enable launch lock" else "Set up a screen lock first",
                        fontSize = 13.sp)
                }
                Text("Records are encrypted at rest with an Android Keystore key. Keep an encrypted backup: uninstalling destroys that local key.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            } else if (section == "Finances") {
                val report = BusinessLogic.financials(jobs, appointments)
                Text("Financial overview", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Recorded job payments only. Campaign income is shown separately, not added twice.",
                    color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                FinancialTile("Total invoiced", report.billed)
                FinancialTile("Payments received", report.received)
                FinancialTile("Outstanding invoices", report.outstanding)
                FinancialTile("Overdue invoices", report.overdue)
                FinancialTile("Received this month", report.monthReceived)
                Text("Issued invoices: " + report.issuedInvoices +
                     " · Paid invoices: " + report.paidInvoices, fontSize = 13.sp)
                Text("Completed appointments: " + report.completedJobs, fontSize = 13.sp)
                HorizontalDivider()
                val legacy = campaigns.sumOf { campaign -> campaign.recipients.sumOf { it.paidAmount } }
                FinancialTile("Campaign payments (legacy tally)", legacy)
                Text("Campaign totals can describe the same money as a job payment. Do not add these totals together. RouteRevive does not connect to a bank or calculate taxes.",
                    color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            } else {
                Text("Import customer CSV", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Import customers with name, phone and ZIP columns. Optional: service, lastServiceDate, address and notes. Limit: 1,000 rows.",
                    fontSize = 13.sp)
                Text("Every imported customer has promotional SMS permission OFF, regardless of data in the CSV.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = {
                    proposedImport = null
                    selectCsv.launch(arrayOf("text/*", "application/csv",
                        "application/vnd.ms-excel", "application/octet-stream"))
                }) { Text("Select CSV file") }
                proposedImport?.let { preview ->
                    Text("Preview: " + preview.customers.size + " valid new customers, " +
                        preview.skipped + " invalid/duplicate rows skipped.",
                        fontWeight = FontWeight.Bold)
                    preview.customers.take(8).forEach {
                        Text("• " + it.name + " · " + it.zip + " · " + it.service,
                            fontSize = 12.sp)
                    }
                    Button(enabled = preview.customers.isNotEmpty(), onClick = {
                        onAddCustomers(preview.customers)
                        message = "Imported " + preview.customers.size +
                            " customers with SMS permission disabled."
                        proposedImport = null
                    }) { Text("Confirm customer import") }
                    TextButton(onClick = { proposedImport = null }) { Text("Cancel import") }
                }
                Text("Check customer details before sending anything. Duplicate phone numbers are skipped; ZIPs must contain five digits.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            }
            if (message.isNotBlank()) Text(message, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FinancialTile(label: String, amount: Double) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Text("$" + String.format(Locale.US, "%,.2f", amount),
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ProfileField(title: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(title) },
        singleLine = true, modifier = Modifier.fillMaxWidth())
}

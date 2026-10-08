package com.routerevive.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** On-device checks: report contains no names, phone numbers, or addresses. */
@Composable
fun BetaReadinessScreen(
    customers: List<Customer>,
    appointments: List<Appointment>,
    jobs: List<JobRecord>,
    requests: List<BookingRequest>,
    campaigns: List<Campaign>,
    business: BusinessProfile,
    appLocked: Boolean,
    lastFullBackup: String,
    onBusiness: () -> Unit,
    onSchedule: () -> Unit,
    onJobs: () -> Unit,
    onRequests: () -> Unit,
    onBackup: () -> Unit
) {
    val context = LocalContext.current
    val report = BetaIntegrity.audit(customers, appointments, jobs, requests,
        campaigns, business) { name -> JobMedia.photoFile(context, name)?.isFile == true }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("BETA READINESS", fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("RouteRevive 0.1.0", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("Private local preflight. No results are uploaded or sent to support.",
            color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Local data health", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("\${report.customerCount} customers · \${report.appointmentCount} appointments · " +
                    "\${report.jobCount} jobs · \${report.requestCount} requests",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                if (report.healthy) {
                    Text("No issues detected by these checks.",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold)
                } else {
                    Text("\${report.issues.size} potential issue categories",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold)
                    report.issues.forEach { Text("• " + it, fontSize = 12.sp) }
                    Text("This report never deletes or changes customer information.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Before inviting beta testers", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                BetaCheck("Company identity and invoice contact saved",
                    business.name.isNotBlank() &&
                        (business.phone.isNotBlank() || business.email.isNotBlank()))
                BetaCheck("Encrypted full backup exported", lastFullBackup.isNotBlank())
                BetaCheck("No local record-health warnings", report.healthy)
                BetaCheck("Device launch lock enabled (optional)", appLocked)
                Text("Last full backup: " +
                    lastFullBackup.ifBlank { "Not recorded on this device" },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                OutlinedButton(onClick = onBusiness, modifier = Modifier.fillMaxWidth()) {
                    Text("Check business profile")
                }
                Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                    Text("Create encrypted full backup")
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Complete-workflow smoke test", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Use only fictional data during initial testing:",
                    color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                Text("1. Customer request → confirm appointment\n" +
                    "2. Schedule → map → navigation\n" +
                    "3. Job → before/after photos\n" +
                    "4. Invoice PDF → deposit → final payment\n" +
                    "5. Encrypted backup → restore on a spare test device",
                    fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRequests, modifier = Modifier.weight(1f)) {
                        Text("Requests")
                    }
                    OutlinedButton(onClick = onSchedule, modifier = Modifier.weight(1f)) {
                        Text("Schedule")
                    }
                }
                OutlinedButton(onClick = onJobs, modifier = Modifier.fillMaxWidth()) {
                    Text("Jobs and payments")
                }
            }
        }
        HorizontalDivider()
        Text("Privacy at a glance", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Customer records, appointment and financial data are encrypted using " +
            "Android Keystore. Photos are app-private files. The full encrypted " +
            "backup includes those photos, and you choose where it is saved.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        Text("Map tiles use OpenStreetMap services; locating an address uses the " +
            "phone's geocoder, and directions open an external maps app. SMS and " +
            "email drafts are sent only when the owner or customer acts. " +
            "There is no cloud customer database, advertising SDK, or " +
            "automatic SMS sending in this beta.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        Text("Google Play submission still requires an accurate published privacy " +
            "policy, Data safety and app access forms, production build review " +
            "and real-device tests. This checklist is not a certification.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun BetaCheck(label: String, checked: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(if (checked) "✓" else "○",
            color = if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold)
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

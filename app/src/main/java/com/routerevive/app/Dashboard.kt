package com.routerevive.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale

/** Dashboard actions use short two-column labels, not full-width oversized pills. */
@Composable
fun DashboardScreen(
    customers: List<Customer>, campaigns: List<Campaign>,
    appointments: List<Appointment>, jobs: List<JobRecord>,
    requests: List<BookingRequest>,
    business: BusinessProfile,
    onCustomers: () -> Unit, onJobs: () -> Unit,
    onSchedule: () -> Unit, onRequests: () -> Unit,
    onCampaigns: () -> Unit, onBusiness: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit,
    onLegacyImport: () -> Unit,
    backupBusy: Boolean, backupMessage: String
) {
    val now = LocalDate.now()
    val openRequests = requests.count { it.status in setOf("NEW", "CONTACTED") }
    val activeJobs = appointments.filter {
        it.status in setOf("SCHEDULED", "IN_PROGRESS") &&
            runCatching { !LocalDate.parse(it.date).isBefore(now) }.getOrDefault(false)
    }.sortedWith(compareBy<Appointment> { it.date }.thenBy { it.time })
    val income = BusinessLogic.financials(jobs, appointments, now)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (business.name.isBlank()) "Your business at a glance" else business.name,
            fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardMetric("Customers", customers.size.toString(), Modifier.weight(1f))
            DashboardMetric("Open requests", openRequests.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardMetric("Upcoming jobs", activeJobs.size.toString(), Modifier.weight(1f))
            DashboardMetric("Unpaid invoices", income.issuedInvoices.minus(income.paidInvoices)
                .coerceAtLeast(0).toString(), Modifier.weight(1f))
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Quick actions", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DashboardAction("Requests", onRequests, Modifier.weight(1f))
                    DashboardAction("Schedule", onSchedule, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DashboardAction("Jobs", onJobs, Modifier.weight(1f))
                    DashboardAction("Customers", onCustomers, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DashboardAction("Campaigns", onCampaigns, Modifier.weight(1f))
                    DashboardAction("Business", onBusiness, Modifier.weight(1f))
                }
            }
        }
        if (openRequests > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$openRequests request(s) need attention", fontWeight = FontWeight.Bold)
                        Text("Review before confirming a time",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    TextButton(onClick = onRequests) { Text("Review") }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Upcoming schedule", fontWeight = FontWeight.Bold)
                if (activeJobs.isEmpty())
                    Text("No upcoming jobs. Check Schedule or Requests.",
                        color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                activeJobs.take(3).forEach { a ->
                    val c = customers.firstOrNull { it.id == a.customerId }
                    Text(a.date + "  " + a.time + "  ·  " + (c?.name ?: a.service),
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onSchedule) { Text("Open schedule") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Payments overview", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Received this month", fontSize = 12.sp)
                    Text("$" + String.format(Locale.US, "%,.2f", income.monthReceived),
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Outstanding invoices", fontSize = 12.sp)
                    Text("$" + String.format(Locale.US, "%,.2f", income.outstanding),
                        fontWeight = FontWeight.Bold)
                }
                Text("Job payments only; campaign totals are not added again.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                TextButton(onClick = onBusiness) { Text("Full financial report") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Secure full backups", fontWeight = FontWeight.Bold)
                Text("Save or restore records and photos with a password. Store the password securely.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                OutlinedButton(enabled = !backupBusy, modifier = Modifier.fillMaxWidth(),
                    onClick = onExport) { Text("Create encrypted backup") }
                OutlinedButton(enabled = !backupBusy, modifier = Modifier.fillMaxWidth(),
                    onClick = onImport) { Text("Restore full backup") }
                TextButton(enabled = !backupBusy, onClick = onLegacyImport) {
                    Text("Import older JSON")
                }
                if (backupBusy) CircularProgressIndicator(Modifier.size(26.dp))
                if (backupMessage.isNotBlank()) Text(backupMessage, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("v0.0.8 · Local-first · SMS reminders require manual sending",
            color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DashboardMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 23.sp,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DashboardAction(label: String, action: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = action, modifier = modifier.heightIn(min = 50.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 7.dp)) {
        Text(label, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

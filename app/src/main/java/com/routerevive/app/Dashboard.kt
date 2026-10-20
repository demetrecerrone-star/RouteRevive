package com.routerevive.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun DashboardScreen(
    state: WorkspaceSnapshot,
    onCustomers: () -> Unit, onJobs: () -> Unit,
    onSchedule: () -> Unit, onRequests: () -> Unit,
    onCampaigns: () -> Unit, onBusiness: () -> Unit, onMap: () -> Unit,
    onOpportunity: (String, String) -> Unit,
    onCloud: () -> Unit, onInsights: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, onLegacyImport: () -> Unit,
    backupBusy: Boolean, backupMessage: String
) {
    val customers = state.customers
    val appointments = state.appointments
    val jobs = state.jobs
    val requests = state.requests
    val business = state.business
    val now = LocalDate.now()
    val opportunities = remember(state, now) { ReactivationRules.groups(state, now) }
    val openRequests = requests.count { it.status in setOf("NEW", "CONTACTED") }
    val upcoming = appointments.filter {
        it.status in setOf("SCHEDULED", "IN_PROGRESS") &&
            runCatching { !LocalDate.parse(it.date).isBefore(now) }.getOrDefault(false)
    }.sortedWith(compareBy<Appointment> { it.date }.thenBy { it.time })
    val todayCount = upcoming.count { it.date == now.toString() }
    val income = BusinessLogic.financials(jobs, appointments, now)
    val context = LocalContext.current
    val cloudVault = remember(context) { CloudVault(context) }
    val health = CloudHealthRules.evaluate(
        cloudVault.automaticEnabled(), cloudVault.lastSyncStatus(),
        cloudVault.lastSyncTime(), cloudVault.autoHours())
    var backupExpanded by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = colors.primaryContainer)) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(
                listOf(colors.primaryContainer, colors.surface))).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                    style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Text(when (LocalTime.now().hour) {
                    in 5..11 -> "Good morning."
                    in 12..17 -> "Good afternoon."
                    else -> "Good evening."
                }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(business.name.ifBlank { "Let's get your business moving." },
                    style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer)
                Text("Your work, customers and follow-ups, all in one place.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Button(onClick = if (business.name.isBlank()) onBusiness else onSchedule,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                    Text(if (business.name.isBlank()) "Set up business profile" else "Open schedule")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }

        RouteCard {
            SectionHeading("Ready to reconnect", "Offers", onCampaigns)
            if (opportunities.isEmpty()) {
                Text(if (customers.isEmpty()) "Add past customers to find your next repeat booking."
                    else "No customers are eligible for a repeat-service offer right now.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Customers need documented permission, a due service and no open booking or other exclusions.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                TextButton(onClick = onCustomers) { Text("Review customers") }
            } else {
                Text("${opportunities.sumOf { it.customers.size }} eligible customers",
                    style = MaterialTheme.typography.titleMedium, color = colors.primary)
                opportunities.take(3).forEach { group ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("ZIP ${group.zip} · ${group.customers.size} ready",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(group.service, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        TextButton(onClick = { onOpportunity(group.zip, group.service) }) { Text("Build offer") }
                    }
                }
                Text("Every message stays a draft until you review it. You send it yourself.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }

        AdaptivePair(
            first = { DashboardMetric("Today's appointments", todayCount.toString(),
                Icons.Default.Today, onSchedule, it) },
            second = { DashboardMetric("Open requests", openRequests.toString(),
                Icons.Default.Inbox, onRequests, it) }
        )
        AdaptivePair(
            first = { DashboardMetric("Customers", customers.size.toString(),
                Icons.Default.People, onCustomers, it) },
            second = { DashboardMetric("Upcoming jobs", upcoming.size.toString(),
                Icons.Default.EventAvailable, onSchedule, it) }
        )

        if (openRequests > 0 || income.overdue > 0) {
            RouteCard {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureIcon(Icons.Default.NotificationsNone, colors.tertiary)
                    Text("A quick follow-up", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                if (openRequests > 0) {
                    Text("$openRequests ${if (openRequests == 1) "inquiry is" else "inquiries are"} waiting for review.",
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRequests) { Text("Review requests") }
                }
                if (income.overdue > 0) {
                    Text("${money(income.overdue)} in overdue invoice balances.",
                        color = colors.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onBusiness) { Text("Review invoices") }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading("Coming up", "Schedule", onSchedule)
            RouteCard {
                if (upcoming.isEmpty()) {
                    RouteEmptyState(Icons.Default.EventAvailable, "Room for your next booking",
                        "Add an appointment in Schedule or review a customer inquiry.",
                        action = "Open schedule", onAction = onSchedule)
                } else {
                    upcoming.take(3).forEachIndexed { index, appointment ->
                        if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                        val customer = customers.firstOrNull { it.id == appointment.customerId }
                        AppointmentPreview(appointment, customer?.name)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading("Business tools")
            AdaptivePair(
                first = { DashboardAction("Jobs & invoices", "Keep work on track",
                    Icons.Default.WorkOutline, onJobs, it) },
                second = { DashboardAction("Neighborhood map", "See your next route",
                    Icons.Default.Map, onMap, it) }
            )
            AdaptivePair(
                first = { DashboardAction("Campaigns", "Bring customers back",
                    Icons.Default.Campaign, onCampaigns, it) },
                second = { DashboardAction("Analytics", "See how you're doing",
                    Icons.Default.BarChart, onInsights, it) }
            )
        }

        RouteCard {
            SectionHeading("Payments", "Report", onBusiness)
            Text("Received this month", color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            Text(money(income.monthReceived), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = colors.primary)
            HorizontalDivider(color = colors.outlineVariant)
            AdaptivePair(
                first = { Column(it, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Outstanding", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                    Text(money(income.outstanding), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                } },
                second = { Column(it, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Unpaid invoices", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                    Text((income.issuedInvoices - income.paidInvoices).coerceAtLeast(0).toString(),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                } }
            )
            Text("From your recorded job payments and issued invoices.",
                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        RouteCard {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureIcon(Icons.Default.Shield)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your data, protected", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(health.title, style = MaterialTheme.typography.bodyMedium,
                        color = if (health.state in listOf(CloudHealthState.ACTION_REQUIRED,
                            CloudHealthState.STALE)) colors.error else colors.onSurfaceVariant)
                }
            }
            Text(health.detail, color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onCloud) {
                Icon(Icons.Default.CloudQueue, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Manage cloud backups")
            }
            HorizontalDivider(color = colors.outlineVariant)
            TextButton(onClick = { backupExpanded = !backupExpanded }) {
                Text(if (backupExpanded) "Hide backup & restore" else "Backup & restore options")
                Spacer(Modifier.width(6.dp))
                Icon(if (backupExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = backupExpanded || backupBusy) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Save records and photos in a password-encrypted file. Keep the password safe.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    OutlinedButton(enabled = !backupBusy, modifier = Modifier.fillMaxWidth(),
                        onClick = onExport) { Text("Create encrypted backup") }
                    OutlinedButton(enabled = !backupBusy, modifier = Modifier.fillMaxWidth(),
                        onClick = onImport) { Text("Restore full backup") }
                    TextButton(enabled = !backupBusy, onClick = onLegacyImport) { Text("Import older JSON") }
                    if (backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (backupMessage.isNotBlank()) Text(backupMessage,
                style = MaterialTheme.typography.bodySmall, color = colors.primary)
        }
        Text("Works offline · You review and send every customer message",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DashboardMetric(label: String, value: String, icon: ImageVector,
                            onClick: () -> Unit, modifier: Modifier) {
    Card(onClick = onClick, modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp))
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardAction(label: String, detail: String, icon: ImageVector,
                            action: () -> Unit, modifier: Modifier) {
    Card(onClick = action, modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureIcon(icon)
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppointmentPreview(appointment: Appointment, customerName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(appointment.date.let { date ->
            runCatching { LocalDate.parse(date).format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(date)
        } + "  ·  " + appointment.time,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(customerName ?: "Customer unavailable", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text(appointment.service, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun money(value: Double): String = "$" + String.format(Locale.US, "%,.2f", value)

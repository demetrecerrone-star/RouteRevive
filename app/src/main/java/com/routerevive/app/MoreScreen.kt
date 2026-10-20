package com.routerevive.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoreScreen(
    onMap: () -> Unit, onRequests: () -> Unit,
    onCampaigns: () -> Unit, onBusiness: () -> Unit,
    onBookingPage: () -> Unit, onBeta: () -> Unit,
    onCloud: () -> Unit, onInsights: () -> Unit, onAlerts: () -> Unit,
    onProfitPlanner: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("A place for everything behind the scenes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ToolGroup("Customers & growth") {
            MoreEntry(Icons.AutoMirrored.Filled.TrendingUp, "Profit planner", "Find nearby work that fits your time and margin", onProfitPlanner)
            ToolDivider()
            MoreEntry(Icons.Default.Inbox, "Booking requests", "Review inquiries and confirm a time", onRequests)
            ToolDivider()
            MoreEntry(Icons.Default.Campaign, "Campaigns", "Plan repeat-service offers", onCampaigns)
            ToolDivider()
            MoreEntry(Icons.Default.Map, "Neighborhood map", "Customer locations and route suggestions", onMap)
            ToolDivider()
            MoreEntry(Icons.Default.Web, "Booking request page", "Share a form that opens an SMS or email inquiry", onBookingPage)
        }
        ToolGroup("Run your business") {
            MoreEntry(Icons.Default.Storefront, "Business & finances", "Profile, billing, reports and CSV import", onBusiness)
            ToolDivider()
            MoreEntry(Icons.Default.BarChart, "Analytics", "Payment trends and customer activity", onInsights)
            ToolDivider()
            MoreEntry(Icons.Default.NotificationsNone, "Reminders & alerts", "Private notifications for work and payments", onAlerts)
        }
        ToolGroup("Data & support") {
            MoreEntry(Icons.Default.CloudQueue, "Private cloud backups", "Encrypted snapshots and restore options", onCloud)
            ToolDivider()
            MoreEntry(Icons.Default.VerifiedUser, "Readiness & privacy", "Check data health and privacy details", onBeta)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Encrypted on this device. Private cloud backups are optional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("RouteRevive · v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ToolGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun ToolDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun MoreEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FeatureIcon(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BookingPageScreen(profile: BusinessProfile) {
    val context = LocalContext.current
    var error by remember { mutableStateOf("") }
    val ready = CustomerBookingPage.ready(profile)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Customer booking request page", fontSize = 22.sp,
            fontWeight = FontWeight.Bold)
        Text("Create a branded, mobile-friendly HTML form with your business details. Share the HTML file as an attachment or publish it on a website you control.",
            color = MaterialTheme.colorScheme.secondary)
        if (!ready) {
            Text("First add a business name and a valid phone number or email in Business & finances → Profile.",
                color = MaterialTheme.colorScheme.error)
        } else {
            Text("Business: " + profile.name, fontWeight = FontWeight.Bold)
            Text("Requests go to " + (if (CampaignRules.digits(profile.phone).length in 10..15)
                "your business SMS number" else "your business email"))
        }
        Button(enabled = ready, onClick = {
            runCatching { CustomerBookingPage.share(context, profile) }
                .onFailure { error = it.message ?: "Could not share the HTML file" }
        }, modifier = Modifier.fillMaxWidth()) { Text("Share booking form (HTML)") }
        Text("How it works", fontWeight = FontWeight.Bold)
        Text("1. Customer opens the file in their browser.\n2. They enter contact details, service, and preferred time.\n3. Their device opens a prefilled SMS or email draft addressed to your business.\n4. You receive their message, open Requests in RouteRevive, and record/confirm the inquiry.",
            color = MaterialTheme.colorScheme.secondary)
        HorizontalDivider()
        Text("This is not a public booking URL and does not connect to live appointment availability. Customers must actually SEND the SMS or email, and you must confirm a date manually. Some apps may not open attached HTML forms; hosting the file on your own HTTPS website improves compatibility.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

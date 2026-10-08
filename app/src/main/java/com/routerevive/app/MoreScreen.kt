package com.routerevive.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MoreScreen(
    business: BusinessProfile,
    onMap: () -> Unit, onRequests: () -> Unit,
    onCampaigns: () -> Unit, onBusiness: () -> Unit,
    onBookingPage: () -> Unit,
    onBeta: () -> Unit,
    onCloud: () -> Unit,
    onInsights: () -> Unit,
    onAlerts: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("YOUR BUSINESS TOOLS", color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
        MoreEntry("Business analytics", "Payment trends, overdue invoices and customer activity",
            onClick = onInsights)
        MoreEntry("Reminders & alerts", "Private local notifications for upcoming work and payments",
            onClick = onAlerts)
        MoreEntry("Private cloud backups", "Business sign-in and password-encrypted cross-device snapshots",
            onClick = onCloud)
        MoreEntry("Beta readiness & privacy", "Local data health, release checklist and privacy details",
            onClick = onBeta)
        MoreEntry("Neighborhood map", "Customer pins and smart route suggestions",
            onClick = onMap)
        MoreEntry("Booking requests", "Review and confirm customer inquiries",
            onClick = onRequests)
        MoreEntry("Campaigns", "Repeat-service offers and consent controls",
            onClick = onCampaigns)
        MoreEntry("Booking request page", "Share an HTML form for customers to send inquiries",
            onClick = onBookingPage)
        MoreEntry("Business & finances", "Your logo, billing, reports and CSV import",
            onClick = onBusiness)
        HorizontalDivider()
        Text("Cloud & local data", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Supabase cloud backup is optional. Private encrypted snapshots can be uploaded manually or on a schedule. Importing cloud changes requires confirmation. No live multi-user edits, merging, or public booking server yet.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        Text("Workspace: " + business.workspaceId.take(12),
            color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        Text("Encrypted records stay on this device. Keep your password-protected full backup safe.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun MoreEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 25.sp)
        }
    }
}

@Composable
fun BookingPageScreen(profile: BusinessProfile, onBack: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf("") }
    val ready = CustomerBookingPage.ready(profile)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("← More tools") }
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

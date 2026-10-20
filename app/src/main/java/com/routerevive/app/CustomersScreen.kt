package com.routerevive.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerScreen(
    customers: List<Customer>, onAdd: () -> Unit,
    onEdit: (String) -> Unit, onHistory: (String) -> Unit,
    onOptOut: (String) -> Unit, onIssue: (String) -> Unit,
    onBooking: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val search = query.trim()
    val matches = customers.filter {
        it.name.contains(search, ignoreCase = true) || it.phone.contains(search) ||
            it.zip.contains(search) || it.service.contains(search, ignoreCase = true)
    }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    val colors = MaterialTheme.colorScheme

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (customers.isEmpty()) {
            item {
                RouteCard {
                    RouteEmptyState(Icons.Default.People, "Your customer list starts here",
                        "Add contact details and a service history to keep your next follow-up simple.",
                        action = "Add customer", onAction = onAdd)
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        placeholder = { Text("Name, phone, ZIP or service") },
                        label = { Text("Search customers") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, "Clear search")
                            }
                        }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = MaterialTheme.shapes.medium)
                    Text(if (search.isEmpty())
                        "${customers.size} ${if (customers.size == 1) "customer" else "customers"}"
                        else "${matches.size} of ${customers.size} customers",
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
            if (matches.isEmpty()) item {
                RouteCard {
                    RouteEmptyState(Icons.Default.SearchOff, "No customers found",
                        "Try a different name, phone number, ZIP code or service.",
                        action = "Clear search", onAction = { query = "" })
                }
            }
            items(matches, key = { it.id }) { customer ->
                RouteCard {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = MaterialTheme.shapes.small, color = colors.primaryContainer) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Text(customer.name.trim().split(Regex("\\s+")).take(2)
                                    .mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
                                    .uppercase(Locale.getDefault()),
                                    fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(customer.name, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("ZIP ${customer.zip}", style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant)
                        }
                    }
                    Text(customer.service, style = MaterialTheme.typography.bodyMedium)
                    Text("Last service · ${customer.lastService}", style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                    val documented = customer.consent && customer.consentEvidence.isNotBlank()
                    Text(when {
                        customer.optedOut -> "Opted out · promotional contact blocked"
                        documented -> "SMS marketing permission documented"
                        else -> "No SMS marketing permission"
                    }, color = when {
                        customer.optedOut -> colors.error
                        documented -> colors.primary
                        else -> colors.tertiary
                    }, style = MaterialTheme.typography.labelMedium)
                    if (customer.address.isNotBlank()) Text(customer.address,
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    if (customer.notes.isNotBlank()) Text(customer.notes,
                        style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    if (customer.issueOpen) Text("Unresolved issue · campaign blocked",
                        color = colors.tertiary, style = MaterialTheme.typography.bodySmall)
                    if (customer.futureBooked) Text("Upcoming appointment recorded",
                        style = MaterialTheme.typography.bodySmall, color = colors.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { onHistory(customer.id) }) {
                            Icon(Icons.Default.History, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("History")
                        }
                        OutlinedButton(onClick = { onEdit(customer.id) }) {
                            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text("Edit profile")
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onIssue(customer.id) }) {
                            Text(if (customer.issueOpen) "Resolve issue" else "Flag issue")
                        }
                        TextButton(onClick = { onBooking(customer.id) }) {
                            Text(if (customer.futureBooked) "Clear booked" else "Has booking")
                        }
                        if (!customer.optedOut) TextButton(onClick = { onOptOut(customer.id) }) {
                            Text("Record opt-out", color = colors.error)
                        }
                    }
                }
            }
        }
    }
}

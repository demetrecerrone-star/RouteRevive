package com.routerevive.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale

data class CustomerActivity(
    val date: LocalDate,
    val title: String,
    val detail: String,
    val id: String
)

/** Record-based chronology, not an edit-by-edit audit trail. */
object CustomerHistoryRules {
    fun events(
        customer: Customer, appointments: List<Appointment>,
        jobs: List<JobRecord>
    ): List<CustomerActivity> {
        val result = mutableListOf<CustomerActivity>()
        fun add(date: String, title: String, detail: String, id: String) {
            val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
            result += CustomerActivity(parsed, title, detail, id)
        }
        if (customer.lastService.isNotBlank()) {
            add(customer.lastService, "Previous service recorded", customer.service, "last-service")
        }
        val linked = appointments.filter { it.customerId == customer.id }
        val indexed = linked.associateBy { it.id }
        for (a in linked) {
            add(a.date, "Appointment: " + a.status.replace('_', ' '),
                a.time + " · " + a.service, "appointment-" + a.id)
            if (a.lastReminderAt.isNotBlank()) {
                add(a.lastReminderAt, "SMS reminder recorded",
                    "Marked sent by the business owner", "reminder-" + a.id)
            }
        }
        for (j in jobs.filter { it.customerId == customer.id && indexed.containsKey(it.appointmentId) }) {
            val appointment = indexed[j.appointmentId] ?: continue
            if (j.statusUpdatedAt.isNotBlank())
                add(j.statusUpdatedAt.take(10), "Job progress: " + JobProgress.label(j.status),
                    appointment.service, "progress-" + j.id)
            if (j.invoiceIssued)
                add(appointment.date, "Invoice on file",
                    "Balance: $" + String.format(Locale.US, "%,.2f", JobMath.balance(j)),
                    "invoice-" + j.id)
            for (payment in j.payments) {
                add(payment.date, "Payment recorded",
                    "$" + String.format(Locale.US, "%,.2f", payment.amount) +
                        " · " + payment.method, "payment-" + payment.id)
            }
            for (photo in j.photos) {
                add(photo.addedAt.take(10), photo.stage + " job photo",
                    appointment.service, "photo-" + photo.filename)
            }
        }
        return result.sortedWith(compareByDescending<CustomerActivity> { it.date }.thenBy { it.id })
    }
}

@Composable
fun CustomerHistoryScreen(
    customer: Customer,
    appointments: List<Appointment>, jobs: List<JobRecord>,
    onEdit: () -> Unit, onSchedule: () -> Unit
) {
    val events = CustomerHistoryRules.events(customer, appointments, jobs)
    val linked = appointments.filter { it.customerId == customer.id }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(customer.name, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(customer.service + " · " + customer.zip, color = MaterialTheme.colorScheme.secondary)
        if (customer.address.isNotBlank()) Text(customer.address, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) { Text("Edit customer") }
            OutlinedButton(onClick = onSchedule) { Text("Open schedule") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryMetric("Appointments", linked.size.toString(), Modifier.weight(1f))
            HistoryMetric("Recorded payments", jobs.filter { it.customerId == customer.id }
                .sumOf { it.payments.size }.toString(), Modifier.weight(1f))
        }
        Text("Customer activity", fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        Text("A timeline from saved service records, bookings, payments and photos. " +
            "It is not a complete history of every profile edit.",
            color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
        if (events.isEmpty()) Text("No service history yet.",
            color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(vertical = 12.dp)) {
            items(events, key = { it.id }) { event ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(event.date.toString(), color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp)
                        Text(event.title, fontWeight = FontWeight.Bold)
                        Text(event.detail, color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMetric(title: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(10.dp)) {
            Text(title, color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
            Text(value, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold, fontSize = 19.sp)
        }
    }
}

package com.routerevive.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

data class IncomeMonth(val month: YearMonth, val collected: Double)

data class BusinessInsights(
    val income: List<IncomeMonth>,
    val totalCollected: Double,
    val invoiced: Double,
    val outstanding: Double,
    val overdueAmount: Double,
    val overdueCount: Int,
    val jobs: Int,
    val customers: Int,
    val returningCustomers: Int,
    val completedAppointments: Int
)

/** Uses actual recorded job payments, not campaign projections or expected appointments. */
object InsightsRules {
    fun calculate(
        jobs: List<JobRecord>, appointments: List<Appointment>, customers: List<Customer>,
        today: LocalDate = LocalDate.now(), months: Int = 6
    ): BusinessInsights {
        require(months in 1..24)
        val current = YearMonth.from(today)
        val range = (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }
        val payments = jobs.flatMap { it.payments }.mapNotNull { payment ->
            val date = runCatching { LocalDate.parse(payment.date) }.getOrNull()
            val amount = payment.amount
            if (date == null || !amount.isFinite() || amount < 0) null
            else date to amount
        }
        val income = range.map { month ->
            IncomeMonth(month, cents(payments.filter { YearMonth.from(it.first) == month }
                .sumOf { it.second }))
        }
        val issued = jobs.filter { it.invoiceIssued }
        val validAppointments = appointments.filter { it.status !in setOf("CANCELLED", "NO_SHOW") }
        val repeating = validAppointments.groupingBy { it.customerId }.eachCount()
        return BusinessInsights(
            income = income,
            totalCollected = cents(payments.sumOf { it.second }),
            invoiced = cents(issued.sumOf { JobMath.subtotal(it) }),
            outstanding = cents(issued.sumOf { JobMath.balance(it) }),
            overdueAmount = cents(issued.filter { JobMath.isOverdue(it, today) }
                .sumOf { JobMath.balance(it) }),
            overdueCount = issued.count { JobMath.isOverdue(it, today) },
            jobs = jobs.size,
            customers = customers.size,
            returningCustomers = customers.count { (repeating[it.id] ?: 0) >= 2 },
            completedAppointments = validAppointments.count {
                it.status == "COMPLETED" &&
                    runCatching { YearMonth.from(LocalDate.parse(it.date)) == current }
                        .getOrDefault(false)
            }
        )
    }

    private fun cents(amount: Double): Double = kotlin.math.round(amount * 100.0) / 100.0
}

@Composable
fun BusinessInsightsScreen(
    customers: List<Customer>, appointments: List<Appointment>, jobs: List<JobRecord>
) {
    var months by remember { mutableIntStateOf(6) }
    val report = remember(customers, appointments, jobs, months) {
        InsightsRules.calculate(jobs, appointments, customers, months = months)
    }
    val currency = { value: Double -> "$" + String.format(Locale.US, "%,.2f", value) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("BUSINESS INTELLIGENCE", fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("Revenue & operations", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Private, on-device reporting based on recorded jobs and payments. " +
            "No forecast or campaign estimates are treated as collected revenue.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightsTile("All-time received", currency(report.totalCollected), Modifier.weight(1f))
            InsightsTile("Unpaid invoices", currency(report.outstanding), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightsTile("Overdue", currency(report.overdueAmount), Modifier.weight(1f))
            InsightsTile("Issued invoice value", currency(report.invoiced), Modifier.weight(1f))
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Collected revenue by month", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = months == 6, onClick = { months = 6 },
                        label = { Text("6 months") })
                    FilterChip(selected = months == 12, onClick = { months = 12 },
                        label = { Text("12 months") })
                }
                val maximum = report.income.maxOfOrNull { it.collected }?.coerceAtLeast(1.0) ?: 1.0
                report.income.forEach { entry ->
                    val portion = (entry.collected / maximum).toFloat().coerceIn(0f, 1f)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.month.toString(), fontSize = 12.sp)
                        Text(currency(entry.collected), fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(
                        progress = { portion }, modifier = Modifier.fillMaxWidth().height(9.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                if (report.income.all { it.collected == 0.0 })
                    Text("No recorded payments in this period yet.",
                        color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Operations snapshot", fontWeight = FontWeight.Bold)
                InsightsLine("Total customers", report.customers.toString())
                InsightsLine("Customers with 2+ bookings", report.returningCustomers.toString())
                InsightsLine("Job records", report.jobs.toString())
                InsightsLine("Completed appointments this month",
                    report.completedAppointments.toString())
                InsightsLine("Overdue invoices", report.overdueCount.toString())
            }
        }
        Text("Paid totals use the payment dates stored in each job. Invoiced and outstanding " +
            "values count issued invoices only. Repeat customers are based on non-cancelled " +
            "appointment history, not marketing engagement. Reports include example data " +
            "if you have not removed the demo records.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun InsightsTile(label: String, amount: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            Text(amount, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun InsightsLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

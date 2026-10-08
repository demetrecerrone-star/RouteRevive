package com.routerevive.app

import java.time.LocalDate
import java.util.Locale

data class BusinessProfile(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val website: String = "",
    val paymentTerms: String = "",
    val logoFile: String = "",
    val workspaceId: String = ""
)

data class FinancialSummary(
    val billed: Double, val received: Double, val outstanding: Double,
    val overdue: Double, val issuedInvoices: Int, val paidInvoices: Int,
    val completedJobs: Int, val monthReceived: Double
)

object BusinessLogic {
    private fun cents(v: Double): Double = kotlin.math.round(v * 100) / 100
    fun financials(jobs: List<JobRecord>, appointments: List<Appointment>,
                   today: LocalDate = LocalDate.now()): FinancialSummary {
        val issued = jobs.filter { it.invoiceIssued }
        val billed = cents(issued.sumOf { JobMath.subtotal(it) })
        // All received amounts are from the job ledger, including deposits taken
        // before issue; never double-count legacy campaign payment records.
        val received = cents(jobs.sumOf { JobMath.paid(it) })
        val outstanding = cents(issued.sumOf { JobMath.balance(it) })
        val overdue = cents(issued.filter { JobMath.isOverdue(it, today) }
            .sumOf { JobMath.balance(it) })
        val paid = issued.count { JobMath.balance(it) == 0.0 }
        val completed = appointments.count { it.status == "COMPLETED" }
        val monthly = cents(jobs.sumOf { job -> job.payments.filter {
            runCatching { val day = LocalDate.parse(it.date)
                day.year == today.year && day.monthValue == today.monthValue }.getOrDefault(false)
        }.sumOf { it.amount } })
        return FinancialSummary(billed, received, outstanding, overdue,
            issued.size, paid, completed, monthly)
    }

    fun importCsv(csv: String, existing: List<Customer>,
                  today: LocalDate = LocalDate.now()): CsvImportResult {
        require(csv.length <= 2_000_000) { "CSV file is too large (2 MB limit)." }
        val parsed = CsvRows.parse(csv)
        require(parsed.isNotEmpty()) { "CSV file is empty." }
        val headers = parsed.first().map { it.trim().lowercase(Locale.US)
            .replace("_", "").replace(" ", "") }
        fun column(vararg aliases: String) = aliases.firstNotNullOfOrNull { alias ->
            headers.indexOf(alias).takeIf { it >= 0 }
        }
        val nameCol = column("name", "customername", "fullname")
        val phoneCol = column("phone", "phonenumber", "mobile")
        val zipCol = column("zip", "zipcode", "postalcode")
        require(nameCol != null && phoneCol != null && zipCol != null) {
            "CSV must include name, phone and ZIP columns."
        }
        val serviceCol = column("service", "lastservice", "servicetype")
        val lastDateCol = column("lastservicedate", "servicedate", "date")
        val addressCol = column("address", "streetaddress")
        val notesCol = column("notes")
        require(parsed.size <= 1001) { "Import up to 1000 customers at a time." }
        val known = existing.mapTo(mutableSetOf()) { CampaignRules.digits(it.phone) }
        val candidates = mutableListOf<Customer>()
        var skipped = 0
        for (row in parsed.drop(1)) {
            fun v(index: Int?): String = if (index != null) row.getOrNull(index)?.trim().orEmpty()
                                        else ""
            if (row.all { it.isBlank() }) continue
            val name = v(nameCol)
            val phone = v(phoneCol)
            val zip = v(zipCol)
            val digits = CampaignRules.digits(phone)
            val service = v(serviceCol).ifBlank { "Service not specified" }
            val last = v(lastDateCol)
            if (name.isBlank() || name.length > 140 ||
                digits.length !in 10..15 || !zip.matches(Regex("\\d{5}")) ||
                service.length > 200 || (last.isNotBlank() &&
                    runCatching { LocalDate.parse(last) }.isFailure) || digits in known) {
                skipped++
                continue
            }
            known += digits
            candidates += Customer(
                name = name, phone = phone, zip = zip, service = service,
                lastService = last.ifBlank { today.toString() },
                address = v(addressCol).take(499),
                notes = v(notesCol).take(3000),
                consent = false, consentEvidence = "", consentDate = "",
                lastPrice = 0.0
            )
        }
        return CsvImportResult(candidates, skipped)
    }
}

data class CsvImportResult(val customers: List<Customer>, val skipped: Int)

/** CSV with quoted commas and newlines; rejects unclosed quotes. */
object CsvRows {
    fun parse(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var afterQuote = false
        var i = 0
        val input = text.removePrefix("\uFEFF")
        while (i < input.length) {
            val ch = input[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < input.length && input[i + 1] == '"') {
                        cell.append('"'); i++
                    } else { quoted = false; afterQuote = true }
                } else cell.append(ch)
            } else when (ch) {
                '"' -> {
                    if (!afterQuote && cell.isEmpty()) quoted = true
                    else error("Unexpected quote in CSV.")
                }
                ',' -> {
                    cells += cell.toString(); cell.clear(); afterQuote = false
                }
                '\n', '\r' -> {
                    if (ch == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    cells += cell.toString(); cell.clear()
                    result += cells.toList(); cells.clear(); afterQuote = false
                }
                else -> {
                    if (afterQuote && !ch.isWhitespace()) error("Malformed quoted CSV field.")
                    if (!afterQuote) cell.append(ch)
                }
            }
            i++
        }
        require(!quoted) { "CSV contains an unclosed quote." }
        if (cell.isNotEmpty() || afterQuote || cells.isNotEmpty()) {
            cells += cell.toString()
            result += cells.toList()
        }
        return result
    }
}

package com.routerevive.app

import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONObject

data class BetaAudit(
    val customerCount: Int,
    val appointmentCount: Int,
    val jobCount: Int,
    val requestCount: Int,
    val issues: List<String>
) {
    val healthy: Boolean get() = issues.isEmpty()
}

/**
 * A local diagnostic only. Never includes names, phone numbers or addresses
 * in a report shared with the business owner or support.
 */
object BetaIntegrity {
    fun audit(
        customers: List<Customer>,
        appointments: List<Appointment>,
        jobs: List<JobRecord>,
        requests: List<BookingRequest>,
        campaigns: List<Campaign>,
        business: BusinessProfile,
        photoExists: (String) -> Boolean = { true }
    ): BetaAudit {
        val errors = mutableListOf<String>()
        val customerIds = customers.map { it.id }.toSet()
        val appointmentIds = appointments.map { it.id }.toSet()
        fun duplicate(values: List<String>): Boolean = values.size != values.toSet().size
        if (duplicate(customers.map { it.id })) errors += "Duplicate customer record IDs"
        if (duplicate(appointments.map { it.id })) errors += "Duplicate appointment IDs"
        if (duplicate(jobs.map { it.id }) ||
            duplicate(jobs.map { it.appointmentId })) errors += "Duplicate job associations"
        if (duplicate(requests.map { it.id })) errors += "Duplicate booking request IDs"
        if (duplicate(campaigns.map { it.id })) errors += "Duplicate campaign IDs"
        if (appointments.any { it.customerId !in customerIds })
            errors += "Appointments refer to missing customers"
        if (jobs.any { it.appointmentId !in appointmentIds ||
            it.customerId !in customerIds ||
            appointments.firstOrNull { a -> a.id == it.appointmentId }?.customerId != it.customerId })
            errors += "Job records refer to missing or mismatched appointments"
        if (requests.any { it.status == "BOOKED" &&
            it.bookedAppointmentId !in appointmentIds })
            errors += "Confirmed requests refer to missing appointments"
        if (campaigns.any { c -> c.recipients.any { it.customerId !in customerIds } })
            errors += "Campaigns refer to missing customers"
        if (appointments.any {
            !ScheduleRules.validDate(it.date) ||
                !ScheduleRules.validTime(it.time) ||
                it.durationMinutes !in 15..480
        }) errors += "Invalid appointment dates, times or durations"
        val active = appointments.filter { it.status in setOf("SCHEDULED", "IN_PROGRESS") }
        if (active.any { ap -> ScheduleRules.overlaps(ap, active) })
            errors += "Active appointments have overlapping time slots"
        if (jobs.any { j ->
                j.lineItems.any { !JobMath.validLineItem(it) } ||
                    j.payments.any { it.amount <= 0 || !it.amount.isFinite() } ||
                    JobMath.paid(j) > JobMath.subtotal(j) + 0.001
            }) errors += "Job amounts or recorded payments need review"
        val photoNames = jobs.flatMap { it.photos.map { p -> p.filename } } +
            listOfNotNull(business.logoFile.takeIf { it.isNotBlank() })
        if (photoNames.any { !photoExists(it) })
            errors += "Some job photos or the business logo are missing"
        return BetaAudit(customers.size, appointments.size, jobs.size,
            requests.size, errors.distinct())
    }
}

/**
 * Backup validation is performed BEFORE overwriting encrypted live storage.
 * Relational issues already present in older backups are surfaced by audit,
 * while obviously malformed or corrupt data is always rejected.
 */
object BackupValidator {
    fun check(payload: String) {
        require(payload.length <= 5_000_000) { "Backup is too large" }
        val root = JSONObject(payload)
        require(root.optInt("schemaVersion", 1) in 1..7) {
            "Unsupported backup version"
        }
        require(root.optJSONArray("customers") != null &&
            root.optJSONArray("campaigns") != null) { "Not a RouteRevive backup" }
        require(root.length() <= 12) { "Unexpected backup structure" }
        fun array(name: String): org.json.JSONArray =
            root.optJSONArray(name) ?: org.json.JSONArray()
        val customers = array("customers")
        val campaigns = array("campaigns")
        val appointments = array("appointments")
        val jobs = array("jobs")
        val requests = array("bookingRequests")
        // Reject optional fields that are present with the wrong JSON type.
        for (name in listOf("appointments", "jobs", "bookingRequests")) {
            require(!root.has(name) || root.optJSONArray(name) != null) {
                "Invalid backup array: $name"
            }
        }
        require(!root.has("businessProfile") || root.optJSONObject("businessProfile") != null) {
            "Invalid business profile"
        }
        require(customers.length() <= 50000 && campaigns.length() <= 10000 &&
            appointments.length() <= 100000 && jobs.length() <= 100000 &&
            requests.length() <= 100000) { "Backup record count exceeds safe limit" }
        fun records(name: String, required: Array<String>, list: org.json.JSONArray) {
            for (i in 0 until list.length()) {
                val record = list.getJSONObject(i)
                for (key in required) {
                    require(record.has(key) && !record.isNull(key)) {
                        "Invalid $name record: required field is missing"
                    }
                }
            }
        }
        records("customer", arrayOf("id", "name", "phone", "zip", "service",
            "lastService"), customers)
        records("campaign", arrayOf("id", "title", "businessName",
            "zip", "service", "visitDate", "expiryDate", "price", "capacity"), campaigns)
        records("appointment", arrayOf("id", "customerId", "service",
            "date", "time"), appointments)
        records("job", arrayOf("id", "appointmentId", "customerId"), jobs)
        records("request", arrayOf("id", "name", "phone", "zip", "service",
            "requestedDate", "requestedTime"), requests)
        for (i in 0 until jobs.length()) {
            val job = jobs.getJSONObject(i)
            job.optJSONArray("lineItems")?.let { list ->
                for (n in 0 until list.length()) {
                    val item = list.getJSONObject(n)
                    require(item.has("description") && item.has("quantity") &&
                        item.has("unitPrice")) { "Invalid job line item" }
                    require(JobMath.validLineItem(JobLineItem(item.getString("description"),
                        item.getDouble("quantity"), item.getDouble("unitPrice")))) {
                        "Invalid job amounts"
                    }
                }
            }
            job.optJSONArray("payments")?.let { list ->
                for (n in 0 until list.length()) {
                    val entry = list.getJSONObject(n)
                    require(entry.has("id") && entry.has("amount") &&
                        entry.has("date")) { "Invalid payment entry" }
                    require(entry.getDouble("amount").isFinite() &&
                        entry.getDouble("amount") > 0) { "Invalid payment value" }
                }
            }
            job.optJSONArray("photos")?.let { list ->
                for (n in 0 until list.length()) {
                    val entry = list.getJSONObject(n)
                    require(entry.getString("filename").matches(
                        Regex("[a-f0-9-]{36}\\.jpg"))) {
                        "Unsafe or invalid photo filename"
                    }
                }
            }
        }
        for (i in 0 until requests.length()) {
            val r = requests.getJSONObject(i)
            require(BookingRules.valid(BookingRequest(
                id = r.getString("id"), name = r.getString("name"),
                phone = r.getString("phone"), zip = r.getString("zip"),
                service = r.getString("service"),
                requestedDate = r.getString("requestedDate"),
                requestedTime = r.getString("requestedTime"),
                durationMinutes = r.optInt("durationMinutes", 60),
                quotedPrice = r.optDouble("quotedPrice", 0.0),
                notes = r.optString("notes"), source = r.optString("source", "Phone"),
                status = r.optString("status", "NEW"),
                bookedAppointmentId = r.optString("bookedAppointmentId")
            ))) { "Invalid booking request" }
        }
    }
}

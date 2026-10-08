package com.routerevive.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Customer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val zip: String,
    val service: String,
    val lastService: String,
    val consent: Boolean = false,
    val consentEvidence: String = "",
    val optedOut: Boolean = false,
    val issueOpen: Boolean = false,
    val futureBooked: Boolean = false,
    val lastContact: String = "",
    val lastPrice: Double = 0.0,
    val address: String = "",
    val notes: String = "",
    val consentDate: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val demo: Boolean = false
)

data class Appointment(
    val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val service: String,
    val date: String,
    val time: String,
    val durationMinutes: Int = 60,
    val status: String = "SCHEDULED",
    val price: Double = 0.0,
    val notes: String = "",
    val campaignId: String = ""
)

data class Recipient(
    val customerId: String,
    val status: String = "DRAFT",
    val message: String = "",
    val approvedAt: String = "",
    val sentAt: String = "",
    val bookedAmount: Double = 0.0,
    val paidAmount: Double = 0.0
)

data class Campaign(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val businessName: String,
    val zip: String,
    val service: String,
    val visitDate: String,
    val expiryDate: String,
    val price: Double,
    val discountPct: Int,
    val capacity: Int,
    val createdAt: String = LocalDate.now().toString(),
    val recipients: List<Recipient> = emptyList()
)

object CampaignRules {
    private const val REPEAT_DAYS = 180L
    private const val CONTACT_COOLDOWN_DAYS = 60L
    private val busy = setOf("APPROVED", "SENT", "INTERESTED", "BOOKED", "COMPLETED", "PAID")

    fun digits(phone: String): String = phone.filter { it.isDigit() }

    fun reasons(
        customer: Customer, zip: String, service: String, today: LocalDate,
        customers: List<Customer>, campaigns: List<Campaign>
    ): List<String> {
        val errors = mutableListOf<String>()
        if (customer.demo) errors += "Example record (not contactable)"
        if (customer.zip != zip || !customer.zip.matches(Regex("\\d{5}"))) errors += "Outside selected ZIP"
        if (!customer.service.equals(service, ignoreCase = true)) errors += "Different service"
        if (digits(customer.phone).length !in 10..15) errors += "Invalid phone"
        if (!customer.consent || customer.consentEvidence.isBlank()) errors += "No documented SMS marketing permission"
        if (customer.optedOut) errors += "Opted out"
        if (customer.issueOpen) errors += "Unresolved complaint"
        if (customer.futureBooked) errors += "Already has upcoming booking"
        val last = runCatching { LocalDate.parse(customer.lastService) }.getOrNull()
        if (last == null || ChronoUnit.DAYS.between(last, today) < REPEAT_DAYS) errors += "Service not due (180 days)"
        if (customer.lastContact.isNotBlank()) {
            val recent = runCatching { LocalDate.parse(customer.lastContact) }.getOrNull()
            if (recent == null || ChronoUnit.DAYS.between(recent, today) < CONTACT_COOLDOWN_DAYS)
                errors += "Contacted in past 60 days"
        }
        val sameNumber = customers.count {
            it.id != customer.id && digits(it.phone) == digits(customer.phone) && digits(it.phone).isNotBlank()
        }
        if (sameNumber > 0) errors += "Duplicate phone number"
        if (campaigns.any { campaign ->
            runCatching { !LocalDate.parse(campaign.expiryDate).isBefore(today) }.getOrDefault(false) &&
                campaign.recipients.any { it.customerId == customer.id && it.status in busy }
        }) errors += "Already in active campaign"
        return errors
    }

    fun bookedSlots(campaign: Campaign): Int =
        campaign.recipients.count { it.status in setOf("BOOKED", "COMPLETED", "PAID") }

    fun message(customer: Customer, campaign: Campaign): String {
        val first = customer.name.trim().substringBefore(" ").ifBlank { "there" }
        val discount = if (campaign.discountPct > 0)
            " You'll receive ${campaign.discountPct}% off your confirmed quote." else ""
        return "Hi $first! This is ${campaign.businessName}. We'll be offering " +
            "${campaign.service.lowercase()} in ZIP ${campaign.zip} on ${campaign.visitDate}, " +
            "starting at $"+String.format(java.util.Locale.US, "%.2f", campaign.price)+
            " (final price confirmed before booking).$discount Interested in an available spot? " +
            "Reply YES to ask about booking or STOP to opt out."
    }
}

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("routerevive_local_v1", Context.MODE_PRIVATE)
    private fun data(): JSONObject =
        runCatching { JSONObject(prefs.getString("db", "{}") ?: "{}") }.getOrElse { JSONObject() }

    fun loadCustomers(): List<Customer> {
        val array = data().optJSONArray("customers") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val j = array.getJSONObject(i)
                Customer(
                    id = j.getString("id"), name = j.getString("name"), phone = j.getString("phone"),
                    zip = j.getString("zip"), service = j.getString("service"),
                    lastService = j.getString("lastService"), consent = j.optBoolean("consent"),
                    consentEvidence = j.optString("consentEvidence"), optedOut = j.optBoolean("optedOut"),
                    issueOpen = j.optBoolean("issueOpen"), futureBooked = j.optBoolean("futureBooked"),
                    lastContact = j.optString("lastContact"), lastPrice = j.optDouble("lastPrice"),
                    address = j.optString("address"), notes = j.optString("notes"),
                    consentDate = j.optString("consentDate"),
                    latitude = if (j.has("latitude") && !j.isNull("latitude")) j.getDouble("latitude") else null,
                    longitude = if (j.has("longitude") && !j.isNull("longitude")) j.getDouble("longitude") else null,
                    demo = j.optBoolean("demo")
                )
            }.getOrNull()
        }
    }

    fun loadCampaigns(): List<Campaign> {
        val array = data().optJSONArray("campaigns") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val j = array.getJSONObject(i)
                val list = j.optJSONArray("recipients") ?: JSONArray()
                Campaign(
                    id = j.getString("id"), title = j.getString("title"),
                    businessName = j.getString("businessName"), zip = j.getString("zip"),
                    service = j.getString("service"), visitDate = j.getString("visitDate"),
                    expiryDate = j.getString("expiryDate"), price = j.getDouble("price"),
                    discountPct = j.optInt("discountPct"), capacity = j.getInt("capacity"),
                    createdAt = j.optString("createdAt"),
                    recipients = (0 until list.length()).map { index ->
                        val r = list.getJSONObject(index)
                        Recipient(
                            customerId = r.getString("customerId"), status = r.getString("status"),
                            message = r.optString("message"), approvedAt = r.optString("approvedAt"),
                            sentAt = r.optString("sentAt"),
                            bookedAmount = r.optDouble("bookedAmount"),
                            paidAmount = r.optDouble("paidAmount")
                        )
                    }
                )
            }.getOrNull()
        }
    }

    fun loadAppointments(): List<Appointment> {
        val array = data().optJSONArray("appointments") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val j = array.getJSONObject(i)
                Appointment(
                    id = j.getString("id"), customerId = j.getString("customerId"),
                    service = j.getString("service"), date = j.getString("date"),
                    time = j.getString("time"), durationMinutes = j.optInt("durationMinutes", 60),
                    status = j.optString("status", "SCHEDULED"), price = j.optDouble("price"),
                    notes = j.optString("notes"), campaignId = j.optString("campaignId")
                )
            }.getOrNull()
        }
    }

    fun loadJobs(): List<JobRecord> {
        val array = data().optJSONArray("jobs") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val j = array.getJSONObject(i)
                val itemArray = j.optJSONArray("lineItems") ?: JSONArray()
                val photoArray = j.optJSONArray("photos") ?: JSONArray()
                val paymentArray = j.optJSONArray("payments") ?: JSONArray()
                JobRecord(
                    id = j.getString("id"),
                    appointmentId = j.getString("appointmentId"),
                    customerId = j.getString("customerId"),
                    businessName = j.optString("businessName"),
                    serviceDetails = j.optString("serviceDetails"),
                    dueDate = j.optString("dueDate"),
                    invoiceIssued = j.optBoolean("invoiceIssued"),
                    invoiceNumber = j.optString("invoiceNumber"),
                    lineItems = (0 until itemArray.length()).map { n ->
                        val item = itemArray.getJSONObject(n)
                        JobLineItem(item.getString("description"), item.getDouble("quantity"),
                            item.getDouble("unitPrice"))
                    },
                    photos = (0 until photoArray.length()).mapNotNull { n ->
                        val photo = photoArray.getJSONObject(n)
                        val name = photo.getString("filename")
                        if (!name.matches(Regex("[a-f0-9-]{36}\\.jpg"))) null
                        else JobPhoto(name, photo.optString("stage", "BEFORE"),
                            photo.optString("addedAt"))
                    },
                    payments = (0 until paymentArray.length()).map { n ->
                        val payment = paymentArray.getJSONObject(n)
                        JobPayment(payment.getString("id"), payment.getDouble("amount"),
                            payment.getString("date"), payment.optString("method"),
                            payment.optString("notes"))
                    }
                )
            }.getOrNull()
        }
    }

    fun exportJson(): String = data().apply { put("schemaVersion", 4) }.toString(2)

    fun importJson(payload: String) {
        val root = JSONObject(payload)
        require(root.optInt("schemaVersion", 1) in 1..4) { "Unsupported backup version" }
        require(root.optJSONArray("customers") != null && root.optJSONArray("campaigns") != null) {
            "Not a RouteRevive backup"
        }
        require(root.length() <= 12) { "Unexpected backup structure" }
        require(payload.length <= 5_000_000) { "Backup is too large" }
        val old = prefs.getString("db", "{}") ?: "{}"
        check(prefs.edit().putString("db", root.toString()).commit()) { "Import failed to save" }
        try {
            val parsedCustomers = loadCustomers()
            val parsedCampaigns = loadCampaigns()
            val parsedAppointments = loadAppointments()
            val parsedJobs = loadJobs()
            require(parsedCustomers.size == root.getJSONArray("customers").length()) { "Invalid customer data" }
            require(parsedCampaigns.size == root.getJSONArray("campaigns").length()) { "Invalid campaign data" }
            require(parsedAppointments.size == (root.optJSONArray("appointments")?.length() ?: 0)) {
                "Invalid appointment data"
            }
            require(parsedJobs.size == (root.optJSONArray("jobs")?.length() ?: 0)) {
                "Invalid job data"
            }
        } catch (e: Exception) {
            prefs.edit().putString("db", old).commit()
            throw IllegalArgumentException("Backup data is invalid: ${e.message}")
        }
    }

    fun save(customers: List<Customer>, campaigns: List<Campaign>, appointments: List<Appointment> = emptyList(), jobs: List<JobRecord> = emptyList()) {
        val cs = JSONArray()
        customers.forEach { c ->
            cs.put(JSONObject().apply {
                put("id", c.id); put("name", c.name); put("phone", c.phone)
                put("zip", c.zip); put("service", c.service); put("lastService", c.lastService)
                put("consent", c.consent); put("consentEvidence", c.consentEvidence)
                put("optedOut", c.optedOut); put("issueOpen", c.issueOpen)
                put("futureBooked", c.futureBooked); put("lastContact", c.lastContact)
                put("lastPrice", c.lastPrice); put("address", c.address)
                put("notes", c.notes); put("consentDate", c.consentDate); put("demo", c.demo)
                if (c.latitude != null && c.longitude != null) {
                    put("latitude", c.latitude); put("longitude", c.longitude)
                }
            })
        }
        val cps = JSONArray()
        campaigns.forEach { c ->
            val rs = JSONArray()
            c.recipients.forEach { r ->
                rs.put(JSONObject().apply {
                    put("customerId", r.customerId); put("status", r.status); put("message", r.message)
                    put("approvedAt", r.approvedAt); put("sentAt", r.sentAt)
                    put("bookedAmount", r.bookedAmount); put("paidAmount", r.paidAmount)
                })
            }
            cps.put(JSONObject().apply {
                put("id", c.id); put("title", c.title); put("businessName", c.businessName)
                put("zip", c.zip); put("service", c.service)
                put("visitDate", c.visitDate); put("expiryDate", c.expiryDate)
                put("price", c.price); put("discountPct", c.discountPct)
                put("capacity", c.capacity); put("createdAt", c.createdAt); put("recipients", rs)
            })
        }
        val aps = JSONArray()
        appointments.forEach { a ->
            aps.put(JSONObject().apply {
                put("id", a.id); put("customerId", a.customerId); put("service", a.service)
                put("date", a.date); put("time", a.time); put("durationMinutes", a.durationMinutes)
                put("status", a.status); put("price", a.price); put("notes", a.notes)
                put("campaignId", a.campaignId)
            })
        }
        val js = JSONArray()
        jobs.forEach { job ->
            val lines = JSONArray()
            job.lineItems.forEach { item ->
                lines.put(JSONObject().apply {
                    put("description", item.description); put("quantity", item.quantity)
                    put("unitPrice", item.unitPrice)
                })
            }
            val photos = JSONArray()
            job.photos.forEach { photo ->
                photos.put(JSONObject().apply {
                    put("filename", photo.filename); put("stage", photo.stage)
                    put("addedAt", photo.addedAt)
                })
            }
            val payments = JSONArray()
            job.payments.forEach { payment ->
                payments.put(JSONObject().apply {
                    put("id", payment.id); put("amount", payment.amount)
                    put("date", payment.date); put("method", payment.method)
                    put("notes", payment.notes)
                })
            }
            js.put(JSONObject().apply {
                put("id", job.id); put("appointmentId", job.appointmentId)
                put("customerId", job.customerId)
                put("businessName", job.businessName)
                put("serviceDetails", job.serviceDetails)
                put("lineItems", lines); put("photos", photos)
                put("payments", payments); put("dueDate", job.dueDate)
                put("invoiceIssued", job.invoiceIssued)
                put("invoiceNumber", job.invoiceNumber)
            })
        }
        // One atomic update: new data coexists with pre-v0.0.6 records.
        check(prefs.edit().putString("db", JSONObject().put("schemaVersion", 4)
            .put("customers", cs).put("campaigns", cps).put("appointments", aps)
            .put("jobs", js).toString()).commit()) {
            "Unable to save RouteRevive data"
        }
    }
}

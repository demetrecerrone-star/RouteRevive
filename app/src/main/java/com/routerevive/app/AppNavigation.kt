package com.routerevive.app

enum class AppSection(val page: String, val label: String) {
    OVERVIEW("home", "Overview"),
    CUSTOMERS("customers", "Customers"),
    SCHEDULE("schedule", "Schedule"),
    JOBS("jobs", "Jobs"),
    MORE("more", "More")
}

/** Shared by the navigation bar, toolbar and Android back gesture. */
object AppNavigation {
    fun isTopLevel(page: String): Boolean = AppSection.entries.any { it.page == page }

    fun section(page: String): AppSection = when (page) {
        "home" -> AppSection.OVERVIEW
        "customers", "new_customer", "edit_customer", "customer_history" -> AppSection.CUSTOMERS
        "schedule" -> AppSection.SCHEDULE
        "jobs" -> AppSection.JOBS
        else -> AppSection.MORE
    }

    fun backTarget(page: String): String? = when (page) {
        "home" -> null
        "new_customer", "edit_customer", "customer_history" -> "customers"
        "campaign_detail", "new_campaign" -> "campaigns"
        "customers", "schedule", "jobs", "more" -> "home"
        else -> "more"
    }

    fun title(page: String, campaignTitle: String? = null): String = when (page) {
        "home" -> "Overview"
        "customers" -> "Customers"
        "new_customer" -> "Add customer"
        "edit_customer" -> "Edit customer"
        "customer_history" -> "Customer history"
        "schedule" -> "Schedule"
        "jobs" -> "Jobs & invoices"
        "business" -> "Business & finances"
        "requests" -> "Booking requests"
        "more" -> "Business tools"
        "booking_page" -> "Booking request page"
        "beta" -> "Readiness & privacy"
        "cloud" -> "Private cloud backups"
        "insights" -> "Business analytics"
        "alerts" -> "Reminders & alerts"
        "map" -> "Neighborhood map"
        "campaigns" -> "Campaigns"
        "new_campaign" -> "New campaign"
        "campaign_detail" -> campaignTitle?.takeIf { it.isNotBlank() } ?: "Campaign"
        else -> "RouteRevive"
    }
}

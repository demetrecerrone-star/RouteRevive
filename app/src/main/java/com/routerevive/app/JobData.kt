package com.routerevive.app

import java.time.LocalDate
import java.util.UUID

data class JobLineItem(
    val description: String,
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0
)

data class JobPhoto(
    val filename: String,
    val stage: String, // BEFORE or AFTER
    val addedAt: String = LocalDate.now().toString()
)

data class JobPayment(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val date: String = LocalDate.now().toString(),
    val method: String = "Other",
    val notes: String = ""
)

data class JobRecord(
    val id: String = UUID.randomUUID().toString(),
    val appointmentId: String,
    val customerId: String,
    val businessName: String = "",
    val serviceDetails: String = "",
    val lineItems: List<JobLineItem> = emptyList(),
    val photos: List<JobPhoto> = emptyList(),
    val payments: List<JobPayment> = emptyList(),
    val dueDate: String = "",
    val invoiceIssued: Boolean = false,
    val invoiceNumber: String = ""
)

object JobMath {
    private fun dollars(value: Double) = kotlin.math.round(value * 100.0) / 100.0

    fun subtotal(job: JobRecord): Double = dollars(job.lineItems.sumOf { item ->
        item.quantity * item.unitPrice
    })
    fun paid(job: JobRecord): Double = dollars(job.payments.sumOf { it.amount })
    fun balance(job: JobRecord): Double = dollars((subtotal(job) - paid(job)).coerceAtLeast(0.0))
    fun isOverdue(job: JobRecord, today: LocalDate = LocalDate.now()): Boolean =
        job.invoiceIssued && job.dueDate.isNotBlank() &&
            runCatching { LocalDate.parse(job.dueDate).isBefore(today) }.getOrDefault(false) &&
            balance(job) > 0

    fun validLineItem(item: JobLineItem): Boolean =
        item.description.isNotBlank() && item.description.length <= 140 &&
        item.quantity.isFinite() && item.quantity > 0 && item.quantity <= 9999 &&
        item.unitPrice.isFinite() && item.unitPrice >= 0 && item.unitPrice <= 1_000_000

    fun canRecordPayment(job: JobRecord, amount: Double): Boolean =
        amount.isFinite() && amount > 0 && dollars(amount) == amount &&
            amount <= balance(job) + 0.00001

    fun defaultFrom(appointment: Appointment) = JobRecord(
        appointmentId = appointment.id, customerId = appointment.customerId,
        serviceDetails = appointment.notes,
        lineItems = listOf(JobLineItem(appointment.service, 1.0, appointment.price)),
        invoiceNumber = "RR-" + appointment.id.replace("-", "").take(8).uppercase()
    )
}

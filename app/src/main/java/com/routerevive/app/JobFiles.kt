package com.routerevive.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/** Photo bytes are PRIVATE to the app and are NOT included in JSON exports. */
object JobMedia {
    private val allowed = Regex("[a-f0-9-]{36}\\.jpg")
    private fun dir(context: Context) = File(context.filesDir, "job_photos").also { it.mkdirs() }
    fun newCameraFile(context: Context): File = File(dir(context), UUID.randomUUID().toString() + ".jpg")
    fun photoFile(context: Context, filename: String): File? =
        if (allowed.matches(filename)) File(dir(context), filename) else null
    fun bitmap(context: Context, filename: String): Bitmap? =
        photoFile(context, filename)?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
    fun remove(context: Context, filename: String) { photoFile(context, filename)?.delete() }
    private fun sample(w: Int, h: Int): Int {
        var size = 1
        while (w / size > 1500 || h / size > 1500) size *= 2
        return size
    }
    fun importPhoto(context: Context, uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Could not read photo")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid photo" }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight) }
        val image = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("Could not decode photo")
        val dest = newCameraFile(context)
        try {
            dest.outputStream().use { check(image.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
            return dest.name
        } catch (ex: Exception) {
            dest.delete()
            throw ex
        } finally { image.recycle() }
    }
    fun normalizeCameraPhoto(context: Context, filename: String): Boolean {
        val file = photoFile(context, filename)?.takeIf { it.exists() } ?: return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        val bitmap = BitmapFactory.decodeFile(file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight) })
            ?: return false
        val tmp = File(dir(context), filename + ".tmp")
        return try {
            tmp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
            check(tmp.renameTo(file)) { "Could not save camera image" }
            true
        } catch (_: Exception) {
            tmp.delete()
            false
        } finally { bitmap.recycle() }
    }
}

object JobPdf {
    private fun usd(v: Double) = "$" + String.format(Locale.US, "%,.2f", v)
    fun write(out: OutputStream, job: JobRecord, customer: Customer,
              appointment: Appointment, invoice: Boolean) {
        val pdf = PdfDocument()
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 35, 52) }
        var number = 0
        var page: PdfDocument.Page? = null
        var y = 44f
        fun newPage() {
            if (page != null) pdf.finishPage(page!!)
            page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, ++number).create())
            page!!.canvas.drawColor(Color.WHITE)
            y = 44f
        }
        fun line(value: String, size: Float = 11f, bold: Boolean = false) {
            if (page == null || y > 780f) newPage()
            ink.textSize = size
            ink.isFakeBoldText = bold
            page!!.canvas.drawText(value.take(100), 38f, y, ink)
            y += if (size >= 18f) 32f else 19f
        }
        fun section() { y += 10f }
        try {
            newPage()
            line(job.businessName.ifBlank { "RouteRevive Service" }, 22f, true)
            line(if (invoice) "INVOICE" else "ESTIMATE", 18f, true)
            line("Reference: " + job.invoiceNumber.ifBlank { job.id.take(12) })
            line("Created: " + LocalDate.now())
            if (invoice && job.dueDate.isNotBlank()) line("Due: " + job.dueDate)
            section()
            line("BILL TO", 12f, true)
            line(customer.name)
            if (customer.address.isNotBlank()) line(customer.address)
            line("ZIP: " + customer.zip)
            section()
            line("Scheduled job: " + appointment.date + " at " + appointment.time)
            if (job.serviceDetails.isNotBlank()) {
                line("WORK NOTES", 12f, true)
                job.serviceDetails.chunked(82).take(5).forEach { line(it) }
            }
            section()
            line("SERVICES", 12f, true)
            job.lineItems.forEach { item ->
                line(item.description.take(56), 11f, true)
                line(String.format(Locale.US, "%.2f x %s = %s",
                    item.quantity, usd(item.unitPrice), usd(item.quantity * item.unitPrice)))
            }
            section()
            line("Subtotal: " + usd(JobMath.subtotal(job)), 14f, true)
            if (invoice) {
                line("Received: " + usd(JobMath.paid(job)))
                line("Balance due: " + usd(JobMath.balance(job)), 14f, true)
                if (job.payments.isNotEmpty()) {
                    section()
                    line("PAYMENT HISTORY", 12f, true)
                    job.payments.takeLast(15).forEach {
                        line(it.date + " · " + it.method + ": " + usd(it.amount))
                    }
                }
            } else line("Estimate only. Confirm before booking or payment.")
            section()
            line("Prepared locally using RouteRevive.", 10f)
            line("No payment has been processed by this app.", 10f)
            pdf.finishPage(page!!)
            page = null
            pdf.writeTo(out)
        } finally { pdf.close() }
    }

    fun share(context: Context, job: JobRecord, customer: Customer,
              appointment: Appointment, invoice: Boolean) {
        val folder = File(context.cacheDir, "job_pdfs").also { it.mkdirs() }
        val file = File(folder, (if (invoice) "Invoice-" else "Estimate-") +
            job.id.filter { it.isLetterOrDigit() }.take(20) + ".pdf")
        file.outputStream().use { write(it, job, customer, appointment, invoice) }
        val uri = FileProvider.getUriForFile(context,
            context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent,
            if (invoice) "Share invoice PDF" else "Share estimate PDF"))
    }
}

package com.routerevive.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.time.LocalDate
import java.util.Locale

private fun money(value: Double): String = "$" + String.format(Locale.US, "%,.2f", value)

@Composable
fun JobsScreen(
    appointments: List<Appointment>,
    customers: List<Customer>,
    jobs: List<JobRecord>,
    onSaveJob: (JobRecord) -> Unit,
    onOpenSchedule: () -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val appointment = appointments.firstOrNull { it.id == selectedId }
    if (appointment != null) {
        val customer = customers.firstOrNull { it.id == appointment.customerId }
        if (customer != null) {
            JobDetailScreen(appointment, customer,
                jobs.firstOrNull { it.appointmentId == appointment.id },
                onSaveJob, onClose = { selectedId = null })
            return
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("JOBS & INVOICING", color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Service details, photos, estimates, invoices and payments",
            color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
        OutlinedButton(onClick = onOpenSchedule, modifier = Modifier.fillMaxWidth()) {
            Text("Manage / add appointments")
        }
        if (appointments.isEmpty()) {
            Text("Your scheduled appointments will appear here as job records. Add one from Schedule.",
                modifier = Modifier.padding(14.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(appointments.sortedWith(compareByDescending<Appointment> { it.date }.thenBy { it.time }),
                key = { it.id }) { a ->
                val customer = customers.firstOrNull { it.id == a.customerId }
                val job = jobs.firstOrNull { it.appointmentId == a.id } ?: JobMath.defaultFrom(a)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(customer?.name ?: "Customer unavailable", fontWeight = FontWeight.Bold)
                        Text(a.date + "  ·  " + a.time + "  ·  " + a.status.replace('_', ' '),
                            color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                        Text(a.service, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Total " + money(JobMath.subtotal(job)), color = MaterialTheme.colorScheme.primary)
                            Text("Due " + money(JobMath.balance(job)), fontSize = 13.sp)
                        }
                        if (JobMath.isOverdue(job)) {
                            Text("OVERDUE", color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold)
                        }
                        Button(enabled = customer != null,
                            onClick = { selectedId = a.id }) { Text("Open job") }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobDetailScreen(
    appointment: Appointment,
    customer: Customer,
    savedJob: JobRecord?,
    onSaveJob: (JobRecord) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val starter = remember(appointment.id) { JobMath.defaultFrom(appointment) }
    val job = savedJob ?: starter
    var company by remember(job.id) { mutableStateOf(job.businessName) }
    var notes by remember(job.id) { mutableStateOf(job.serviceDetails) }
    var dueDate by remember(job.id) { mutableStateOf(job.dueDate) }
    var showLine by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var confirmRemovePhoto by remember { mutableStateOf<JobPhoto?>(null) }
    var lineName by remember { mutableStateOf("") }
    var lineQuantity by remember { mutableStateOf("1") }
    var linePrice by remember { mutableStateOf("0") }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cash") }
    var paymentDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var paymentNote by remember { mutableStateOf("") }
    var photoStage by remember { mutableStateOf("BEFORE") }
    var cameraFile by remember { mutableStateOf<String?>(null) }
    var issue by remember { mutableStateOf("") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val filename = JobMedia.importPhoto(context, uri)
            onSaveJob(job.copy(photos = job.photos + JobPhoto(filename, photoStage)))
        }.onFailure { issue = "Could not import image: " + it.message }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val name = cameraFile
        cameraFile = null
        if (name != null) {
            if (captured && JobMedia.normalizeCameraPhoto(context, name)) {
                onSaveJob(job.copy(photos = job.photos + JobPhoto(name, photoStage)))
            } else {
                JobMedia.remove(context, name)
                if (captured) issue = "Unable to save camera photo."
            }
        }
    }

    val validDue = dueDate.isBlank() || runCatching { LocalDate.parse(dueDate) }.isSuccess
    val validNotes = notes.length <= 4000 && company.length <= 100
    val itemCandidate = JobLineItem(lineName.trim(),
        lineQuantity.toDoubleOrNull() ?: 0.0, linePrice.toDoubleOrNull() ?: -1.0)
    val paymentValue = paymentAmount.toDoubleOrNull() ?: 0.0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onClose) { Text("← All jobs") }
        Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(appointment.service + " · " + appointment.date + " at " + appointment.time,
            color = MaterialTheme.colorScheme.secondary)
        Text("Status: " + appointment.status.replace('_', ' '), fontSize = 12.sp)
        HorizontalDivider()
        Text("JOB DETAILS", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = company, onValueChange = { company = it },
            label = { Text("Your business name for estimates / invoices") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = notes, onValueChange = { if (it.length <= 4000) notes = it },
            label = { Text("Service details / instructions") },
            modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(value = dueDate, onValueChange = { dueDate = it },
            label = { Text("Invoice due date YYYY-MM-DD (optional)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (!validDue) Text("Enter a valid date or leave it blank.",
            color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Button(onClick = {
            onSaveJob(job.copy(businessName = company.trim(), serviceDetails = notes.trim(),
                dueDate = dueDate.trim()))
            issue = "Job details saved."
        }, enabled = validDue && validNotes, modifier = Modifier.fillMaxWidth()) {
            Text("Save job details")
        }
        HorizontalDivider()
        Text("SERVICES & PRICING", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        job.lineItems.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.description, fontWeight = FontWeight.SemiBold)
                    Text(String.format(Locale.US, "%.2f × %s = %s",
                        item.quantity, money(item.unitPrice), money(item.quantity * item.unitPrice)),
                        color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    if (job.payments.isEmpty() || JobMath.paid(job) <=
                        JobMath.subtotal(job.copy(lineItems = job.lineItems.filterIndexed { i, _ -> i != index }))) {
                        onSaveJob(job.copy(lineItems = job.lineItems.filterIndexed { i, _ -> i != index }))
                    } else issue = "Cannot reduce total below existing payments."
                }) { Text("Remove") }
            }
        }
        OutlinedButton(onClick = { showLine = true },
            enabled = job.lineItems.size < 20) { Text("+ Add service line") }
        Text("Job total: " + money(JobMath.subtotal(job)), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider()
        Text("BEFORE / AFTER PHOTOS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Stored privately on this phone. JSON backups do not include photo files.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = photoStage == "BEFORE",
                onClick = { photoStage = "BEFORE" }, label = { Text("Before") })
            FilterChip(selected = photoStage == "AFTER",
                onClick = { photoStage = "AFTER" }, label = { Text("After") })
        }
        job.photos.filter { it.stage == photoStage }.forEach { photo ->
            val bitmap = remember(photo.filename) { JobMedia.bitmap(context, photo.filename) }
            if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = photo.stage + " job photo",
                modifier = Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Crop)
            else Text("Photo unavailable (not included in backup): " + photo.filename.take(8),
                color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            TextButton(onClick = { confirmRemovePhoto = photo }) { Text("Remove photo") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = job.photos.size < 16, onClick = { pickImage.launch("image/*") }) {
                Text("Choose photo")
            }
            OutlinedButton(enabled = job.photos.size < 16, onClick = {
                runCatching {
                    val file = JobMedia.newCameraFile(context)
                    cameraFile = file.name
                    val uri = FileProvider.getUriForFile(context,
                        context.packageName + ".fileprovider", file)
                    takePhoto.launch(uri)
                }.onFailure { issue = "Camera unavailable: " + it.message }
            }) { Text("Take photo") }
        }
        HorizontalDivider()
        Text("INVOICE & PAYMENTS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Invoice reference: " + job.invoiceNumber, fontSize = 12.sp)
        Text("Subtotal: " + money(JobMath.subtotal(job)))
        Text("Received: " + money(JobMath.paid(job)))
        Text("Balance: " + money(JobMath.balance(job)),
            fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        if (JobMath.isOverdue(job)) Text("OVERDUE INVOICE",
            color = MaterialTheme.colorScheme.error)
        if (job.invoiceIssued) Text("Invoice issued · No automated reminders",
            color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
        job.payments.forEach { payment ->
            Text(payment.date + " · " + payment.method + " · " + money(payment.amount),
                fontSize = 12.sp)
        }
        OutlinedButton(enabled = JobMath.balance(job) > 0,
            onClick = { showPayment = true }) { Text("+ Record deposit / payment") }
        Text("Payments are recorded manually. No bank or card is charged.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                runCatching { JobPdf.share(context, job, customer, appointment, false) }
                    .onFailure { issue = "Estimate PDF failed: " + it.message }
            }) { Text("Share estimate PDF") }
        }
        Button(onClick = {
            if (validDue && validNotes && job.lineItems.isNotEmpty()) {
                val ready = job.copy(
                    businessName = company.trim(),
                    serviceDetails = notes.trim(), dueDate = dueDate.trim(),
                    invoiceIssued = true)
                runCatching { JobPdf.share(context, ready, customer, appointment, true) }
                    .onSuccess { onSaveJob(ready) }
                    .onFailure { issue = "Invoice PDF failed: " + it.message }
            } else issue = "Save valid details and add a service first."
        }, enabled = validDue && validNotes && job.lineItems.isNotEmpty()) {
            Text("Create / share invoice PDF")
        }
        if (issue.isNotBlank()) Text(issue, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(30.dp))
    }

    if (showLine) AlertDialog(onDismissRequest = { showLine = false },
        title = { Text("Add service") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(lineName, { lineName = it }, label = { Text("Description") })
                OutlinedTextField(lineQuantity, { lineQuantity = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(linePrice, { linePrice = it },
                    label = { Text("Unit price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton(enabled = JobMath.validLineItem(itemCandidate),
            onClick = {
                onSaveJob(job.copy(lineItems = job.lineItems + itemCandidate))
                lineName = ""; lineQuantity = "1"; linePrice = "0"; showLine = false
            }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { showLine = false }) { Text("Cancel") } })

    if (showPayment) AlertDialog(onDismissRequest = { showPayment = false },
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Remaining balance: " + money(JobMath.balance(job)))
                OutlinedTextField(paymentAmount, { paymentAmount = it },
                    label = { Text("Amount received") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(paymentDate, { paymentDate = it },
                    label = { Text("Date YYYY-MM-DD") })
                OutlinedTextField(paymentMethod, { paymentMethod = it },
                    label = { Text("Method (cash, check, etc.)") })
                OutlinedTextField(paymentNote, { paymentNote = it },
                    label = { Text("Note (optional)") })
                Text("Only record payments already received.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            }
        },
        confirmButton = { TextButton(enabled =
            JobMath.canRecordPayment(job, paymentValue) &&
            runCatching { LocalDate.parse(paymentDate) }.isSuccess &&
            paymentMethod.isNotBlank() && paymentNote.length < 300,
            onClick = {
                onSaveJob(job.copy(payments = job.payments +
                    JobPayment(amount = paymentValue, date = paymentDate,
                        method = paymentMethod.trim(), notes = paymentNote.trim())))
                showPayment = false; paymentAmount = ""; paymentNote = ""
            }) { Text("Record received") } },
        dismissButton = { TextButton(onClick = { showPayment = false }) { Text("Cancel") } })

    confirmRemovePhoto?.let { photo ->
        AlertDialog(onDismissRequest = { confirmRemovePhoto = null },
            title = { Text("Remove photo?") },
            text = { Text("This deletes the local photo from this device.") },
            confirmButton = { TextButton(onClick = {
                onSaveJob(job.copy(photos = job.photos.filterNot { it.filename == photo.filename }))
                JobMedia.remove(context, photo.filename)
                confirmRemovePhoto = null
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmRemovePhoto = null }) { Text("Cancel") } })
    }
}

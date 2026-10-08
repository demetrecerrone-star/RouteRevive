package com.routerevive.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class AlertPreferences(
    val enabled: Boolean = false,
    val appointments: Boolean = true,
    val invoices: Boolean = true,
    val cloud: Boolean = true
)

data class AlertSummary(
    val today: Int,
    val tomorrow: Int,
    val overdue: Int,
    val cloudIssue: Boolean
) {
    fun isEmpty() = today == 0 && tomorrow == 0 && overdue == 0 && !cloudIssue
    fun message(): String = buildList {
        if (today > 0) add("$today appointment(s) today")
        if (tomorrow > 0) add("$tomorrow tomorrow")
        if (overdue > 0) add("$overdue overdue invoice(s)")
        if (cloudIssue) add("cloud backup needs attention")
    }.joinToString(" • ")
}

/** All alerts are for the business owner. No SMS, client names, or financial details leave the app. */
object AlertRules {
    fun summarize(
        appointments: List<Appointment>, jobs: List<JobRecord>,
        cloudIssue: Boolean, prefs: AlertPreferences,
        today: LocalDate = LocalDate.now()
    ): AlertSummary {
        val active = appointments.filter {
            it.status in setOf("SCHEDULED", "IN_PROGRESS", "CONFIRMED")
        }
        val todays = if (prefs.appointments) active.count { it.date == today.toString() } else 0
        val tomorrows = if (prefs.appointments) active.count {
            it.date == today.plusDays(1).toString()
        } else 0
        val overdue = if (prefs.invoices) jobs.count { JobMath.isOverdue(it, today) } else 0
        return AlertSummary(todays, tomorrows, overdue, prefs.cloud && cloudIssue)
    }

    fun cloudNeedsAttention(vault: CloudVault, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!vault.automaticEnabled()) return false
        val status = vault.lastSyncStatus()
        if (status.startsWith("Backup needs attention:") ||
            status.startsWith("Changes exist on multiple devices")) return true
        val last = vault.lastSyncTime()
        if (last <= 0L) return false // Allow setup/first run before warning.
        val limit = if (vault.autoHours() == 168L) 9L else 3L
        return nowMillis - last > TimeUnit.DAYS.toMillis(limit)
    }
}

class AlertStore(context: Context) {
    private val prefs = context.getSharedPreferences("routerevive_alerts_v1", Context.MODE_PRIVATE)
    fun load() = AlertPreferences(
        enabled = prefs.getBoolean("enabled", false),
        appointments = prefs.getBoolean("appointments", true),
        invoices = prefs.getBoolean("invoices", true),
        cloud = prefs.getBoolean("cloud", true)
    )

    fun save(config: AlertPreferences) {
        check(prefs.edit()
            .putBoolean("enabled", config.enabled)
            .putBoolean("appointments", config.appointments)
            .putBoolean("invoices", config.invoices)
            .putBoolean("cloud", config.cloud)
            .commit()) { "Unable to save notification preferences." }
    }

    /** Used to avoid posting duplicates after Android reschedules work. */
    fun alreadySent(date: LocalDate): Boolean =
        prefs.getString("last_alert_date", "") == date.toString()

    fun recordSent(date: LocalDate) {
        check(prefs.edit().putString("last_alert_date", date.toString()).commit())
    }
}

object LocalAlertNotifications {
    private const val CHANNEL_ID = "routerevive_business_daily"
    private const val ID = 2092

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    // Permission and system notification settings are checked immediately below.
    @android.annotation.SuppressLint("MissingPermission")
    fun notify(context: Context, summary: AlertSummary): Boolean {
        if (summary.isEmpty() || !canNotify(context)) return false
        val service = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Business reminders", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Private daily summary of your business tasks" })
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("RouteRevive: business reminders")
            .setContentText(summary.message())
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.message()))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(ID, notification)
            true
        } catch (_: SecurityException) { false }
    }

    fun dismiss(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }
}

class BusinessAlertWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefsStore = AlertStore(applicationContext)
        val prefs = prefsStore.load()
        if (!prefs.enabled || !LocalAlertNotifications.canNotify(applicationContext)) return Result.success()
        val today = LocalDate.now()
        if (prefsStore.alreadySent(today)) return Result.success()
        return try {
            val store = LocalStore(applicationContext)
            val summary = AlertRules.summarize(
                store.loadAppointments(), store.loadJobs(),
                AlertRules.cloudNeedsAttention(CloudVault(applicationContext)), prefs, today
            )
            if (summary.isEmpty()) return Result.success()
            if (LocalAlertNotifications.notify(applicationContext, summary)) prefsStore.recordSent(today)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}

object BusinessAlertScheduler {
    private const val WORK = "routerevive-local-business-alerts"
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        val target = LocalTime.of(8, 0)
        var next = now.toLocalDate().atTime(target)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = java.time.Duration.between(
            now.atZone(ZoneId.systemDefault()).toInstant(),
            next.atZone(ZoneId.systemDefault()).toInstant()
        ).toMillis().coerceAtLeast(0)
        val request = PeriodicWorkRequestBuilder<BusinessAlertWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK)
        LocalAlertNotifications.dismiss(context)
    }
}

@Composable
fun BusinessAlertsScreen() {
    val context = LocalContext.current
    val store = remember { AlertStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var message by remember { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted ->
        if (granted && LocalAlertNotifications.canNotify(context)) {
            runCatching {
                settings = settings.copy(enabled = true)
                store.save(settings)
                BusinessAlertScheduler.schedule(context)
                message = "Daily reminders enabled. Android will deliver them when background work is allowed."
            }.onFailure { message = it.message ?: "Could not enable reminders." }
        } else message = "Notifications were not allowed. Enable them in Android app settings to continue."
    }
    fun saveCategories(updated: AlertPreferences) {
        runCatching {
            store.save(updated)
            settings = updated
            message = "Preferences saved."
        }.onFailure { message = it.message ?: "Could not save preferences." }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("LOCAL REMINDERS", fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("Business notifications", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Private notifications for you, not customers. " +
            "No automatic texts, emails, or payment requests are sent.",
            color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Daily reminder summary", fontWeight = FontWeight.Bold)
                    Switch(checked = settings.enabled, onCheckedChange = { enabled ->
                        if (enabled) {
                            if (LocalAlertNotifications.canNotify(context)) {
                                runCatching {
                                    settings = settings.copy(enabled = true)
                                    store.save(settings)
                                    BusinessAlertScheduler.schedule(context)
                                    message = "Business reminders enabled."
                                }.onFailure { message = it.message ?: "Could not enable reminders." }
                            } else if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                message = "Enable RouteRevive notifications in your phone's app settings."
                            }
                        } else {
                            runCatching {
                                settings = settings.copy(enabled = false)
                                store.save(settings)
                                BusinessAlertScheduler.stop(context)
                                message = "Business reminders disabled."
                            }.onFailure { message = it.message ?: "Could not stop reminders." }
                        }
                    })
                }
                Text("Android schedules one private summary per day, approximately 8 AM. " +
                    "Exact delivery can vary because of battery optimization.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Today's & tomorrow's jobs")
                    Switch(checked = settings.appointments,
                        onCheckedChange = { saveCategories(settings.copy(appointments = it)) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Overdue invoice reminders")
                    Switch(checked = settings.invoices,
                        onCheckedChange = { saveCategories(settings.copy(invoices = it)) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cloud backup problems")
                    Switch(checked = settings.cloud,
                        onCheckedChange = { saveCategories(settings.copy(cloud = it)) })
                }
            }
        }
        Text("Notifications contain only counts and status, not client names or invoice amounts. " +
            "All reminders are calculated on your phone from locally stored records. " +
            "Backup warnings appear only if automatic cloud backups are enabled.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp)
    }
}

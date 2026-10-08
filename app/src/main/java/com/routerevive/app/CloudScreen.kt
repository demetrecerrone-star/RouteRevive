package com.routerevive.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Explicit opt-in for automatic backups; remote imports always require confirmation. */
@Composable
fun CloudScreen(store: LocalStore, onRestoreComplete: () -> Unit) {
    val context = LocalContext.current
    val vault = remember { CloudVault(context) }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(vault.settings()) }
    var projectUrl by remember { mutableStateOf(settings.url) }
    var publicKey by remember { mutableStateOf(settings.publicKey) }
    var accountEmail by remember { mutableStateOf(vault.storedIdentity()?.second.orEmpty()) }
    var accountPassword by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<CloudSession?>(null) }
    var signedInUid by remember { mutableStateOf(vault.storedIdentity()?.first.orEmpty()) }
    var backups by remember { mutableStateOf<List<CloudBackupFile>>(emptyList()) }
    var archivePassword by remember { mutableStateOf("") }
    var selectedRestore by remember { mutableStateOf<CloudBackupFile?>(null) }
    var pendingDelete by remember { mutableStateOf<CloudBackupFile?>(null) }
    var cleanupConfirm by remember { mutableStateOf(false) }
    var retentionKeep by remember { mutableIntStateOf(vault.retentionKeep()) }
    var showUpload by remember { mutableStateOf(false) }
    var autoEnabled by remember { mutableStateOf(vault.automaticEnabled()) }
    var chosenHours by remember { mutableLongStateOf(vault.autoHours().takeIf { it > 0 } ?: 24L) }
    var automaticPassword by remember { mutableStateOf("") }
    var pendingSync by remember { mutableStateOf<String?>(null) }
    var syncStatus by remember { mutableStateOf(vault.lastSyncStatus()) }
    var syncTime by remember { mutableLongStateOf(vault.lastSyncTime()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    // Each operation refreshes the short-lived JWT and stores rotated refresh credentials.
    suspend fun currentSession(): CloudSession = withContext(Dispatchers.IO) {
        CloudAuth.refresh(vault, settings)
    }

    fun startWork(task: suspend () -> String) {
        if (busy) return
        busy = true
        error = ""
        message = ""
        scope.launch {
            try { message = task() }
            catch (e: Exception) { error = e.message ?: "Cloud operation failed." }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PRIVATE CLOUD", color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("RouteRevive cloud backups", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("Password-encrypted snapshots with optional background backup. Cloud changes " +
            "can be imported safely between your own devices after confirmation. " +
            "This is not real-time merging or a shared team account.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)

        if (!settings.valid()) {
            CloudPanel("1. Connect a Supabase project") {
                Text("Create your project and run the private-bucket security SQL first. " +
                    "Enter only the Project URL and anon/publishable key. Never enter a secret or service_role key.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                OutlinedTextField(value = projectUrl, onValueChange = { projectUrl = it.trim().take(190) },
                    label = { Text("https://PROJECT.supabase.co") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = publicKey,
                    onValueChange = { publicKey = it.trim().take(2500) },
                    label = { Text("Publishable or anon key (public)") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
                val candidate = CloudSettings(projectUrl, publicKey)
                Button(enabled = !busy && candidate.valid(), onClick = {
                    runCatching {
                        CloudBackupScheduler.stop(context)
                        vault.saveSettings(candidate)
                        autoEnabled = false
                        settings = vault.settings()
                        session = null
                        signedInUid = ""
                        backups = emptyList()
                    }.onFailure { error = it.message ?: "Settings could not be saved." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save project connection") }
                Text("Setup guide: github.com/demetrecerrone-star/RouteRevive/blob/main/docs/SUPABASE_SETUP.md",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            CloudPanel("Connected project") {
                Text(settings.url, fontSize = 12.sp)
                Text("Cloud connection is optional. Settings do not affect your existing app data.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                if (!busy) TextButton(onClick = {
                    // Clear local sign-in when changing projects. No data is deleted.
                    CloudBackupScheduler.stop(context)
                    runCatching { vault.signOut() }
                    autoEnabled = false
                    settings = CloudSettings("", "")
                    session = null
                    signedInUid = ""
                    backups = emptyList()
                }) { Text("Change project") }
            }

            CloudPanel("2. Business account") {
                if (signedInUid.isBlank()) {
                    OutlinedTextField(value = accountEmail,
                        onValueChange = { accountEmail = it.trim().take(180) },
                        label = { Text("Business email") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = accountPassword,
                        onValueChange = { accountPassword = it.take(300) },
                        label = { Text("Account password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    val valid = accountEmail.contains("@") && accountPassword.length >= 8
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && valid, onClick = {
                            val password = accountPassword
                            accountPassword = ""
                            startWork {
                                val result = withContext(Dispatchers.IO) {
                                    val signed = SupabaseCloud(settings).signIn(accountEmail, password)
                                    vault.saveSession(signed)
                                    signed
                                }
                                session = result
                                signedInUid = result.userId
                                "Signed in. You may now upload a private encrypted backup."
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Sign in") }
                        OutlinedButton(enabled = !busy && valid, onClick = {
                            val password = accountPassword
                            accountPassword = ""
                            startWork {
                                val created = withContext(Dispatchers.IO) {
                                    SupabaseCloud(settings).signUp(accountEmail, password)
                                }
                                if (created != null) {
                                    withContext(Dispatchers.IO) { vault.saveSession(created) }
                                    session = created
                                    signedInUid = created.userId
                                    "Account created and signed in."
                                } else "Check your email to verify your account, then sign in."
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Create account") }
                    }
                    TextButton(enabled = !busy && accountEmail.contains("@"),
                        onClick = {
                            startWork {
                                withContext(Dispatchers.IO) {
                                    SupabaseCloud(settings).requestPasswordReset(accountEmail)
                                }
                                "If this account exists, a password recovery email will be sent. " +
                                    "Follow the provider's browser instructions, then sign in."
                            }
                        }) { Text("Forgot password?") }
                } else {
                    Text("Account: " + accountEmail.ifBlank { vault.storedIdentity()?.second.orEmpty() })
                    Text("Private account ID: " + signedInUid.take(8) + "…",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("Your cloud provider manages the account. The app keeps a Keystore-protected " +
                        "refresh credential, not your account password.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = {
                            startWork {
                                val result = currentSession()
                                session = result
                                signedInUid = result.userId
                                "Cloud login refreshed."
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Check login") }
                        TextButton(enabled = !busy, onClick = {
                            runCatching { vault.signOut() }
                            CloudBackupScheduler.stop(context)
                            autoEnabled = false
                            session = null
                            signedInUid = ""
                            backups = emptyList()
                            accountPassword = ""
                            archivePassword = ""
                            message = "Signed out on this phone. Local data was not touched."
                        }) { Text("Sign out") }
                    }
                }
            }

            if (signedInUid.isNotBlank()) {
                CloudPanel("3. Backup your data to the cloud") {
                    Text("Encrypted backups include customer information, job photos, requests, " +
                        "invoices and recorded payments. They can be restored on a second device " +
                        "with the same account and backup password.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    Button(enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        onClick = { archivePassword = ""; showUpload = true }) {
                        Text("Create & upload encrypted backup")
                    }
                    OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            startWork {
                                val result = currentSession()
                                session = result
                                signedInUid = result.userId
                                backups = withContext(Dispatchers.IO) {
                                    SupabaseCloud(settings).backups(result)
                                }
                                "Showing " + backups.size + " newest cloud backup(s)."
                            }
                        }) { Text("Refresh cloud backup history") }

                    if (backups.isEmpty()) Text("No backups loaded. Select Refresh to list your files.",
                        color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    if (backups.isNotEmpty()) {
                        Text("${backups.size} snapshots · " +
                            "${backups.sumOf { it.size.coerceAtLeast(0L) } / (1024 * 1024)} MiB listed",
                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Newest 50 shown. Retention checks all archives before deletion.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    backups.forEach { snapshot ->
                        HorizontalDivider()
                        Text(snapshot.name.removePrefix("backup-").take(15) + " UTC",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Size: " + snapshot.size / 1024 + " KB · Created: " +
                            snapshot.createdAt.take(19),
                            color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                        Text(if (CloudRules.isAutomaticName(snapshot.name)) "Automatic snapshot"
                            else "Manual snapshot", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !busy, onClick = {
                                archivePassword = ""
                                selectedRestore = snapshot
                            }) { Text("Restore") }
                            val protectedSnapshot = snapshot.name == backups.firstOrNull()?.name ||
                                snapshot.name == backups.firstOrNull {
                                    CloudRules.isAutomaticName(it.name)
                                }?.name || snapshot.name == vault.syncBaseline()?.first
                            if (!protectedSnapshot) TextButton(enabled = !busy,
                                onClick = { pendingDelete = snapshot }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            } else Text("Protected", fontSize = 11.sp,
                                modifier = Modifier.padding(top = 14.dp),
                                color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                CloudPanel("4. Automatic backups & device synchronization") {
                    Text("Opt in to a daily or weekly encrypted backup. The separate backup " +
                        "password is kept using Android Keystore on THIS device. " +
                        "Use the same password on your other device to import snapshots. " +
                        "Only changed data is uploaded; nothing is imported in the background.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    val health = CloudHealthRules.evaluate(
                        autoEnabled, syncStatus, syncTime, vault.autoHours())
                    Text(health.title, fontWeight = FontWeight.Bold,
                        color = if (health.state in listOf(CloudHealthState.ACTION_REQUIRED,
                            CloudHealthState.STALE)) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary)
                    Text(health.detail, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary)
                    if (!autoEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = chosenHours == 24L,
                                onClick = { chosenHours = 24L },
                                label = { Text("Daily") })
                            FilterChip(selected = chosenHours == 168L,
                                onClick = { chosenHours = 168L },
                                label = { Text("Weekly") })
                        }
                        OutlinedTextField(value = automaticPassword,
                            onValueChange = { automaticPassword = it.take(200) },
                            label = { Text("Shared backup password (10+ characters)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(enabled = !busy && automaticPassword.length >= 10,
                            modifier = Modifier.fillMaxWidth(), onClick = {
                                val password = automaticPassword.toCharArray()
                                automaticPassword = ""
                                startWork {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            vault.enableAutomatic(password, chosenHours)
                                            CloudBackupScheduler.schedule(context, chosenHours)
                                        }
                                        autoEnabled = true
                                        "Automatic backup enabled. It will run when network " +
                                            "access and Android background scheduling allow."
                                    } finally { password.fill(0.toChar()) }
                                }
                            }) { Text("Enable encrypted automatic backups") }
                        Text("Only enable on your other device after signing into the SAME cloud " +
                            "account. If that device already has records, review conflicts first.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    } else {
                        Text("Enabled: " + if (vault.autoHours() == 168L) "Weekly" else "Daily",
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                            startWork {
                                val result = withContext(Dispatchers.IO) {
                                    CloudSyncEngine.syncOnce(context, store, vault).also {
                                        vault.recordSyncStatus(it.message)
                                    }
                                }
                                syncStatus = vault.lastSyncStatus()
                                syncTime = vault.lastSyncTime()
                                if (result.choice == CloudSyncChoice.DOWNLOAD)
                                    pendingSync = result.remoteName
                                result.message
                            }
                        }) { Text("Check & sync now") }
                        OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                            syncStatus = vault.lastSyncStatus()
                            syncTime = vault.lastSyncTime()
                        }) { Text("Refresh sync status") }
                        CloudPanel("Automatic backup retention") {
                            Text("Optional: keep the newest automatic snapshots. " +
                                "Cleanup runs after a successful sync. Manual archives " +
                                "are never deleted automatically.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CloudRetentionRules.choices.forEach { keep ->
                                    FilterChip(selected = retentionKeep == keep, onClick = {
                                        runCatching {
                                            vault.setRetentionKeep(keep)
                                            retentionKeep = keep
                                            message = if (keep == 0) "Keep all selected."
                                                else "Keeping $keep newest automatic archives. " +
                                                    "Cleanup runs after the next cloud sync."
                                        }.onFailure { error = it.message ?: "Could not save retention." }
                                    }, label = { Text(if (keep == 0) "All" else "$keep") })
                                }
                            }
                            if (retentionKeep > 0) {
                                OutlinedButton(enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { cleanupConfirm = true }) {
                                    Text("Clean up older archives now")
                                }
                                Text("Other devices may rely on older archives. " +
                                    "Create an offline backup before cleanup.",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        TextButton(enabled = !busy, onClick = {
                            runCatching {
                                CloudBackupScheduler.stop(context)
                                vault.disableAutomatic()
                                autoEnabled = false
                                retentionKeep = 0
                                automaticPassword = ""
                                message = "Automatic backups stopped. Cloud archives were not deleted."
                            }.onFailure { error = it.message ?: "Could not stop automatic backups." }
                        }) { Text("Disable automatic backups") }
                        if (syncStatus.isNotBlank())
                            Text(syncStatus, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary)
                        if (syncTime > 0L)
                            Text("Last successful sync: " +
                                java.time.Instant.ofEpochMilli(syncTime).toString(),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Text("If both devices changed records, syncing pauses. Keep both local " +
                            "copies and use the manual snapshot restore only if you intend to " +
                            "replace one device's records. This is not simultaneous editing.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Secure cloud operation running. Keep RouteRevive open.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }
        if (message.isNotBlank())
            Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        if (error.isNotBlank())
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Text("Cloud backups use AES-GCM with a separate password you choose for each backup. " +
            "Losing that password means the archive cannot be restored, even if you can sign in. " +
            "Keep an offline backup too. Cloud storage costs and limits depend on your provider.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
    }

    pendingDelete?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingDelete = null },
            title = { Text("Permanently delete this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This removes the encrypted cloud archive permanently. " +
                        "You cannot recover it, and another device may depend on it. " +
                        "Local phone records are not deleted.")
                    Text(snapshot.name, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                pendingDelete = null
                startWork {
                    val latest = withContext(Dispatchers.IO) {
                        CloudSyncEngine.deleteOldSnapshot(vault, snapshot.name)
                        val signed = CloudAuth.refresh(vault, settings)
                        SupabaseCloud(settings).backups(signed)
                    }
                    backups = latest
                    "Old encrypted cloud backup permanently deleted."
                }
            }) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) {
                Text("Keep backup")
            } }
        )
    }

    if (cleanupConfirm) AlertDialog(
        onDismissRequest = { if (!busy) cleanupConfirm = false },
        title = { Text("Clean up old automatic backups?") },
        text = { Text("Keep the newest $retentionKeep automatic backups, " +
            "plus protected cloud heads and this device's sync baseline. " +
            "Manual backups are preserved. Deleted archives cannot be recovered. " +
            "Other devices may need an older version.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            cleanupConfirm = false
            startWork {
                val result = withContext(Dispatchers.IO) {
                    val count = CloudSyncEngine.cleanupNow(vault)
                    val signed = CloudAuth.refresh(vault, settings)
                    count to SupabaseCloud(settings).backups(signed)
                }
                backups = result.second
                "${result.first} old automatic backup(s) deleted. Protected archives remain."
            }
        }) { Text("Delete older backups") } },
        dismissButton = { TextButton(onClick = { cleanupConfirm = false }) { Text("Cancel") } }
    )

    if (showUpload) AlertDialog(onDismissRequest = {
        if (!busy) { showUpload = false; archivePassword = "" }
    }, title = { Text("Encrypt & upload complete backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("This takes a fresh snapshot of the data currently saved on this phone, " +
                    "encrypts it with your chosen password, then uploads only the encrypted file. " +
                    "No existing cloud snapshots are overwritten.")
                OutlinedTextField(value = archivePassword,
                    onValueChange = { archivePassword = it.take(200) },
                    label = { Text("Separate backup password (10+ characters)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }, confirmButton = {
            TextButton(enabled = !busy && archivePassword.length >= 10, onClick = {
                val password = archivePassword.toCharArray()
                archivePassword = ""
                showUpload = false
                startWork {
                    val file = File(context.cacheDir,
                        "rr-cloud-upload-" + UUID.randomUUID() + ".rrb")
                    try {
                        val signed = currentSession()
                        session = signed
                        withContext(Dispatchers.IO) {
                            file.outputStream().use {
                                BackupArchive.create(context, store, password, it)
                            }
                            val cloud = SupabaseCloud(settings)
                            cloud.upload(signed, file, CloudRules.newName())
                        }
                        "Encrypted backup uploaded. Refresh the history to see it."
                    } finally {
                        password.fill(0.toChar())
                        file.delete()
                    }
                }
            }) { Text("Encrypt & upload") }
        }, dismissButton = {
            TextButton(onClick = { showUpload = false; archivePassword = "" }) { Text("Cancel") }
        })

    pendingSync?.let { expected ->
        AlertDialog(onDismissRequest = { if (!busy) pendingSync = null },
            title = { Text("Import newer cloud data?") },
            text = {
                Text("This replaces ALL local customers, jobs, photos, payments, requests, " +
                    "appointments and settings on this phone with the latest encrypted snapshot. " +
                    "Only proceed if you have NOT made separate local changes. " +
                    "Create a local backup first if you're unsure.")
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    pendingSync = null
                    startWork {
                        withContext(Dispatchers.IO) {
                            CloudSyncEngine.receive(context, store, vault, expected)
                        }
                        onRestoreComplete()
                        syncStatus = vault.lastSyncStatus()
                        syncTime = vault.lastSyncTime()
                        "Latest encrypted snapshot imported. Local screens were refreshed."
                    }
                }) { Text("Import cloud snapshot") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSync = null }) { Text("Keep local records") }
            })
    }

    selectedRestore?.let { backup ->
        AlertDialog(onDismissRequest = {
            if (!busy) { selectedRestore = null; archivePassword = "" }
        }, title = { Text("Replace ALL local data from cloud?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("WARNING: this replaces customers, campaigns, appointments, booking " +
                        "requests, jobs, payments, and business settings on THIS PHONE. " +
                        "Unsynced local changes will be LOST. Export a local encrypted backup first. " +
                        "Your cloud snapshot itself will not be changed.")
                    Text("Archive: " + backup.name.take(90), fontSize = 11.sp)
                    OutlinedTextField(value = archivePassword,
                        onValueChange = { archivePassword = it.take(200) },
                        label = { Text("Original backup password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && archivePassword.length >= 10, onClick = {
                    val password = archivePassword.toCharArray()
                    archivePassword = ""
                    selectedRestore = null
                    startWork {
                        val file = File(context.cacheDir,
                            "rr-cloud-restore-" + UUID.randomUUID() + ".rrb")
                        try {
                            val signed = currentSession()
                            session = signed
                            withContext(Dispatchers.IO) {
                                SupabaseCloud(settings).download(signed, backup.name, file)
                                file.inputStream().use {
                                    BackupArchive.restore(context, store, password, it)
                                }
                            }
                            withContext(Dispatchers.IO) { vault.clearSyncBaseline() }
                            onRestoreComplete()
                            "Encrypted cloud snapshot restored. Automatic sync will require review."
                        } finally {
                            password.fill(0.toChar())
                            file.delete()
                        }
                    }
                }) { Text("Replace local data") }
            },
            dismissButton = {
                TextButton(onClick = { selectedRestore = null; archivePassword = "" }) {
                    Text("Cancel")
                }
            })
    }
}

@Composable
private fun CloudPanel(title: String, contents: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            contents()
        }
    }
}

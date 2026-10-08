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

/** Cloud actions require explicit button presses. Local data always works offline. */
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
    var showUpload by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    // Each operation refreshes the short-lived JWT and stores rotated refresh credentials.
    suspend fun currentSession(): CloudSession = withContext(Dispatchers.IO) {
        val token = vault.refreshToken() ?: error("Sign in to access private cloud backups.")
        val newSession = SupabaseCloud(settings).refresh(token)
        vault.saveSession(newSession)
        newSession
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
        Text("Optional, end-to-end password-encrypted snapshots. Your customer data and " +
            "photos stay on this phone until YOU upload an encrypted archive. " +
            "This is not automatic live sync or a shared team account.",
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
                        vault.saveSettings(candidate)
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
                    runCatching { vault.signOut() }
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
                                "Found " + backups.size + " cloud backup(s)."
                            }
                        }) { Text("Refresh cloud backup history") }

                    if (backups.isEmpty()) Text("No backups loaded. Select Refresh to list your files.",
                        color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                    backups.forEach { snapshot ->
                        HorizontalDivider()
                        Text(snapshot.name.removePrefix("backup-").take(15) + " UTC",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Size: " + snapshot.size / 1024 + " KB · Created: " +
                            snapshot.createdAt.take(19),
                            color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                        OutlinedButton(enabled = !busy, onClick = {
                            archivePassword = ""
                            selectedRestore = snapshot
                        }) { Text("Restore this snapshot") }
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
                            onRestoreComplete()
                            "Encrypted cloud snapshot restored. Check your customers, jobs and photos."
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

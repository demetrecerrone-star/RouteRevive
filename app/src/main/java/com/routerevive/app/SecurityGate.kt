package com.routerevive.app

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Requests the phone's existing PIN, password or pattern on cold app launch.
 * This is separate from the Android Keystore protection of the local database.
 */
@Composable
fun SecurityGate(store: LocalStore, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val keyguard = remember {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }
    var locked by remember {
        mutableStateOf(store.isAppLockEnabled() && keyguard.isDeviceSecure)
    }
    val authenticate = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) locked = false
    }
    if (!locked) {
        content()
    } else {
        Column(Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RouteRevive is locked", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Text("Confirm your device screen lock to view customer and financial data.")
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                keyguard.createConfirmDeviceCredentialIntent(
                    "Unlock RouteRevive", "Confirm your device credentials"
                )?.let { authenticate.launch(it) }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Unlock with device credentials")
            }
        }
    }
}

package com.amohjustice.amohai

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.MessageDigest

data class LaunchableApp(val label: String, val packageName: String, val intent: Intent)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AmohLauncher() }
    }
}

@Composable
private fun AmohLauncher() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("amoh_launcher", 0) }
    var apps by remember { mutableStateOf(loadApps(context)) }
    var hidden by remember { mutableStateOf(prefs.getStringSet("hidden_apps", emptySet())?.toSet() ?: emptySet()) }
    var screen by remember { mutableStateOf("home") }
    var pinInput by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    val savedHash = prefs.getString("pin_hash", null)

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF5B4BDB))) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text("AMOH AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Your home. Your choices.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))

                when (screen) {
                    "secret" -> {
                        Text(if (savedHash == null) "Create your PIN" else "Enter your PIN", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
                            label = { Text("4–6 digit PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true
                        )
                        if (savedHash == null) {
                            Button(onClick = {
                                if (pinInput.length in 4..6) { firstPin = pinInput; pinInput = ""; screen = "confirm" }
                                else message = "Enter 4 to 6 digits."
                            }) { Text("Continue") }
                        } else {
                            Button(onClick = {
                                if (pinInput.length in 4..6 && hash(pinInput) == savedHash) {
                                    unlocked = true; pinInput = ""; screen = "hidden"
                                } else { message = "Incorrect PIN. Try again."; pinInput = "" }
                            }) { Text("Unlock My Secret") }
                        }
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                    "confirm" -> {
                        Text("Confirm your PIN", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(value = pinInput, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it }, label = { Text("Re-enter PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                        Button(onClick = {
                            if (pinInput == firstPin) {
                                prefs.edit().putString("pin_hash", hash(pinInput)).apply()
                                pinInput = ""; firstPin = ""; message = "PIN created."
                                screen = "secret"
                            } else { message = "PINs do not match."; pinInput = "" }
                        }) { Text("Save PIN") }
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                    "hidden" -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("My Secret", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { screen = "reset" }) { Text("Reset PIN", color = MaterialTheme.colorScheme.error) }
                        }
                        Text("Apps hidden from the AMOH AI app drawer")
                        LazyColumn { items(apps.filter { it.packageName in hidden }) { app ->
                            Text(app.label, Modifier.fillMaxWidth().clickable { launch(context, app) }.padding(vertical = 12.dp))
                        } }
                        TextButton(onClick = { unlocked = false; screen = "home" }) { Text("Lock") }
                    }
                    "reset" -> {
                        Text("Reset PIN", style = MaterialTheme.typography.titleLarge)
                        Text("Create a new PIN after you have unlocked My Secret.")
                        OutlinedTextField(value = pinInput, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it }, label = { Text("New PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                        Button(onClick = {
                            if (pinInput.length in 4..6) {
                                prefs.edit().putString("pin_hash", hash(pinInput)).apply()
                                pinInput = ""; message = "PIN updated."; screen = "hidden"
                            } else message = "Enter 4 to 6 digits."
                        }) { Text("Save new PIN") }
                        TextButton(onClick = { pinInput = ""; screen = "hidden" }) { Text("Cancel") }
                        Text(message)
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { apps = loadApps(context) }) { Text("Refresh apps") }
                            OutlinedButton(onClick = { pinInput = ""; message = ""; screen = "secret" }) { Text("My Secret") }
                        }
                        Text("Choose apps to hide from this launcher", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                        LazyColumn {
                            items(apps.filter { it.packageName != context.packageName }) { app ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(app.label, Modifier.weight(1f).clickable { launch(context, app) })
                                    Checkbox(checked = app.packageName in hidden, onCheckedChange = { checked ->
                                        hidden = if (checked) hidden + app.packageName else hidden - app.packageName
                                        prefs.edit().putStringSet("hidden_apps", hidden).apply()
                                    })
                                }
                                HorizontalDivider()
                            }
                        }
                        Text("Only icons in AMOH AI are hidden. Android Settings and other launchers may still show these apps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun loadApps(context: android.content.Context): List<LaunchableApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(intent, 0)
        .mapNotNull { info: ResolveInfo ->
            val activity = info.activityInfo ?: return@mapNotNull null
            LaunchableApp(info.loadLabel(context.packageManager).toString(), activity.packageName,
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(activity.packageName, activity.name))
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun launch(context: android.content.Context, app: LaunchableApp) {
    runCatching { context.startActivity(app.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun hash(pin: String): String =
    MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }

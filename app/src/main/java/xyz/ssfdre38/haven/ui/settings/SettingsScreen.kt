package xyz.ssfdre38.haven.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.ssfdre38.haven.data.backup.BackupManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val result = BackupManager.exportBackup(context, outputStream)
                        result.fold(
                            onSuccess = {
                                Toast.makeText(context, "Backup created successfully!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                Toast.makeText(context, "Backup failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error writing file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val result = BackupManager.importBackup(context, inputStream)
                        result.fold(
                            onSuccess = {
                                Toast.makeText(context, "Backup restored successfully! App will now update UI.", Toast.LENGTH_LONG).show()
                            },
                            onFailure = { err ->
                                Toast.makeText(context, "Restore failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Load initial settings
    var ashHost by remember { mutableStateFlowOf(sharedPrefs.getString("ash_host", "http://10.0.2.2") ?: "http://10.0.2.2") }
    var ashPort by remember { mutableStateFlowOf(sharedPrefs.getString("ash_port", "18799") ?: "18799") }
    var sdHost by remember { mutableStateFlowOf(sharedPrefs.getString("sd_host", "http://10.0.2.2:8080") ?: "http://10.0.2.2:8080") }
    var temperature by remember { mutableStateFlowOf(sharedPrefs.getString("gen_temp", "0.7") ?: "0.7") }
    var negativePrompt by remember { mutableStateFlowOf(sharedPrefs.getString("gen_neg_prompt", "blurry, low quality, bad anatomy, deformed") ?: "blurry, low quality, bad anatomy, deformed") }
    var userName by remember { mutableStateFlowOf(sharedPrefs.getString("user_name", "User") ?: "User") }
    var autoSpeak by remember { mutableStateFlowOf(sharedPrefs.getBoolean("auto_speak", true)) }
    var quietTimeEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("quiet_time_enabled", false)) }
    var quietTimeStart by remember { mutableStateOf(sharedPrefs.getString("quiet_time_start", "22:00") ?: "22:00") }
    var quietTimeEnd by remember { mutableStateOf(sharedPrefs.getString("quiet_time_end", "07:00") ?: "07:00") }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Haven Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Backend Server Configurations",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = ashHost,
                onValueChange = { ashHost = it },
                label = { Text("Haven Server Host (e.g., http://100.x.y.z)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = ashPort,
                onValueChange = { ashPort = it },
                label = { Text("Haven Server Port (e.g., 18799)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = sdHost,
                onValueChange = { sdHost = it },
                label = { Text("Stable Diffusion Host (e.g., http://100.x.y.z:8080)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text("My Display Name (e.g., your name or nickname)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connection Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Verify connection stability to the server. If a zombie connection is hanging or timeouts occur, resetting will purge OkHttp connection pools and cancel active calls.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (testStatus != null) {
                        Text(
                            text = testStatus!!,
                            color = if (testStatus!!.contains("Successful", ignoreCase = true) || testStatus!!.contains("Reconnected", ignoreCase = true)) Color(0xFF4CAF50) else Color(0xFFF44336),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                isTesting = true
                                testStatus = "Testing connection..."
                                val serverUrl = "${ashHost.trimEnd('/')}:${ashPort.trim()}"
                                xyz.ssfdre38.haven.data.network.HavenHttpClient.testConnection(serverUrl) { result ->
                                    isTesting = false
                                    result.fold(
                                        onSuccess = { msg ->
                                            testStatus = msg
                                        },
                                        onFailure = { err ->
                                            testStatus = "Failed: ${err.message}. Auto-resetting connections..."
                                            xyz.ssfdre38.haven.data.network.HavenHttpClient.resetConnections()
                                            // Retry connection test once after resetting!
                                            xyz.ssfdre38.haven.data.network.HavenHttpClient.testConnection(serverUrl) { retryResult ->
                                                retryResult.fold(
                                                    onSuccess = { retryMsg ->
                                                        testStatus = "Reset Successful & Reconnected!"
                                                    },
                                                    onFailure = { retryErr ->
                                                        testStatus = "Reset Complete. Reconnect Failed: ${retryErr.message}"
                                                    }
                                                )
                                            }
                                        }
                                    )
                                }
                            },
                            enabled = !isTesting,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Text("Test Connection")
                            }
                        }

                        Button(
                            onClick = {
                                xyz.ssfdre38.haven.data.network.HavenHttpClient.resetConnections()
                                testStatus = "Connection pool reset complete!"
                                Toast.makeText(context, "Network connections reset", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Reset Network")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Model Parameters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it },
                label = { Text("Temperature (0.1 - 2.0)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = negativePrompt,
                onValueChange = { negativePrompt = it },
                label = { Text("Default Negative Prompt") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Auto-Play Companion Voice",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = autoSpeak,
                    onCheckedChange = { autoSpeak = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Quiet Time Scheduler",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Quiet Time (Mute Notifications)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = quietTimeEnabled,
                    onCheckedChange = { quietTimeEnabled = it }
                )
            }

            if (quietTimeEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = quietTimeStart,
                        onValueChange = { quietTimeStart = it },
                        label = { Text("Start Time (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("22:00") }
                    )
                    OutlinedTextField(
                        value = quietTimeEnd,
                        onValueChange = { quietTimeEnd = it },
                        label = { Text("End Time (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("07:00") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Companion Pairing",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            val token = remember { mutableStateOf(sharedPrefs.getString("auth_token", null)) }
            Text(
                text = if (token.value != null) "Status: Paired (Token Active)" else "Status: Unpaired (No Active Session)",
                color = if (token.value != null) Color(0xFF4CAF50) else Color(0xFFF44336),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            var pairingCode by remember { mutableStateOf("") }
            var isPairing by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it.take(6).uppercase() },
                    label = { Text("Pairing Code") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isPairing && token.value == null
                )

                Button(
                    onClick = {
                        if (pairingCode.length != 6) {
                            Toast.makeText(context, "Pairing code must be 6 characters", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isPairing = true
                        val serverUrl = "${ashHost.trimEnd('/')}:${ashPort.trim()}"
                        xyz.ssfdre38.haven.data.network.HavenHttpClient.pairDevice(
                            serverUrl = serverUrl,
                            pairingCode = pairingCode,
                            deviceName = android.os.Build.MODEL,
                            onResult = { result ->
                                isPairing = false
                                result.fold(
                                    onSuccess = { tokenStr ->
                                        sharedPrefs.edit().putString("auth_token", tokenStr).apply()
                                        token.value = tokenStr
                                        pairingCode = ""
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            Toast.makeText(context, "Device Paired Successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onFailure = { err ->
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            Toast.makeText(context, "Pairing Failed: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                            }
                        )
                    },
                    enabled = !isPairing && token.value == null && pairingCode.length == 6,
                    modifier = Modifier.height(56.dp)
                ) {
                    if (isPairing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Pair")
                    }
                }

                if (token.value != null) {
                    Button(
                        onClick = {
                            sharedPrefs.edit().remove("auth_token").apply()
                            token.value = null
                            Toast.makeText(context, "Device unpaired", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Unpair")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Backup & Restore (.haven Packages)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Export or restore all companions, message history, memories, and custom portraits/avatars into a single portable file.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                createBackupLauncher.launch("haven-backup-${System.currentTimeMillis() / 1000}.haven")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Create Backup")
                        }

                        Button(
                            onClick = {
                                restoreBackupLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Restore Backup")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "System Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Request unrestricted battery usage to prevent VPN (NetBird) and server connection timeouts when the app is idle.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            try {
                                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                                val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
                                if (isIgnoring) {
                                    Toast.makeText(context, "Battery optimizations are already disabled!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Could not open battery settings: ${ex.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Battery Optimizations")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    sharedPrefs.edit().apply {
                        putString("ash_host", ashHost.trim())
                        putString("ash_port", ashPort.trim())
                        putString("sd_host", sdHost.trim())
                        putString("gen_temp", temperature.trim())
                        putString("gen_neg_prompt", negativePrompt.trim())
                        putString("user_name", userName.trim())
                        putBoolean("auto_speak", autoSpeak)
                        putBoolean("quiet_time_enabled", quietTimeEnabled)
                        putString("quiet_time_start", quietTimeStart.trim())
                        putString("quiet_time_end", quietTimeEnd.trim())
                        apply()
                    }
                    Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                    onBackClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text("Save & Apply Settings")
            }
        }
    }
}

// Utility extension helper to work with mutable state in Compose
fun <T> mutableStateFlowOf(value: T) = mutableStateOf(value)

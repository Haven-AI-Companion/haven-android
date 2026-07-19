package xyz.ssfdre38.haven.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import xyz.ssfdre38.haven.data.backup.BackupManager
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale

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
    var userGender by remember { mutableStateFlowOf(sharedPrefs.getString("user_gender", "Unspecified") ?: "Unspecified") }
    var userAvatarPath by remember { mutableStateFlowOf(sharedPrefs.getString("user_avatar_path", "") ?: "") }
    var autoSpeak by remember { mutableStateFlowOf(sharedPrefs.getBoolean("auto_speak", true)) }
    var enableProactive by remember { mutableStateOf(sharedPrefs.getBoolean("enable_proactive", true)) }
    var quietTimeEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("quiet_time_enabled", false)) }
    var quietTimeStart by remember { mutableStateOf(sharedPrefs.getString("quiet_time_start", "22:00") ?: "22:00") }
    var quietTimeEnd by remember { mutableStateOf(sharedPrefs.getString("quiet_time_end", "07:00") ?: "07:00") }
    var enableBubbles by remember { mutableStateOf(sharedPrefs.getBoolean("enable_bubbles", true)) }
    var enableOverlay by remember { mutableStateOf(sharedPrefs.getBoolean("enable_overlay", false) && android.provider.Settings.canDrawOverlays(context)) }
    var shareDeviceStatus by remember { mutableStateOf(sharedPrefs.getBoolean("share_device_status", false)) }
    var enableWakeWord by remember { mutableStateOf(sharedPrefs.getBoolean("enable_wake_word", false)) }
    var customWakeWord by remember { mutableStateOf(sharedPrefs.getString("custom_wake_word", "") ?: "") }
    var shareLocalTime by remember { mutableStateOf(sharedPrefs.getBoolean("share_local_time", true)) }
    var enableLongTermMemory by remember { mutableStateOf(sharedPrefs.getBoolean("enable_long_term_memory", true)) }
    var freezeRelationshipLevel by remember { mutableStateOf(sharedPrefs.getBoolean("freeze_relationship_level", false)) }
    var shareActiveMedia by remember { mutableStateOf(sharedPrefs.getBoolean("share_active_media", false)) }
    var shareCityLocation by remember { mutableStateOf(sharedPrefs.getBoolean("share_city_location", false)) }
    var shareAppTheme by remember { mutableStateOf(sharedPrefs.getBoolean("share_app_theme", false)) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    
    val database = remember { xyz.ssfdre38.haven.data.database.AppDatabase.getInstance(context) }
    val dao = remember { database.havenDao() }
    val repository = remember { xyz.ssfdre38.haven.data.DefaultDataRepository(dao) }
    var activeCompanionVrmPath by remember { mutableStateOf<String?>(null) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val extension = if (mimeType.contains("png")) ".png" else ".jpg"
                        val fileName = "avatar_$extension"
                        val tokenStr = sharedPrefs.getString("auth_token", null)
                        val serverUrl = "${ashHost.trimEnd('/')}:${ashPort.trim()}"
                        if (tokenStr != null) {
                            xyz.ssfdre38.haven.data.network.HavenHttpClient.uploadUserAvatar(
                                serverUrl = serverUrl,
                                token = tokenStr,
                                fileBytes = bytes,
                                fileName = fileName,
                                mimeType = mimeType,
                                onResult = { result ->
                                    result.fold(
                                        onSuccess = { remotePath ->
                                            val fullRemoteUrl = if (remotePath.startsWith("http")) remotePath else "${serverUrl.trimEnd('/')}$remotePath"
                                            sharedPrefs.edit().putString("user_avatar_path", fullRemoteUrl).apply()
                                            userAvatarPath = fullRemoteUrl
                                            (context as? android.app.Activity)?.runOnUiThread {
                                                Toast.makeText(context, "Avatar uploaded successfully!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onFailure = { err ->
                                            (context as? android.app.Activity)?.runOnUiThread {
                                                Toast.makeText(context, "Avatar upload failed: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    )
                                }
                            )
                        } else {
                            (context as? android.app.Activity)?.runOnUiThread {
                                Toast.makeText(context, "Please pair your device first to upload avatar", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        dao.getAllCharacters().collect { list ->
            val activeChar = list.maxByOrNull { it.relationshipXp } ?: list.firstOrNull()
            activeCompanionVrmPath = activeChar?.vrmModelPath
            
            // If the active companion has no VRM model, turn off the overlay automatically
            val hasVrm = activeChar?.vrmModelPath?.let { java.io.File(it).exists() } == true
            if (!hasVrm && sharedPrefs.getBoolean("enable_overlay", false)) {
                sharedPrefs.edit().putBoolean("enable_overlay", false).apply()
                enableOverlay = false
                context.stopService(Intent(context, xyz.ssfdre38.haven.service.FloatingCompanionService::class.java))
            }
        }
    }
    
    val hasVrmModel = remember(activeCompanionVrmPath) {
        activeCompanionVrmPath?.let { java.io.File(it).exists() } == true
    }
    
    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var sdStatus by remember { mutableStateOf("Not tested") }
    var ttsStatus by remember { mutableStateOf("Not tested") }
    var dbMessageCount by remember { mutableStateOf(0) }
    var dbMemoryCount by remember { mutableStateOf(0) }
    var dbFileSizeMb by remember { mutableStateOf(0.0) }

    // Cache Stats
    var cacheModelsSize by remember { mutableStateOf(0.0) }
    var cacheModelsCount by remember { mutableStateOf(0) }
    var cacheGenSize by remember { mutableStateOf(0.0) }
    var cacheGenCount by remember { mutableStateOf(0) }
    var cacheCompanionSize by remember { mutableStateOf(0.0) }
    var cacheCompanionCount by remember { mutableStateOf(0) }
    var cacheAvatarsSize by remember { mutableStateOf(0.0) }
    var cacheAvatarsCount by remember { mutableStateOf(0) }

    fun getDirSizeAndCount(dirName: String): Pair<Double, Int> {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = java.io.File(baseDir, dirName)
        if (!dir.exists() || !dir.isDirectory) return Pair(0.0, 0)
        
        var totalBytes = 0L
        var fileCount = 0
        
        fun walk(file: java.io.File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { walk(it) }
            } else {
                totalBytes += file.length()
                fileCount++
            }
        }
        
        walk(dir)
        val sizeMb = totalBytes.toDouble() / (1024 * 1024)
        return Pair(Math.round(sizeMb * 100.0) / 100.0, fileCount)
    }

    fun clearDirContent(dirName: String) {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = java.io.File(baseDir, dirName)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
        }
    }

    fun refreshCacheStats() {
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val models = getDirSizeAndCount("vrm_models")
            val gen = getDirSizeAndCount("generated")
            val companion = getDirSizeAndCount("companion")
            val avatars = getDirSizeAndCount("avatars")
            
            cacheModelsSize = models.first
            cacheModelsCount = models.second
            cacheGenSize = gen.first
            cacheGenCount = gen.second
            cacheCompanionSize = companion.first
            cacheCompanionCount = companion.second
            cacheAvatarsSize = avatars.first
            cacheAvatarsCount = avatars.second
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val msgCount = dao.getMessageCount()
                val memCount = dao.getMemoryCount()
                val dbFile = context.getDatabasePath("haven_database")
                val sizeBytes = if (dbFile.exists()) dbFile.length() else 0L
                val sizeMb = sizeBytes.toDouble() / (1024 * 1024)
                
                dbMessageCount = msgCount
                dbMemoryCount = memCount
                dbFileSizeMb = Math.round(sizeMb * 100.0) / 100.0
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        refreshCacheStats()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.currentStateFlow.collect { state ->
            if (state == androidx.lifecycle.Lifecycle.State.RESUMED) {
                val hasPerm = android.provider.Settings.canDrawOverlays(context)
                val prefEnabled = sharedPrefs.getBoolean("enable_overlay", false)
                enableOverlay = prefEnabled && hasPerm
                if (prefEnabled && hasPerm) {
                    context.startService(Intent(context, xyz.ssfdre38.haven.service.FloatingCompanionService::class.java))
                } else if (!hasPerm) {
                    context.stopService(Intent(context, xyz.ssfdre38.haven.service.FloatingCompanionService::class.java))
                }
            }
        }
    }

    val mainGradient = remember {
        listOf(Color(0xFF1E1035), Color(0xFF0C051A))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(mainGradient))
    ) {
        // Floating premium background animations
        val infiniteTransition = rememberInfiniteTransition(label = "background_orbs")
        
        val orb1X by infiniteTransition.animateFloat(
            initialValue = -50f,
            targetValue = 180f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb1X"
        )
        val orb1Y by infiniteTransition.animateFloat(
            initialValue = -50f,
            targetValue = 350f,
            animationSpec = infiniteRepeatable(
                animation = tween(14000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb1Y"
        )
        
        val orb2X by infiniteTransition.animateFloat(
            initialValue = 220f,
            targetValue = -60f,
            animationSpec = infiniteRepeatable(
                animation = tween(16000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb2X"
        )
        val orb2Y by infiniteTransition.animateFloat(
            initialValue = 450f,
            targetValue = 60f,
            animationSpec = infiniteRepeatable(
                animation = tween(11000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb2Y"
        )

        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
        ) {
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(alpha = 0.15f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(orb1X.dp.toPx(), orb1Y.dp.toPx()),
                    radius = 300.dp.toPx()
                ),
                radius = 300.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(orb1X.dp.toPx(), orb1Y.dp.toPx())
            )
            
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color(0xFFD946EF).copy(alpha = 0.12f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(orb2X.dp.toPx(), orb2Y.dp.toPx()),
                    radius = 280.dp.toPx()
                ),
                radius = 280.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(orb2X.dp.toPx(), orb2Y.dp.toPx())
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Haven Settings", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
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

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "User Profile Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                                .clickable {
                                    avatarPickerLauncher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (userAvatarPath.isNotEmpty()) {
                                AsyncImage(
                                    model = if (userAvatarPath.startsWith("http")) userAvatarPath else java.io.File(userAvatarPath),
                                    contentDescription = "User Avatar",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "No Avatar",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Profile Picture",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap to upload your avatar photo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    var genderDropdownExpanded by remember { mutableStateOf(false) }
                    val genders = listOf("Male", "Female", "Non-Binary", "Unspecified")

                    ExposedDropdownMenuBox(
                        expanded = genderDropdownExpanded,
                        onExpandedChange = { genderDropdownExpanded = !genderDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = userGender,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Gender") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = genderDropdownExpanded,
                            onDismissRequest = { genderDropdownExpanded = false }
                        ) {
                            genders.forEach { gender ->
                                DropdownMenuItem(
                                    text = { Text(gender) },
                                    onClick = {
                                        userGender = gender
                                        genderDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connection & System Diagnostics",
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
                        text = "Verify connection stability, network latency, and local database statistics.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Haven Core Server:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = testStatus ?: "Not tested",
                                color = if (testStatus?.contains("Successful", ignoreCase = true) == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Stable Diffusion API:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = sdStatus,
                                color = if (sdStatus.contains("Successful", ignoreCase = true)) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TTS Voices Endpoint:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = ttsStatus,
                                color = if (ttsStatus.contains("Successful", ignoreCase = true)) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                    Text("Local SQLite Database Stats", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Messages Saved:", style = MaterialTheme.typography.bodyMedium)
                            Text("$dbMessageCount messages", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Long-term Memories:", style = MaterialTheme.typography.bodyMedium)
                            Text("$dbMemoryCount memories", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Database File Size:", style = MaterialTheme.typography.bodyMedium)
                            Text("$dbFileSizeMb MB", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                isTesting = true
                                testStatus = "Testing core..."
                                sdStatus = "Testing SD..."
                                ttsStatus = "Testing TTS..."
                                val serverUrl = "${ashHost.trimEnd('/')}:${ashPort.trim()}"
                                
                                // 1. Test Core Server
                                xyz.ssfdre38.haven.data.network.HavenHttpClient.testConnectionLatency(serverUrl) { result ->
                                    result.fold(
                                        onSuccess = { (status, latency) ->
                                            testStatus = "$status (${latency}ms)"
                                        },
                                        onFailure = { err ->
                                            testStatus = "Failed: ${err.message}"
                                        }
                                    )
                                }

                                // 2. Test SD Server
                                val sdHealthUrl = if (sdHost.contains("/health") || sdHost.contains("/v1")) sdHost else "${sdHost.trimEnd('/')}/v1/models"
                                xyz.ssfdre38.haven.data.network.HavenHttpClient.testConnectionLatency(sdHealthUrl) { result ->
                                    result.fold(
                                        onSuccess = { (status, latency) ->
                                            sdStatus = "$status (${latency}ms)"
                                        },
                                        onFailure = { err ->
                                            sdStatus = "Failed: ${err.message}"
                                        }
                                    )
                                }

                                // 3. Test TTS
                                val ttsHealthUrl = "${serverUrl.trimEnd('/')}/api/tts/voices"
                                xyz.ssfdre38.haven.data.network.HavenHttpClient.testConnectionLatency(ttsHealthUrl) { result ->
                                    isTesting = false
                                    result.fold(
                                        onSuccess = { (status, latency) ->
                                            ttsStatus = "$status (${latency}ms)"
                                        },
                                        onFailure = { err ->
                                            ttsStatus = "Failed: ${err.message}"
                                        }
                                    )
                                }

                                // 4. Refresh DB Size
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val msgCount = dao.getMessageCount()
                                        val memCount = dao.getMemoryCount()
                                        val dbFile = context.getDatabasePath("haven_database")
                                        val sizeBytes = if (dbFile.exists()) dbFile.length() else 0L
                                        val sizeMb = sizeBytes.toDouble() / (1024 * 1024)
                                        dbMessageCount = msgCount
                                        dbMemoryCount = memCount
                                        dbFileSizeMb = Math.round(sizeMb * 100.0) / 100.0
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            enabled = !isTesting,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Text("Run Tests")
                            }
                        }

                        Button(
                            onClick = {
                                xyz.ssfdre38.haven.data.network.HavenHttpClient.resetConnections()
                                testStatus = "Pool reset!"
                                Toast.makeText(context, "Network connections reset", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Reset Network")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    var isSyncing by remember { mutableStateOf(false) }

                    Button(
                        onClick = {
                            isSyncing = true
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    forceServerSync(context, repository)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "Server sync completed successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    isSyncing = false
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onTertiary, strokeWidth = 2.dp)
                        } else {
                            Text("Force Server Sync")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cache & Asset Management",
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
                        text = "Manage storage usage for downloaded assets, custom portraits, and 3D companion models.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Generated Chat Images:", style = MaterialTheme.typography.bodyMedium)
                            Text("$cacheGenSize MB ($cacheGenCount files)", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("3D Avatar Models:", style = MaterialTheme.typography.bodyMedium)
                            Text("$cacheModelsSize MB ($cacheModelsCount files)", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Companion Portraits:", style = MaterialTheme.typography.bodyMedium)
                            Text("$cacheCompanionSize MB ($cacheCompanionCount files)", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Imported Tavern Cards:", style = MaterialTheme.typography.bodyMedium)
                            Text("$cacheAvatarsSize MB ($cacheAvatarsCount files)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    clearDirContent("generated")
                                    refreshCacheStats()
                                }
                                Toast.makeText(context, "Chat images cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Images")
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    clearDirContent("vrm_models")
                                    refreshCacheStats()
                                }
                                Toast.makeText(context, "3D models cleared", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Models")
                        }
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                clearDirContent("generated")
                                clearDirContent("vrm_models")
                                clearDirContent("companion")
                                clearDirContent("avatars")
                                refreshCacheStats()
                            }
                            Toast.makeText(context, "All assets cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear All Asset Cache")
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Allow Companions to Auto-Talk",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Allow companions to proactively check in or text you after periods of silence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enableProactive,
                    onCheckedChange = { enableProactive = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Conversational Chat Bubbles",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = enableBubbles,
                    onCheckedChange = { enableBubbles = it }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Voice Wake Word",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Listen for 'Hey Nova' or 'Hey Hasaji' to hands-free voice call.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enableWakeWord,
                    onCheckedChange = { enableWakeWord = it }
                )
            }

            if (enableWakeWord) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customWakeWord,
                    onValueChange = { customWakeWord = it },
                    label = { Text("Custom Wake Phrase") },
                    placeholder = { Text("e.g. hey computer") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                onClick = { showPrivacyDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🛡️ Privacy & Context Sharing",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Manage sharing settings for local time, battery level, memory extraction, and relationship levels.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Privacy Sub-menu",
                        tint = Color.White
                    )
                }
            }

            if (showPrivacyDialog) {
                AlertDialog(
                    onDismissRequest = { showPrivacyDialog = false },
                    title = {
                        Text(
                            text = "Privacy & Context Sharing",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Customize what data is sent to the C# server and LLM backend during your chat sessions.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Share Device Status",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Send battery and charging states.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = shareDeviceStatus,
                                    onCheckedChange = { shareDeviceStatus = it }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Share Local Time of Day",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Provide hours/minutes for time awareness.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = shareLocalTime,
                                    onCheckedChange = { shareLocalTime = it }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Long-Term Memory",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Automatically extract and recall facts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = enableLongTermMemory,
                                    onCheckedChange = { enableLongTermMemory = it }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Freeze Relationship XP",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Pause level progression and status title changes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = freezeRelationshipLevel,
                                    onCheckedChange = { freezeRelationshipLevel = it }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Share Active Media / Music",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Share active media playing in the background (artist/title).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = shareActiveMedia,
                                    onCheckedChange = { shareActiveMedia = it }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Share City Location",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Provide nearest major city from timezone for geographic context.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = shareCityLocation,
                                    onCheckedChange = { shareCityLocation = it }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Share App Theme Style",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Provide Dark Mode / Light Mode status.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = shareAppTheme,
                                    onCheckedChange = { shareAppTheme = it }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPrivacyDialog = false }) {
                            Text("Done")
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Floating 3D Companion Overlay",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (hasVrmModel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    if (!hasVrmModel) {
                        Text(
                            text = "(No 3D model attached to active companion)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Switch(
                    checked = enableOverlay && hasVrmModel,
                    enabled = hasVrmModel,
                    onCheckedChange = { checked ->
                        if (checked) {
                            sharedPrefs.edit().putBoolean("enable_overlay", true).apply()
                            if (!android.provider.Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                                Toast.makeText(context, "Grant 'Display over other apps' permission to enable", Toast.LENGTH_LONG).show()
                            } else {
                                enableOverlay = true
                                context.startService(Intent(context, xyz.ssfdre38.haven.service.FloatingCompanionService::class.java))
                            }
                        } else {
                            enableOverlay = false
                            sharedPrefs.edit().putBoolean("enable_overlay", false).apply()
                            context.stopService(Intent(context, xyz.ssfdre38.haven.service.FloatingCompanionService::class.java))
                        }
                    }
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
                        putString("user_gender", userGender)
                        putString("user_avatar_path", userAvatarPath)
                        putBoolean("auto_speak", autoSpeak)
                        putBoolean("enable_proactive", enableProactive)
                        putBoolean("quiet_time_enabled", quietTimeEnabled)
                        putString("quiet_time_start", quietTimeStart.trim())
                        putString("quiet_time_end", quietTimeEnd.trim())
                        putBoolean("enable_bubbles", enableBubbles)
                        putBoolean("enable_overlay", enableOverlay)
                        putBoolean("share_device_status", shareDeviceStatus)
                        putBoolean("enable_wake_word", enableWakeWord)
                        putString("custom_wake_word", customWakeWord.trim())
                        putBoolean("share_local_time", shareLocalTime)
                        putBoolean("enable_long_term_memory", enableLongTermMemory)
                        putBoolean("freeze_relationship_level", freezeRelationshipLevel)
                        putBoolean("share_active_media", shareActiveMedia)
                        putBoolean("share_city_location", shareCityLocation)
                        putBoolean("share_app_theme", shareAppTheme)
                        apply()
                    }

                    val wwIntent = Intent(context, xyz.ssfdre38.haven.service.WakeWordService::class.java)
                    if (enableWakeWord) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(wwIntent)
                        } else {
                            context.startService(wwIntent)
                        }
                    } else {
                        context.stopService(wwIntent)
                    }

                    // Background profile sync to Haven server
                    val tokenStr = sharedPrefs.getString("auth_token", null)
                    val serverUrl = "${ashHost.trimEnd('/')}:${ashPort.trim()}"
                    if (tokenStr != null) {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                xyz.ssfdre38.haven.data.network.HavenHttpClient.updateUserProfile(
                                    serverUrl = serverUrl,
                                    token = tokenStr,
                                    displayName = userName.trim(),
                                    gender = userGender,
                                    onResult = { /* fire-and-forget sync */ }
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
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
}

private suspend fun forceServerSync(context: android.content.Context, repository: xyz.ssfdre38.haven.data.DataRepository) {
    // 1. Process sync queue first
    xyz.ssfdre38.haven.data.sync.SyncQueueManager.processQueue(context)

    val sharedPrefs = context.getSharedPreferences("haven_prefs", android.content.Context.MODE_PRIVATE)
    val host = sharedPrefs.getString("ash_host", "") ?: ""
    val port = sharedPrefs.getString("ash_port", "") ?: ""
    val token = sharedPrefs.getString("auth_token", "") ?: ""
    if (host.isBlank() || port.isBlank()) return

    val formattedHost = if (host.startsWith("http")) host.trimEnd('/') else "http://${host.trimEnd('/')}"
    val serverUrl = "$formattedHost:${port.trim()}"

    // Helper to get case-insensitive json values
    fun getJsonStringCaseInsensitive(obj: org.json.JSONObject, vararg keys: String, fallback: String = ""): String {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.getString(key)
            }
        }
        return fallback
    }

    // 2. Sync/Load Companions from server
    try {
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("$serverUrl/api/companions")
            .addHeader("Authorization", "Bearer $token")
            .build()
            
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val jsonArray = org.json.JSONArray(body)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = getJsonStringCaseInsensitive(obj, "name", "Name")
                    if (name.isBlank()) continue

                    val avatarPath = getJsonStringCaseInsensitive(obj, "avatar_path", "avatarPath", "AvatarPath").ifBlank { null }
                    val voiceId = getJsonStringCaseInsensitive(obj, "voice_id", "voiceId", "VoiceId", fallback = "en_US-amy-medium")
                    val description = getJsonStringCaseInsensitive(obj, "description", "Description")
                    val personality = getJsonStringCaseInsensitive(obj, "personality", "Personality")
                    val scenario = getJsonStringCaseInsensitive(obj, "scenario", "Scenario")
                    val firstMessage = getJsonStringCaseInsensitive(obj, "first_message", "firstMessage", "FirstMessage")
                    val systemPrompt = getJsonStringCaseInsensitive(obj, "system_prompt", "systemPrompt", "SystemPrompt")
                    val currentOutfit = getJsonStringCaseInsensitive(obj, "current_outfit", "currentOutfit", "CurrentOutfit")
                    val currentLocation = getJsonStringCaseInsensitive(obj, "current_location", "currentLocation", "CurrentLocation")
                    val currentMood = getJsonStringCaseInsensitive(obj, "current_mood", "currentMood", "CurrentMood")
                    val bodyType = getJsonStringCaseInsensitive(obj, "body_type", "bodyType", "BodyType")
                    val bodyShape = getJsonStringCaseInsensitive(obj, "body_shape", "bodyShape", "BodyShape")
                    val clothingState = getJsonStringCaseInsensitive(obj, "clothing_state", "clothingState", "ClothingState")

                    val resolvedAvatarUrl = if (!avatarPath.isNullOrBlank()) {
                        if (avatarPath.startsWith("/")) {
                            "$serverUrl$avatarPath"
                        } else {
                            avatarPath
                        }
                    } else null

                    var finalAvatarPath = avatarPath
                    if (!resolvedAvatarUrl.isNullOrBlank()) {
                        try {
                            val localPath = xyz.ssfdre38.haven.data.network.HavenHttpClient.downloadImage(context, resolvedAvatarUrl, name)
                            if (localPath != null) {
                                finalAvatarPath = localPath
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    val existing = repository.getCharacterByName(name)
                    if (existing != null) {
                        val updated = existing.copy(
                            voiceId = if (voiceId.isNotBlank()) voiceId else existing.voiceId,
                            description = if (description.isNotBlank()) description else existing.description,
                            personality = if (personality.isNotBlank()) personality else existing.personality,
                            scenario = if (scenario.isNotBlank()) scenario else existing.scenario,
                            systemPrompt = if (systemPrompt.isNotBlank()) systemPrompt else existing.systemPrompt,
                            avatarPath = if (!avatarPath.isNullOrBlank() && (existing.avatarPath.isNullOrBlank() || !java.io.File(existing.avatarPath).exists() || existing.avatarPath.startsWith("/uploads/") || existing.avatarPath.startsWith("http"))) finalAvatarPath else existing.avatarPath,
                            bodyType = if (bodyType.isNotBlank()) bodyType else existing.bodyType,
                            bodyShape = if (bodyShape.isNotBlank()) bodyShape else existing.bodyShape,
                            currentOutfit = if (currentOutfit.isNotBlank()) currentOutfit else existing.currentOutfit,
                            currentLocation = if (currentLocation.isNotBlank()) currentLocation else existing.currentLocation,
                            currentMood = if (currentMood.isNotBlank()) currentMood else existing.currentMood,
                            clothingState = if (clothingState.isNotBlank()) clothingState else existing.clothingState
                        )
                        repository.updateCharacter(updated)
                    } else {
                        val deletedSet = sharedPrefs.getStringSet("deleted_companions", emptySet()) ?: emptySet()
                        if (!deletedSet.contains(name)) {
                            val newChar = xyz.ssfdre38.haven.data.database.CharacterEntity(
                                name = name,
                                avatarPath = finalAvatarPath,
                                voiceId = voiceId,
                                description = description,
                                personality = personality,
                                scenario = scenario,
                                firstMessage = firstMessage,
                                systemPrompt = systemPrompt,
                                currentOutfit = currentOutfit,
                                currentLocation = currentLocation,
                                currentMood = currentMood,
                                bodyType = bodyType,
                                bodyShape = bodyShape,
                                clothingState = clothingState
                            )
                            val newId = repository.insertCharacter(newChar).toInt()
                            if (firstMessage.isNotBlank()) {
                                repository.insertMessage(
                                    xyz.ssfdre38.haven.data.database.MessageEntity(
                                        characterId = newId,
                                        sender = "character",
                                        text = firstMessage
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 3. Sync/Load Groups from server
    try {
        val serverGroups = xyz.ssfdre38.haven.data.network.HavenHttpClient.getGroups(serverUrl, token)
        val serverUuids = serverGroups.map { obj ->
            if (obj.has("id")) obj.getString("id") else obj.getString("uuid")
        }.toSet()
        
        serverGroups.forEach { obj ->
            val uuid = if (obj.has("id")) obj.getString("id") else obj.getString("uuid")
            val name = obj.getString("name")
            val characterNamesStr = if (obj.has("character_names")) obj.getString("character_names") else obj.getString("characterNames")
            
            val names = characterNamesStr.split(",").map { it.trim() }
            val resolvedIds = names.mapNotNull { charName ->
                repository.getCharacterByName(charName)?.id
            }
            val newIdsStr = resolvedIds.joinToString(",")
            
            val scenario = getJsonStringCaseInsensitive(obj, "scenario", "Scenario")
            val systemPrompt = getJsonStringCaseInsensitive(obj, "system_prompt", "systemPrompt", "SystemPrompt")
            
            val existing = repository.getGroupChatByUuid(uuid)
            if (existing == null) {
                repository.insertGroupChat(
                    xyz.ssfdre38.haven.data.database.GroupChatEntity(
                        name = name,
                        characterIdsString = newIdsStr,
                        uuid = uuid,
                        scenario = scenario,
                        systemPrompt = systemPrompt
                    )
                )
            } else {
                if (existing.name != name || existing.characterIdsString != newIdsStr || existing.scenario != scenario || existing.systemPrompt != systemPrompt) {
                    repository.insertGroupChat(
                        existing.copy(
                            name = name,
                            characterIdsString = newIdsStr,
                            scenario = scenario,
                            systemPrompt = systemPrompt
                        )
                    )
                }
            }
        }
        
        // Cleanup groups deleted on server
        val localGroups = repository.getAllGroupChats().first()
        for (grp in localGroups) {
            val uuid = grp.uuid
            if (uuid != null && !serverUuids.contains(uuid)) {
                repository.deleteGroupChat(grp)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Utility extension helper to work with mutable state in Compose
fun <T> mutableStateFlowOf(value: T) = mutableStateOf(value)

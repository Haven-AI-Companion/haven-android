package xyz.ssfdre38.haven.ui.chat

import xyz.ssfdre38.haven.ui.components.VrmAvatarView

import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import android.content.Context
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.conflate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.CharacterEntity
import xyz.ssfdre38.haven.data.database.MessageEntity
import java.io.File
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    characterId: Int,
    repository: DataRepository,
    onBackClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDiaryClick: (String) -> Unit,
    onVoiceCallClick: () -> Unit = {},
    onMemoryVaultClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    chatViewModel: ChatViewModel = viewModel(key = "chat_$characterId") { ChatViewModel(characterId, repository) }
) {
    val context = LocalContext.current
    val character by chatViewModel.character.collectAsStateWithLifecycle()
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by chatViewModel.isGenerating.collectAsStateWithLifecycle()
    val isSpeaking by chatViewModel.isSpeaking.collectAsStateWithLifecycle()
    val errorMessage by chatViewModel.errorMessage.collectAsStateWithLifecycle()

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var wasGenerating by remember { mutableStateOf(false) }
    LaunchedEffect(isGenerating) {
        if (wasGenerating && !isGenerating) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
        wasGenerating = isGenerating
    }

    val scope = rememberCoroutineScope()
    var editingMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var immersiveMode by remember { mutableStateOf(false) }
    var blurBackdrop by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show()
            chatViewModel.clearError()
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (chatViewModel.scrollIndex != -1) chatViewModel.scrollIndex else 0,
        initialFirstVisibleItemScrollOffset = if (chatViewModel.scrollIndex != -1) chatViewModel.scrollOffset else 0
    )

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                chatViewModel.scrollIndex = index
                chatViewModel.scrollOffset = offset
            }
    }

    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var activeFullscreenImage by remember { mutableStateOf<Any?>(null) }

    var showWallpaperPickerDialog by remember { mutableStateOf(false) }
    var galleryWallpaperFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    var previousLevel by remember { mutableStateOf<Int?>(null) }
    var showLevelUpDialog by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(character?.relationshipXp) {
        val xp = character?.relationshipXp ?: 0
        val currentLevel = (xp / 100) + 1
        if (previousLevel != null && currentLevel > previousLevel!!) {
            showLevelUpDialog = currentLevel
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
        previousLevel = currentLevel
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    val requestPortrait = {
        val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        val sdHost = prefs.getString("sd_host", null)
        if (sdHost.isNullOrBlank()) {
            Toast.makeText(context, "Configure Stable Diffusion server in Settings first", Toast.LENGTH_SHORT).show()
        } else {
            character?.let { char ->
                val loc = char.currentLocation.ifBlank { "cozy room" }
                val outfit = char.currentOutfit.ifBlank { "casual clothing" }
                val moodStr = if (char.currentMood.isNotBlank()) "${char.currentMood} expression, " else ""
                val imagePrompt = "${char.name}, digital art portrait, highly detailed, ${moodStr}standing in $loc, wearing $outfit"
                chatViewModel.generateImage(context.applicationContext, sdHost, imagePrompt)
            }
        }
    }

    // Auto-scroll to latest message
    var previousSize by remember { mutableStateOf(messages.size) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val delta = messages.size - previousSize
            if (delta == 1) {
                listState.animateScrollToItem(messages.size - 1)
            } else {
                listState.scrollToItem(messages.size - 1)
            }
        }
        previousSize = messages.size
    }

    // Auto-scroll while companion response is streaming
    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            snapshotFlow { messages.lastOrNull()?.text ?: "" }
                .conflate()
                .collect { text ->
                    if (messages.isNotEmpty()) {
                        val layoutInfo = listState.layoutInfo
                        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                        val isAtBottom = lastVisibleItem == null || lastVisibleItem.index >= layoutInfo.totalItemsCount - 2
                        if (isAtBottom) {
                            listState.scrollToItem(messages.size - 1)
                        }
                    }
                }
        }
    }

    // Sync chat logs from server
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        val ashHost = prefs.getString("ash_host", null)
        val ashPort = prefs.getString("ash_port", "18799")
        val token = prefs.getString("auth_token", null)
        if (!ashHost.isNullOrBlank() && !token.isNullOrBlank()) {
            val serverUrl = "${ashHost.trimEnd('/')}:$ashPort"
            chatViewModel.syncMessages(context.applicationContext, serverUrl, token)
        }
    }

    // Text-To-Speech Engine for reading companion responses aloud
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    DisposableEffect(context) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }
    val speakText: (String) -> Unit = { text ->
        if (tts == null) {
            tts = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    tts?.language = java.util.Locale.US
                    tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        } else {
            tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
            Unit
        }
    }

    val themeGradients = remember(characterId) {
        when (characterId) {
            1 -> listOf(Color(0xFF1E1035), Color(0xFF0C051A)) // Nova: cosmic violet
            2 -> listOf(Color(0xFF3D1E03), Color(0xFF150A00)) // Aria: solar amber
            3 -> listOf(Color(0xFF05201A), Color(0xFF010A08)) // Lumina: glassmorphic teal
            else -> listOf(Color(0xFF121212), Color(0xFF080808))
        }
    }
    val bgModifier = Modifier.background(Brush.verticalGradient(themeGradients))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(bgModifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        immersiveMode = !immersiveMode
                    }
                )
            }
    ) {
        // Full-screen static background image (for immersion / custom wallpaper)
        val localChar = character
        if (localChar != null) {
            val bgPath = localChar.chatWallpaperPath ?: localChar.avatarPath
            if (bgPath != null) {
                val bgFile = remember(bgPath) { File(bgPath) }
                var fileExists by remember(bgPath) { mutableStateOf(false) }
                var lastModified by remember(bgPath) { mutableStateOf(0L) }

                LaunchedEffect(bgPath) {
                    withContext(Dispatchers.IO) {
                        try {
                            val exists = bgFile.exists()
                            fileExists = exists
                            if (exists) {
                                lastModified = bgFile.lastModified()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                if (fileExists) {
                    val request = remember(bgPath, lastModified) {
                        coil.request.ImageRequest.Builder(context)
                            .data(bgFile)
                            .memoryCacheKey(bgFile.absolutePath + "_" + lastModified)
                            .diskCacheKey(bgFile.absolutePath + "_" + lastModified)
                            .build()
                    }
                    androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(model = request),
                        contentDescription = "Background",
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (blurBackdrop) Modifier.blur(20.dp) else Modifier),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        alpha = if (immersiveMode) 0.8f else 0.3f
                    )
                }
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!immersiveMode) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                character?.let { char ->
                                    val avatarPulseAnim = rememberInfiniteTransition(label = "avatar_pulse")
                                    val avatarScale by avatarPulseAnim.animateFloat(
                                        initialValue = 1f,
                                        targetValue = if (isSpeaking) 1.12f else 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(500, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "avatarScale"
                                    )
                                    xyz.ssfdre38.haven.ui.main.CharacterAvatar(
                                        character = char,
                                        modifier = Modifier
                                            .scale(avatarScale)
                                            .size(36.dp)
                                            .border(
                                                if (isSpeaking) 1.5.dp else 0.dp,
                                                if (isSpeaking) Color(0xFF4CAF50) else Color.Transparent,
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = char.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (char.currentMood.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                var moodMenuExpanded by remember { mutableStateOf(false) }
                                                Box {
                                                    Text(
                                                        text = getMoodEmoji(char.currentMood),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        modifier = Modifier
                                                            .clickable { moodMenuExpanded = true }
                                                            .padding(2.dp)
                                                    )
                                                    DropdownMenu(
                                                        expanded = moodMenuExpanded,
                                                        onDismissRequest = { moodMenuExpanded = false },
                                                        modifier = Modifier.width(240.dp).background(MaterialTheme.colorScheme.surface)
                                                    ) {
                                                        val level = (char.relationshipXp / 100) + 1
                                                        val xpInCurrentLevel = char.relationshipXp % 100
                                                        val xpProgress = xpInCurrentLevel / 100f
                                                        val relationshipTitle = when {
                                                            level >= 20 -> "Partner"
                                                            level >= 10 -> "Close Friend"
                                                            level >= 5  -> "Friend"
                                                            else        -> "Acquaintance"
                                                        }

                                                        Column(modifier = Modifier.padding(14.dp)) {
                                                            Text(
                                                                text = "Relationship Bond",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = relationshipTitle,
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = "Lv $level • $xpInCurrentLevel / 100 XP",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            LinearProgressIndicator(
                                                                progress = { xpProgress },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(5.dp)
                                                                    .clip(CircleShape),
                                                                color = when {
                                                                    level >= 20 -> Color(0xFFFFD700)
                                                                    level >= 10 -> Color(0xFF9C27B0)
                                                                    level >= 5  -> Color(0xFF2196F3)
                                                                    else        -> Color(0xFF4CAF50)
                                                                },
                                                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                                            )
                                                            Spacer(modifier = Modifier.height(14.dp))
                                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                                            Spacer(modifier = Modifier.height(12.dp))
                                                            
                                                            // Mood
                                                            Text(
                                                                text = "Current Mood: ${char.currentMood.replaceFirstChar { it.uppercase() }}",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            
                                                            // Location
                                                            if (char.currentLocation.isNotBlank()) {
                                                                Spacer(modifier = Modifier.height(6.dp))
                                                                Text(
                                                                    text = "📍 Location: ${char.currentLocation}",
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                            
                                                            // Outfit
                                                            if (char.currentOutfit.isNotBlank()) {
                                                                Spacer(modifier = Modifier.height(6.dp))
                                                                Text(
                                                                    text = "👗 Outfit: ${char.currentOutfit}",
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                if (isGenerating) {
                                    Text(
                                        text = "Typing...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (isSpeaking) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Speaking...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF4CAF50)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        MiniAudioVisualizer()
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val isTabletOrWide = configuration.screenWidthDp >= 600 || isLandscape

                    // 1. Direct App Bar Actions:
                    // Voice call (always visible)
                    IconButton(onClick = onVoiceCallClick) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Request Portrait (always visible)
                    IconButton(
                        onClick = { requestPortrait() },
                        enabled = !isGenerating
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Request Portrait",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Gallery (direct button on tablets/landscape, otherwise in more menu)
                    if (isTabletOrWide) {
                        IconButton(onClick = onGalleryClick) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "View Gallery",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // 2. Three-dot Dropdown Menu (Universal - visible on all devices)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Actions",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (blurBackdrop) "Disable Wallpaper Blur" else "Enable Wallpaper Blur") },
                                onClick = {
                                    expanded = false
                                    blurBackdrop = !blurBackdrop
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Blur Wallpaper"
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Immersive Mode") },
                                onClick = {
                                    expanded = false
                                    immersiveMode = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = "Immersive Mode"
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Set Chat Wallpaper") },
                                onClick = {
                                    expanded = false
                                    character?.let { char ->
                                        val list = getCompanionGalleryImages(context, char.name, messages)
                                        if (list.isEmpty()) {
                                            Toast.makeText(context, "No images in companion gallery yet. Generate some photos first!", Toast.LENGTH_LONG).show()
                                        } else {
                                            galleryWallpaperFiles = list
                                            showWallpaperPickerDialog = true
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Set Chat Wallpaper"
                                    )
                                }
                            )
                            if (character?.chatWallpaperPath != null) {
                                DropdownMenuItem(
                                    text = { Text("Clear Chat Wallpaper") },
                                    onClick = {
                                        expanded = false
                                        chatViewModel.clearChatWallpaper()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Clear Chat Wallpaper"
                                        )
                                    }
                                )
                            }
                            val localChar = character
                            if (localChar != null) {
                                DropdownMenuItem(
                                    text = { Text("Export Tavern Card") },
                                    onClick = {
                                        expanded = false
                                        chatViewModel.exportCompanionAsTavernCard(context, localChar)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Export Tavern Card"
                                        )
                                    }
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                            
                            DropdownMenuItem(
                                text = { Text("View Journal") },
                                onClick = {
                                    expanded = false
                                    onDiaryClick(character?.name ?: "Companion")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Book,
                                        contentDescription = "Journal"
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Memory Vault") },
                                onClick = {
                                    expanded = false
                                    onMemoryVaultClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = "Memory"
                                    )
                                }
                            )
                            
                            // Only show Gallery in more menu on mobile screens
                            if (!isTabletOrWide) {
                                DropdownMenuItem(
                                    text = { Text("View Gallery") },
                                    onClick = {
                                        expanded = false
                                        onGalleryClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = "Gallery"
                                        )
                                    }
                                )
                            }
                            
                            val prefs = remember { context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE) }
                            val isOverlayActive = prefs.getBoolean("enable_overlay", false) && android.provider.Settings.canDrawOverlays(context)
                            
                            DropdownMenuItem(
                                text = { Text(if (isOverlayActive) "Close Floating Companion" else "Show Floating Companion") },
                                onClick = {
                                    expanded = false
                                    if (isOverlayActive) {
                                        prefs.edit().putBoolean("enable_overlay", false).apply()
                                        context.stopService(android.content.Intent(context, xyz.ssfdre38.haven.service.FloatingCompanionService::class.java))
                                    } else {
                                        prefs.edit().putBoolean("enable_overlay", true).apply()
                                        if (!android.provider.Settings.canDrawOverlays(context)) {
                                            val intent = android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                android.net.Uri.parse("package:${context.packageName}")
                                            ).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            context.startService(android.content.Intent(context, xyz.ssfdre38.haven.service.FloatingCompanionService::class.java))
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isOverlayActive) Icons.Default.Close else Icons.Default.Cloud,
                                        contentDescription = "Floating Companion"
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bubble Conversation") },
                                onClick = {
                                    expanded = false
                                    character?.let { char ->
                                        val lastMsgText = messages.lastOrNull { it.sender == "character" }?.text?.take(200) ?: "Let's chat!"
                                        try {
                                            xyz.ssfdre38.haven.data.work.ProactiveMessageWorker.showNotification(
                                                context = context,
                                                character = char,
                                                messageText = lastMsgText,
                                                isSilent = false
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Error launching bubble: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Bubble Conversation"
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    },
        bottomBar = {
            if (!immersiveMode) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .navigationBarsPadding()
                            .imePadding()
                    ) {
                        // Selected image preview chip
                        selectedImageUri?.let { uri ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Attached image",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Image attached",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove image",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        val performSend = {
                            val text = inputText.trim()
                            val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                            val ashHost = prefs.getString("ash_host", null)
                            val ashPort = prefs.getString("ash_port", "18799")
                            val token = prefs.getString("auth_token", null)
                            if (ashHost.isNullOrBlank() || token.isNullOrBlank()) {
                                Toast.makeText(context, "Please configure the server in Settings first", Toast.LENGTH_LONG).show()
                            } else if (text.isNotBlank() || selectedImageUri != null) {
                                val serverUrl = "${ashHost.trimEnd('/')}:$ashPort"
                                val capturedUri = selectedImageUri
                                inputText = ""
                                selectedImageUri = null
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                if (capturedUri != null) {
                                    chatViewModel.sendPhotoMessage(context.applicationContext, serverUrl, token, text, capturedUri)
                                } else {
                                    chatViewModel.sendMessage(context.applicationContext, serverUrl, token, text)
                                }
                            }
                        }
                        val canSend = (inputText.isNotBlank() || selectedImageUri != null) && !isGenerating

                        // Input row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Attach image button
                            IconButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Attach image",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }

                            // AI Portrait Generation Button!
                            IconButton(
                                onClick = { requestPortrait() },
                                enabled = !isGenerating,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "Request Portrait",
                                    tint = if (isGenerating) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text("Message...", color = Color.White.copy(alpha = 0.5f)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.15f),
                                    disabledContainerColor = Color.Black.copy(alpha = 0.05f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                            if (!keyEvent.isShiftPressed) {
                                                if (canSend) {
                                                    performSend()
                                                }
                                                true // consume event
                                            } else {
                                                false // let shift+enter insert newline
                                            }
                                        } else {
                                            false
                                        }
                                    },
                                maxLines = 4,
                                enabled = !isGenerating,
                                shape = RoundedCornerShape(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { performSend() },
                                enabled = canSend,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (canSend) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier

    ) { innerPadding ->
        val themeGradients = remember(characterId) {
            when (characterId) {
                1 -> listOf(Color(0xFF1E1035), Color(0xFF0C051A)) // Nova: cosmic violet
                2 -> listOf(Color(0xFF3D1E03), Color(0xFF150A00)) // Aria: solar amber
                3 -> listOf(Color(0xFF05201A), Color(0xFF010A08)) // Lumina: glassmorphic teal
                else -> null
            }
        }
        val bgModifier = if (themeGradients != null) {
            Modifier.background(Brush.verticalGradient(themeGradients))
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        }

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val isTabletOrWide = configuration.screenWidthDp >= 600 || isLandscape
        Box(
            modifier = Modifier
                .padding(if (immersiveMode) PaddingValues(0.dp) else innerPadding)
                .fillMaxSize()
        ) {
            // Sleek Level/XP progress bar at the very top of the chat area (just under top bar)
            character?.let { char ->
                val level = (char.relationshipXp / 100) + 1
                val xpInCurrentLevel = char.relationshipXp % 100
                val xpProgress = xpInCurrentLevel / 100f
                val progressColor = when {
                    level >= 20 -> Color(0xFFFFD700) // Gold
                    level >= 10 -> Color(0xFF9C27B0) // Purple
                    level >= 5  -> Color(0xFF2196F3) // Blue
                    else        -> Color(0xFF4CAF50) // Green
                }

                LinearProgressIndicator(
                    progress = { xpProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.TopCenter),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.12f)
                )
            }

            val vrmPath = character?.vrmModelPath
            val hasVrm = remember(vrmPath) { vrmPath?.let { File(it).exists() } == true }
            val currentAnimationIndex = remember(character?.currentMood, isSpeaking) {
                val moodClean = character?.currentMood?.lowercase() ?: "neutral"
                when {
                    isSpeaking && (moodClean.contains("happy") || moodClean.contains("excited") || moodClean.contains("joy") || moodClean.contains("smile") || moodClean.contains("laugh")) -> 1
                    moodClean.contains("think") || moodClean.contains("ponder") || moodClean.contains("reflect") || moodClean.contains("neutral") || moodClean.contains("thoughtful") -> 2
                    else -> 0
                }
            }

            if (isTabletOrWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left 3D Character View (40% width / 100% if immersive)
                    val leftWeight = if (immersiveMode) 1f else 0.4f
                    if (hasVrm && vrmPath != null) {
                        Box(
                            modifier = Modifier
                                .weight(leftWeight)
                                .fillMaxHeight()
                        ) {
                            VrmAvatarView(
                                modelPath = vrmPath,
                                mood = character?.currentMood ?: "neutral",
                                isSpeaking = isSpeaking,
                                animationIndex = currentAnimationIndex,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // Fallback: 2D static large character avatar
                        Box(
                            modifier = Modifier
                                .weight(leftWeight)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            character?.let { char ->
                                xyz.ssfdre38.haven.ui.main.CharacterAvatar(
                                    character = char,
                                    modifier = Modifier.size(if (immersiveMode) 320.dp else 200.dp)
                                )
                            }
                        }
                    }

                    // Right Chat Messages View (60% width)
                    if (!immersiveMode) {
                        Box(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(messages, key = { it.id }) { message ->
                                    val isLast = message.id == messages.lastOrNull { it.sender == "character" }?.id
                                    MessageBubble(
                                        message = message,
                                        character = character,
                                        onImageClick = { activeFullscreenImage = it },
                                        isLastMessage = isLast,
                                        onRegenerateClick = {
                                            val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                                            val ashHost = prefs.getString("ash_host", "") ?: ""
                                            val ashPort = prefs.getString("ash_port", "18799") ?: "18799"
                                            val token = prefs.getString("auth_token", "") ?: ""
                                            if (ashHost.isNotBlank()) {
                                                val serverUrl = "${ashHost.trimEnd('/')}:$ashPort"
                                                chatViewModel.regenerateLastMessage(context.applicationContext, serverUrl, token)
                                            }
                                        },
                                        speakText = speakText,
                                        onPlayAudio = { chatViewModel.playAudio(it) },
                                        onEditClick = { editingMessage = it }
                                    )
                                }
                                if (isGenerating) {
                                    item {
                                        TypingBubble(character = character)
                                    }
                                } else {
                                    val lastMsg = messages.lastOrNull()
                                    if (lastMsg != null && lastMsg.sender == "user") {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Button(
                                                    onClick = {
                                                        val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                                                        val ashHost = prefs.getString("ash_host", "") ?: ""
                                                        val ashPort = prefs.getString("ash_port", "18799") ?: "18799"
                                                        val token = prefs.getString("auth_token", "") ?: ""
                                                        if (ashHost.isNotBlank()) {
                                                            val serverUrl = "${ashHost.trimEnd('/')}:$ashPort"
                                                            chatViewModel.resendLastMessage(context.applicationContext, serverUrl, token)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Resend",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Resend Failed Message", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Mobile layout: 3D character in background behind chat bubbles
                if (hasVrm && vrmPath != null) {
                    VrmAvatarView(
                        modelPath = vrmPath,
                        mood = character?.currentMood ?: "neutral",
                        isSpeaking = isSpeaking,
                        animationIndex = currentAnimationIndex,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (immersiveMode) 1.0f else 0.45f)
                    )
                }

                if (!immersiveMode) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            val isLast = message.id == messages.lastOrNull { it.sender == "character" }?.id
                            MessageBubble(
                                message = message,
                                character = character,
                                onImageClick = { activeFullscreenImage = it },
                                isLastMessage = isLast,
                                onRegenerateClick = {
                                    val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                                    val ashHost = prefs.getString("ash_host", "") ?: ""
                                    val ashPort = prefs.getString("ash_port", "18799") ?: "18799"
                                    val token = prefs.getString("auth_token", "") ?: ""
                                    if (ashHost.isNotBlank()) {
                                        val serverUrl = "${ashHost.trimEnd('/')}:$ashPort"
                                        chatViewModel.regenerateLastMessage(context.applicationContext, serverUrl, token)
                                    }
                                },
                                speakText = speakText,
                                onPlayAudio = { chatViewModel.playAudio(it) },
                                onEditClick = { editingMessage = it }
                            )
                        }
                        if (isGenerating) {
                            item {
                                TypingBubble(character = character)
                            }
                        } else {
                            val lastMsg = messages.lastOrNull()
                            if (lastMsg != null && lastMsg.sender == "user") {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(
                                            onClick = {
                                                val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                                                val ashHost = prefs.getString("ash_host", "") ?: ""
                                                val ashPort = prefs.getString("ash_port", "18799") ?: "18799"
                                                val token = prefs.getString("auth_token", "") ?: ""
                                                if (ashHost.isNotBlank()) {
                                                    val serverUrl = "${ashHost.trimEnd('/')}:$ashPort"
                                                    chatViewModel.resendLastMessage(context.applicationContext, serverUrl, token)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Resend",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Resend Failed Message", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editingMessage?.let { msg ->
        var editFieldText by remember { mutableStateOf(msg.text) }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = {
                Text(
                    text = "Edit Message",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                OutlinedTextField(
                    value = editFieldText,
                    onValueChange = { editFieldText = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    label = { Text("Message Content") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newText = editFieldText.trim()
                        if (newText.isNotEmpty()) {
                            scope.launch(Dispatchers.IO) {
                                repository.insertMessage(msg.copy(text = newText))
                            }
                        }
                        editingMessage = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showLevelUpDialog?.let { lv ->
        AlertDialog(
            onDismissRequest = { showLevelUpDialog = null },
            title = {
                Text(
                    text = "🎉 Relationship Level Up!",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your bond with ${character?.name ?: "your companion"} has grown stronger!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(72.dp),
                        tonalElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Lv $lv",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    val title = when {
                        lv >= 20 -> "Partner"
                        lv >= 10 -> "Close Friend"
                        lv >= 5  -> "Friend"
                        else     -> "Acquaintance"
                    }
                    Text(
                        text = "New Status: $title",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLevelUpDialog = null }) {
                    Text("Awesome!")
                }
            }
        )
    }

    activeFullscreenImage?.let { imgModel ->
        FullscreenImageViewer(
            imageModel = imgModel,
            onDismiss = { activeFullscreenImage = null }
        )
    }

    if (showWallpaperPickerDialog) {
        GalleryWallpaperPickerDialog(
            images = galleryWallpaperFiles,
            onImageSelected = { file ->
                showWallpaperPickerDialog = false
                chatViewModel.setChatWallpaper(context, Uri.fromFile(file))
            },
            onDismiss = { showWallpaperPickerDialog = false }
        )
    }
    }
}

@Composable
fun FullscreenImageViewer(
    imageModel: Any,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val state = androidx.compose.foundation.gestures.rememberTransformableState { zoomChange, offsetChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            offset += offsetChange * scale
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = androidx.compose.ui.geometry.Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = "Fullscreen Image",
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = state),
                contentScale = ContentScale.Fit
            )

            // Header close and download buttons row at top right
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
            ) {
                val context = LocalContext.current
                IconButton(
                    onClick = {
                        saveImageToGallery(context, imageModel) { success ->
                            if (success) {
                                Toast.makeText(context, "Saved to Pictures/Haven!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Save to Gallery",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun saveImageToGallery(context: android.content.Context, imageModel: Any, onResult: (Boolean) -> Unit) {
    val resolver = context.contentResolver
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            var inputStream: java.io.InputStream? = null
            var displayName = "Haven_${System.currentTimeMillis()}"
            var mimeType = "image/png"

            if (imageModel is File) {
                if (imageModel.exists()) {
                    inputStream = imageModel.inputStream()
                    val ext = imageModel.extension.lowercase()
                    displayName = "Haven_${imageModel.name}"
                    mimeType = if (ext == "webp") "image/webp" else if (ext == "jpg" || ext == "jpeg") "image/jpeg" else "image/png"
                }
            } else if (imageModel is String) {
                val url = imageModel
                val ext = if (url.lowercase().endsWith(".webp")) "webp"
                          else if (url.lowercase().endsWith(".jpg") || url.lowercase().endsWith(".jpeg")) "jpg"
                          else "png"
                displayName = "Haven_${System.currentTimeMillis()}.$ext"
                mimeType = if (ext == "webp") "image/webp" else if (ext == "jpg") "image/jpeg" else "image/png"

                val request = okhttp3.Request.Builder().url(url).build()
                val response = xyz.ssfdre38.haven.data.network.HavenHttpClient.httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        inputStream = java.io.ByteArrayInputStream(bytes)
                    }
                }
            }

            if (inputStream == null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false)
                }
                return@launch
            }

            val imageDetails = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Haven")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, imageDetails)
            if (uri == null) {
                inputStream.close()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false)
                }
                return@launch
            }

            resolver.openOutputStream(uri)?.use { out ->
                inputStream.use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                imageDetails.clear()
                imageDetails.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, imageDetails, null, null)
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(false)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    character: CharacterEntity?,
    onImageClick: (Any) -> Unit,
    isLastMessage: Boolean,
    onRegenerateClick: () -> Unit,
    speakText: (String) -> Unit,
    onPlayAudio: (String) -> Unit,
    onEditClick: (MessageEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == "user"
    val context = androidx.compose.ui.platform.LocalContext.current
    val serverUrl = remember {
        val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        val ashHost = prefs.getString("ash_host", "") ?: ""
        val ashPort = prefs.getString("ash_port", "") ?: ""
        val host = ashHost.trimEnd('/')
        val port = ashPort.trim()
        if (host.startsWith("http")) "$host:$port" else "http://$host:$port"
    }

    val userAvatarPath = remember {
        val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        prefs.getString("user_avatar_path", "") ?: ""
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val bubbleMaxWidth = remember(configuration.screenWidthDp) { (configuration.screenWidthDp * 0.72f).dp }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser && character != null) {
            xyz.ssfdre38.haven.ui.main.CharacterAvatar(
                character = character,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Parse message text for thoughts
            val parsed = remember(message.text) { parseMessageText(message.text) }

            // Extract inline images from cleanText
            val contentParsed = remember(parsed.cleanText, serverUrl) {
                parseImageUrls(parsed.cleanText, serverUrl)
            }

            // If the message has a generated image, show it
            if (message.imagePath != null) {
                val imgFile = remember(message.imagePath) { File(message.imagePath) }
                AsyncImage(
                    model = imgFile,
                    contentDescription = "Generated image",
                    modifier = Modifier
                        .widthIn(max = bubbleMaxWidth)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onImageClick(imgFile) },
                    contentScale = ContentScale.FillWidth
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // If the message contains an inline remote image URL, show it (only if local image is not already present)
            if (contentParsed.imageUrl != null && message.imagePath == null) {
                AsyncImage(
                    model = contentParsed.imageUrl,
                    contentDescription = "Inline generated portrait",
                    modifier = Modifier
                        .widthIn(max = bubbleMaxWidth)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onImageClick(contentParsed.imageUrl) },
                    contentScale = ContentScale.FillWidth
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Render inner thoughts if present
            if (!isUser && parsed.thought != null && parsed.thought.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .widthIn(max = bubbleMaxWidth),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = "Thoughts",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Inner Monologue",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = parsed.thought,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Render main message bubble
                if (contentParsed.cleanText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = if (isUser) 18.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp
                        ),
                        color = if (isUser)
                            Color(0xFF4A148C).copy(alpha = 0.55f) // Glassmorphic User purple
                        else
                            Color(0xFF1A1A1A).copy(alpha = 0.7f), // Glassmorphic Companion dark grey
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .widthIn(max = bubbleMaxWidth)
                            .combinedClickable(
                                onClick = { /* no-op */ },
                                onLongClick = { onEditClick(message) }
                            )
                    ) {
                        Text(
                            text = formatMessageText(contentParsed.cleanText, isUser),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                if (!isUser && message.audioPath != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            onPlayAudio(message.audioPath)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = "Play voice",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Inline Action Toolbar directly below the bubble (only for companion messages)
            if (!isUser && contentParsed.cleanText.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    // Copy button
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Message", contentParsed.cleanText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    // TTS button (uses player for audioPath, falls back to speakText)
                    IconButton(
                        onClick = {
                            if (message.audioPath != null) {
                                onPlayAudio(message.audioPath)
                            } else {
                                speakText(contentParsed.cleanText)
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (message.audioPath != null) Icons.Filled.PlayCircle else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Speak Text",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    
                    // Regenerate button (only shown if this is the last companion message)
                    if (isLastMessage) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onRegenerateClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            if (userAvatarPath.isNotEmpty()) {
                val model = remember(userAvatarPath) {
                    if (userAvatarPath.startsWith("http")) userAvatarPath else java.io.File(userAvatarPath)
                }
                AsyncImage(
                    model = model,
                    contentDescription = "User avatar",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Me", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

// Regex helper to extract thoughts
data class ParsedMessage(val thought: String?, val cleanText: String)

fun parseMessageText(text: String): ParsedMessage {
    val callRegex = "<\\s*call\\s*>.*?<\\s*/\\s*call\\s*>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    var processedText = text.replace(callRegex, "").trim()
    
    val strayCallRegex = "</?\\s*call[^>]*>".toRegex(RegexOption.IGNORE_CASE)
    processedText = processedText.replace(strayCallRegex, "").trim()

    val thoughtRegex = "<\\s*(thought|thinking|reasoning|Reasoning/Plan|plan|thought_process)\\b[^>]*>(.*?)</\\s*(?:thought|thinking|reasoning|Reasoning/Plan|plan|thought_process)\\s*>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val match = thoughtRegex.find(processedText)
    
    val rawParsed = if (match != null) {
        val thoughtContent = match.groups[2]?.value?.trim()
        val cleanTextContent = processedText.replace(thoughtRegex, "").trim()
        ParsedMessage(thoughtContent, cleanTextContent)
    } else {
        // Fallback 1: If only closing tag is present
        var closingTagIndex = -1
        var matchedTag = ""
        val tags = listOf("thought", "thinking", "reasoning", "Reasoning/Plan", "plan", "thought_process")
        for (tag in tags) {
            val idx = processedText.indexOf("</$tag>", ignoreCase = true)
            if (idx != -1) {
                closingTagIndex = idx
                matchedTag = tag
                break
            }
        }
        if (closingTagIndex != -1) {
            val thoughtContent = processedText.substring(0, closingTagIndex).replace("<$matchedTag>", "", ignoreCase = true).trim()
            val cleanTextContent = processedText.substring(closingTagIndex + "</$matchedTag>".length).trim()
            ParsedMessage(
                thought = if (thoughtContent.isNotBlank()) thoughtContent else null,
                cleanText = cleanTextContent
            )
        } else {
            // Fallback 2: If only opening tag is present (cut-off)
            var openingTagIndex = -1
            for (tag in tags) {
                val idx = processedText.indexOf("<$tag>", ignoreCase = true)
                if (idx != -1) {
                    openingTagIndex = idx
                    matchedTag = tag
                    break
                }
            }
            if (openingTagIndex != -1) {
                val thoughtContent = processedText.substring(openingTagIndex + "<$matchedTag>".length).trim()
                ParsedMessage(
                    thought = if (thoughtContent.isNotBlank()) thoughtContent else null,
                    cleanText = ""
                )
            } else {
                ParsedMessage(null, processedText)
            }
        }
    }

    return ParsedMessage(
        thought = rawParsed.thought?.let { cleanMessageTextSpacing(it) },
        cleanText = cleanMessageTextSpacing(rawParsed.cleanText)
    )
}

fun cleanMessageTextSpacing(input: String): String {
    var text = input

    // 1. Fix spaces around contractions/possessive apostrophes only (e.g. "I ' m" -> "I'm", "isn ' t" -> "isn't", "don ' t" -> "don't")
    val apostropheRegex = """(\b\w+)\s*'\s*(s|t|m|re|ve|ll|d)\b""".toRegex(RegexOption.IGNORE_CASE)
    text = text.replace(apostropheRegex, "$1'$2")

    // 2. Fix spaces before punctuation marks (e.g. "hello !" -> "hello!", "going ." -> "going.")
    val punctuationRegex = """\s+([.,!?;:])""".toRegex()
    text = text.replace(punctuationRegex, "$1")

    // 3. Fix common split suffixes / subwords (e.g. "ghost ing" -> "ghosting", "interest ed" -> "interested")
    val suffixRegex = """\b(\w+)\s+(ing|ed|ly|ment|ness|tion|ence|aging|ler)\b""".toRegex(RegexOption.IGNORE_CASE)
    text = text.replace(suffixRegex, "$1$2")

    // 4. Fix specific known splits from Gemma tokenizer:
    text = text.replace("""\bEnc\s+our\s+aging\b""".toRegex(RegexOption.IGNORE_CASE), "encouraging")
    text = text.replace("""\bintel\s+lectual\b""".toRegex(RegexOption.IGNORE_CASE), "intellectual")
    text = text.replace("""\boffline\s+ing\b""".toRegex(RegexOption.IGNORE_CASE), "offlining")
    text = text.replace("""\bRef\s+ining\b""".toRegex(RegexOption.IGNORE_CASE), "refining")
    text = text.replace("""\bTarget\s+ing\b""".toRegex(RegexOption.IGNORE_CASE), "targeting")
    text = text.replace("""\bghost\s+ing\b""".toRegex(RegexOption.IGNORE_CASE), "ghosting")
    text = text.replace("""\bHol\s+ler\b""".toRegex(RegexOption.IGNORE_CASE), "holler")
    text = text.replace("""\bsil\s+ence\b""".toRegex(RegexOption.IGNORE_CASE), "silence")
    text = text.replace("""\bDraft\s+ing\b""".toRegex(RegexOption.IGNORE_CASE), "drafting")

    return text
}

data class MessageContent(val cleanText: String, val imageUrl: String?)

fun parseImageUrls(text: String, serverUrl: String): MessageContent {
    val regex = "(https?://[^\\s/]+/uploads/[%a-zA-Z_0-9.-]+)|(/uploads/[%a-zA-Z_0-9.-]+)".toRegex(RegexOption.IGNORE_CASE)
    val match = regex.find(text)
    return if (match != null) {
        val rawUrl = match.value
        val resolvedUrl = if (rawUrl.startsWith("/")) {
            val host = serverUrl.trimEnd('/')
            if (host.startsWith("http")) "$host$rawUrl" else "http://$host$rawUrl"
        } else {
            rawUrl
        }
        val cleanText = text.replace(regex, "").trim()
        MessageContent(cleanText, resolvedUrl)
    } else {
        MessageContent(text, null)
    }
}

fun formatMessageText(text: String, isUser: Boolean): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val parts = text.split("*")
        val processedParts = mutableListOf<Pair<String, Boolean>>() // text, isAction

        for (i in parts.indices) {
            val part = parts[i]
            if (part.isEmpty()) continue

            val isAction = (i % 2 == 1)
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue

            // If this part consists only of punctuation/quotes, merge it into the previous part
            if (processedParts.isNotEmpty() && trimmed.matches("""^[.,!?;:"'()\s\-]+$""".toRegex())) {
                val last = processedParts.removeAt(processedParts.size - 1)
                processedParts.add(Pair(last.first + part, last.second))
            } else {
                processedParts.add(Pair(part, isAction))
            }
        }

        var activePartsCount = 0
        for (i in processedParts.indices) {
            val pair = processedParts[i]
            val rawText = pair.first
            val isAction = pair.second
            val trimmed = rawText.trim()
            if (trimmed.isEmpty()) continue

            // Determine if we should add a newline before this part
            if (activePartsCount > 0) {
                val prevPair = processedParts[i - 1]
                val prevTextTrimmed = prevPair.first.trim()
                
                // Add newline if there's a sentence-ending punctuation or quotation mark boundary
                val endsWithSentencePunct = prevTextTrimmed.endsWith(".") || 
                                           prevTextTrimmed.endsWith("!") || 
                                           prevTextTrimmed.endsWith("?") || 
                                           prevTextTrimmed.endsWith("\"")
                
                val startsWithQuote = trimmed.startsWith("\"")

                if (endsWithSentencePunct || startsWithQuote) {
                    append("\n")
                } else {
                    // Inline transition: ensure exactly one space between parts
                    append(" ")
                }
            }
            activePartsCount++

            if (isAction) {
                withStyle(
                    style = SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = if (isUser) Color.White.copy(alpha = 0.7f) else Color(0xFFD1C4E9) // Soft purple tint
                    )
                ) {
                    append(trimmed)
                }
            } else {
                withStyle(
                    style = SpanStyle(
                        color = Color.White
                    )
                ) {
                    append(trimmed)
                }
            }
        }
    }
}

@Composable
fun MiniAudioVisualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "mini_visualizer")
    
    val height1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        val barColor = Color(0xFF4CAF50)
        Box(modifier = Modifier.size(width = 2.dp, height = height1.dp).background(barColor, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.size(width = 2.dp, height = height2.dp).background(barColor, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.size(width = 2.dp, height = height3.dp).background(barColor, RoundedCornerShape(1.dp)))
    }
}

private fun getMoodEmoji(mood: String): String {
    val lower = mood.lowercase()
    return when {
        lower.contains("submissive") -> "🥺"
        lower.contains("dominant") -> "😈"
        lower.contains("blush") -> "😳"
        lower.contains("flirt") || lower.contains("seductive") -> "😏"
        lower.contains("shy") -> "🫣"
        lower.contains("happy") || lower.contains("cheerful") -> "😊"
        lower.contains("sad") || lower.contains("crying") -> "😢"
        lower.contains("angry") || lower.contains("mad") -> "😠"
        lower.contains("excited") -> "😆"
        lower.contains("aroused") -> "😳"
        lower.contains("neutral") -> "😐"
        else -> "✨"
    }
}

@Composable
fun TypingBubble(
    character: CharacterEntity?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (character != null) {
                xyz.ssfdre38.haven.ui.main.CharacterAvatar(
                    character = character,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("🌸", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(
                topStart = 2.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val transition = rememberInfiniteTransition(label = "typing_dots")
                repeat(3) { index ->
                    val delay = index * 150
                    val floatAnim by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = -6f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 600
                                0.0f at delay with FastOutLinearInEasing
                                -6.0f at delay + 150 with LinearOutSlowInEasing
                                0.0f at delay + 300 with FastOutLinearInEasing
                                0.0f at 600
                            },
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "dot_$index"
                    )
                    Box(
                        modifier = Modifier
                            .graphicsLayer { translationY = floatAnim }
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
fun GalleryWallpaperPickerDialog(
    images: List<File>,
    onImageSelected: (File) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Chat Wallpaper",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (images.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No images in companion gallery yet.\nGenerate or receive some photos first!",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(images) { file ->
                        AsyncImage(
                            model = file,
                            contentDescription = "Gallery Wallpaper Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageSelected(file) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun getCompanionGalleryImages(context: Context, characterName: String, messages: List<MessageEntity>): List<File> {
    val files = mutableSetOf<File>()

    // 1. From database messages
    messages.forEach { msg ->
        msg.imagePath?.let { path ->
            val f = File(path)
            if (f.exists()) files.add(f)
        }
    }

    // 2. From companion directory
    val cleanName = characterName.replace("[^a-zA-Z0-9]".toRegex(), "_")
    val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
    val dirsToScan = listOf(
        File(baseDir, "companion/images/$cleanName"),
        File(context.filesDir, "companion/images/$cleanName")
    )

    dirsToScan.forEach { dir ->
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && (file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp"))) {
                    files.add(file)
                }
            }
        }
    }

    return files.toList().sortedByDescending { it.lastModified() }
}

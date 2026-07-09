package xyz.ssfdre38.haven.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    viewModel: ChatViewModel = viewModel(key = "chat_$characterId") { ChatViewModel(characterId, repository) }
) {
    val context = LocalContext.current
    val character by viewModel.character.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var activeFullscreenImage by remember { mutableStateOf<Any?>(null) }

    var previousLevel by remember { mutableStateOf<Int?>(null) }
    var showLevelUpDialog by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(character?.relationshipXp) {
        val xp = character?.relationshipXp ?: 0
        val currentLevel = (xp / 100) + 1
        if (previousLevel != null && currentLevel > previousLevel!!) {
            showLevelUpDialog = currentLevel
        }
        previousLevel = currentLevel
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    // Auto-scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        character?.let { char ->
                            xyz.ssfdre38.haven.ui.main.CharacterAvatar(
                                character = char,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = char.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isGenerating) {
                                    Text(
                                        text = "Typing...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
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

                    // Voice call (always visible as key quick action)
                    IconButton(onClick = onVoiceCallClick) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isTabletOrWide) {
                        IconButton(onClick = { onDiaryClick(character?.name ?: "Companion") }) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = "View Journal",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onMemoryVaultClick) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Memory Vault",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onGalleryClick) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "View Gallery",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(
                            onClick = {
                                val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                                val sdHost = prefs.getString("sd_host", null)
                                if (sdHost.isNullOrBlank()) {
                                    Toast.makeText(context, "Configure Stable Diffusion server in Settings first", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                character?.let { char ->
                                    val loc = char.currentLocation.ifBlank { "cozy room" }
                                    val outfit = char.currentOutfit.ifBlank { "casual clothing" }
                                    val moodStr = if (char.currentMood.isNotBlank()) "${char.currentMood} expression, " else ""
                                    val imagePrompt = "${char.name}, digital art portrait, highly detailed, ${moodStr}standing in $loc, wearing $outfit"
                                    viewModel.generateImage(context, sdHost, imagePrompt)
                                }
                            },
                            enabled = !isGenerating
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Generate Image",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
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
                                DropdownMenuItem(
                                    text = { Text("Request Portrait") },
                                    onClick = {
                                        expanded = false
                                        val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                                        val sdHost = prefs.getString("sd_host", null)
                                        if (sdHost.isNullOrBlank()) {
                                            Toast.makeText(context, "Configure Stable Diffusion server in Settings first", Toast.LENGTH_SHORT).show()
                                            return@DropdownMenuItem
                                        }
                                        character?.let { char ->
                                            val loc = char.currentLocation.ifBlank { "cozy room" }
                                            val outfit = char.currentOutfit.ifBlank { "casual clothing" }
                                            val moodStr = if (char.currentMood.isNotBlank()) "${char.currentMood} expression, " else ""
                                            val imagePrompt = "${char.name}, digital art portrait, highly detailed, ${moodStr}standing in $loc, wearing $outfit"
                                            viewModel.generateImage(context, sdHost, imagePrompt)
                                        }
                                    },
                                    enabled = !isGenerating,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = "Portrait"
                                        )
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding()
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
                            if (capturedUri != null) {
                                viewModel.sendPhotoMessage(context, serverUrl, token, text, capturedUri)
                            } else {
                                viewModel.sendMessage(context, serverUrl, token, text)
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
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Message...") },
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

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .then(bgModifier)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        character = character,
                        onImageClick = { activeFullscreenImage = it }
                    )
                }
            }
        }
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

            // Close button at top right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
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

@Composable
fun MessageBubble(
    message: MessageEntity,
    character: CharacterEntity?,
    onImageClick: (Any) -> Unit,
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
                        .widthIn(max = 260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onImageClick(imgFile) },
                    contentScale = ContentScale.FillWidth
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // If the message contains an inline remote image URL, show it
            if (contentParsed.imageUrl != null) {
                AsyncImage(
                    model = contentParsed.imageUrl,
                    contentDescription = "Inline generated portrait",
                    modifier = Modifier
                        .widthIn(max = 260.dp)
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
                        .widthIn(max = 260.dp),
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
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = contentParsed.cleanText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                if (!isUser && message.audioPath != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            try {
                                android.media.MediaPlayer().apply {
                                    setDataSource(message.audioPath)
                                    prepareAsync()
                                    setOnPreparedListener { start() }
                                    setOnCompletionListener { release() }
                                    setOnErrorListener { _, _, _ ->
                                        release()
                                        true
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
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
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
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

// Regex helper to extract thoughts
data class ParsedMessage(val thought: String?, val cleanText: String)

fun parseMessageText(text: String): ParsedMessage {
    val thoughtRegex = "<\\s*thought\\s*>(.*?)<\\s*/\\s*thought\\s*>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val match = thoughtRegex.find(text)
    return if (match != null) {
        val thoughtContent = match.groups[1]?.value?.trim()
        val cleanTextContent = text.replace(thoughtRegex, "").trim()
        ParsedMessage(thoughtContent, cleanTextContent)
    } else {
        ParsedMessage(null, text)
    }
}

data class MessageContent(val cleanText: String, val imageUrl: String?)

fun parseImageUrls(text: String, serverUrl: String): MessageContent {
    val regex = "(https?://[^\\s/]+/uploads/[\\w\\d-]+\\.(?:png|jpg|jpeg|webp))|(/uploads/[\\w\\d-]+\\.(?:png|jpg|jpeg|webp))".toRegex(RegexOption.IGNORE_CASE)
    val match = regex.find(text)
    return if (match != null) {
        val rawUrl = match.value
        val resolvedUrl = if (rawUrl.startsWith("/")) {
            "${serverUrl.trimEnd('/')}$rawUrl"
        } else {
            rawUrl
        }
        val cleanText = text.replace(regex, "").trim()
        MessageContent(cleanText, resolvedUrl)
    } else {
        MessageContent(text, null)
    }
}


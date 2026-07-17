package xyz.ssfdre38.haven.ui.group

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.CharacterEntity
import xyz.ssfdre38.haven.data.database.GroupMessageEntity
import xyz.ssfdre38.haven.ui.chat.parseImageUrls
import xyz.ssfdre38.haven.ui.chat.parseMessageText
import xyz.ssfdre38.haven.ui.chat.formatMessageText
import java.io.File
import androidx.compose.ui.input.key.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: Int,
    repository: DataRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupChatViewModel = viewModel(key = "group_$groupId") { GroupChatViewModel(groupId, repository) }
) {
    val context = LocalContext.current
    val group by viewModel.group.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selectedSpeakerId by viewModel.selectedSpeakerId.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val activeStreamingMessageId by viewModel.activeStreamingMessageId.collectAsStateWithLifecycle()
    val allCompanions by repository.getAllCharacters().collectAsStateWithLifecycle(emptyList())

    val filteredMessages = remember(messages, activeStreamingMessageId) {
        messages.filter { msg ->
            msg.text.isNotBlank() || msg.imagePath != null || msg.id == activeStreamingMessageId
        }
    }
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showGalleryDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    // Resolve server settings
    val serverUrl = remember {
        val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        val host = prefs.getString("ash_host", "") ?: ""
        val port = prefs.getString("ash_port", "") ?: ""
        val trimmedHost = host.trimEnd('/')
        val trimmedPort = port.trim()
        if (trimmedHost.startsWith("http")) "$trimmedHost:$trimmedPort" else "http://$trimmedHost:$trimmedPort"
    }
    val token = remember {
        val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        prefs.getString("auth_token", "") ?: ""
    }

    var previousSize by remember { mutableStateOf(filteredMessages.size) }
    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            val delta = filteredMessages.size - previousSize
            if (delta == 1) {
                listState.animateScrollToItem(filteredMessages.size - 1)
            } else {
                listState.scrollToItem(filteredMessages.size - 1)
            }
        }
        previousSize = filteredMessages.size
    }

    LaunchedEffect(serverUrl, token) {
        if (serverUrl.isNotBlank() && token.isNotBlank()) {
            viewModel.syncGroupMessages(context, serverUrl, token)
        }
    }

    val mainGradient = remember {
        listOf(Color(0xFF1E1035), Color(0xFF0C051A))
    }

    val backdropMap = remember {
        mapOf(
            "lobby" to "https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800&auto=format&fit=crop",
            "fireplace" to "https://images.unsplash.com/photo-1545048702-79362596cdc9?w=800&auto=format&fit=crop",
            "cozy" to "https://images.unsplash.com/photo-1513694203232-719a280e022f?w=800&auto=format&fit=crop",
            "rain" to "https://images.unsplash.com/photo-1428908728789-d2de25dbd4e2?w=800&auto=format&fit=crop",
            "cafe" to "https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800&auto=format&fit=crop",
            "library" to "https://images.unsplash.com/photo-1507842217343-583bb7270b66?w=800&auto=format&fit=crop",
            "bedroom" to "https://images.unsplash.com/photo-1505691938895-1758d7feb511?w=800&auto=format&fit=crop",
            "garden" to "https://images.unsplash.com/photo-1585320806297-9794b3e4eeae?w=800&auto=format&fit=crop"
        )
    }

    val activeSpeaker = participants.firstOrNull { it.id == selectedSpeakerId }
    val activeLocation = activeSpeaker?.currentLocation?.lowercase() ?: "lobby"
    val backdropUrl = remember(activeLocation) {
        backdropMap.entries.firstOrNull { activeLocation.contains(it.key) }?.value 
            ?: backdropMap["lobby"]
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(mainGradient))
    ) {
        if (backdropUrl != null) {
            coil.compose.AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0C051A).copy(alpha = 0.65f))
            )
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = group?.name ?: "Group Chat",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "${participants.size} companions in room",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
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
                    ),
                    actions = {
                        val autoBanter by viewModel.autoBanterEnabled.collectAsStateWithLifecycle()
                        if (autoBanter && isGenerating) {
                            IconButton(onClick = { viewModel.stopBanter() }) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Banter",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Banter Settings",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showGalleryDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Gallery",
                                tint = Color.White
                            )
                        }
                    }
                )
            },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C051A).copy(alpha = 0.85f))
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Active typing companion indicator
                val typingName by viewModel.typingCompanionName.collectAsStateWithLifecycle()
                if (typingName != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${typingName} is writing...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                // Speaker Selector Chips
                val activeSpeaker = participants.firstOrNull { it.id == selectedSpeakerId }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Responder:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedSpeakerId == -1) {
                        Text(
                            text = "Auto-selecting responder",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    } else if (activeSpeaker != null) {
                        val statusText = buildString {
                            if (activeSpeaker.currentOutfit.isNotBlank()) append(activeSpeaker.currentOutfit)
                            if (activeSpeaker.currentLocation.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(activeSpeaker.currentLocation)
                            }
                            if (activeSpeaker.currentMood.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(activeSpeaker.currentMood)
                            }
                        }
                        if (statusText.isNotBlank()) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        val isSelected = selectedSpeakerId == -1
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.selectSpeaker(-1) }
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("A", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Auto", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    items(participants) { char ->
                        val isSelected = char.id == selectedSpeakerId
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.selectSpeaker(char.id) }
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (char.avatarPath != null) {
                                    AsyncImage(
                                        model = File(char.avatarPath),
                                        contentDescription = char.name,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(char.name.take(1), fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(char.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                val canSendGroup = inputText.isNotBlank() && !isGenerating
                val performSendGroup = {
                    if (canSendGroup) {
                        viewModel.sendMessage(context, serverUrl, token, inputText)
                        inputText = ""
                    }
                }

                // Chat Input Field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Message group...") },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                    if (!keyEvent.isShiftPressed) {
                                        if (canSendGroup) {
                                            performSendGroup()
                                        }
                                        true // consume event
                                    } else {
                                        false // let shift+enter insert newline
                                    }
                                } else {
                                    false
                                }
                            },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { performSendGroup() },
                        enabled = canSendGroup,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSendGroup)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSendGroup)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMessages, key = { it.id }) { message ->
                    val isUser = message.sender == "user"
                    val speaker = participants.firstOrNull { it.id == message.characterId }

                    GroupMessageBubble(
                        message = message,
                        isUser = isUser,
                        speaker = speaker,
                        serverUrl = serverUrl
                    )
                }
            }
        }

        if (showSettingsDialog) {
            val autoBanter by viewModel.autoBanterEnabled.collectAsStateWithLifecycle()
            val banterLimit by viewModel.banterLimit.collectAsStateWithLifecycle()
            var tempSelectedParticipants by remember(participants) { mutableStateOf(participants.toSet()) }
            val groupVal = group
            var tempScenario by remember(groupVal) { mutableStateOf(groupVal?.scenario ?: "") }
            var tempSystemPrompt by remember(groupVal) { mutableStateOf(groupVal?.systemPrompt ?: "") }

            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Group Chat Settings") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        OutlinedTextField(
                            value = tempScenario,
                            onValueChange = { tempScenario = it },
                            label = { Text("Room Scenario / Context", color = Color.White.copy(alpha = 0.6f)) },
                            placeholder = { Text("e.g. Cozy campfire in a snowy forest...", color = Color.White.copy(alpha = 0.4f)) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = tempSystemPrompt,
                            onValueChange = { tempSystemPrompt = it },
                            label = { Text("Room System Prompt", color = Color.White.copy(alpha = 0.6f)) },
                            placeholder = { Text("e.g. Speak with medieval slang, keep responses brief.", color = Color.White.copy(alpha = 0.4f)) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Auto Banter", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = autoBanter,
                                onCheckedChange = { viewModel.autoBanterEnabled.value = it }
                            )
                        }
                        
                        if (autoBanter) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Consecutive Turn Limit:", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val limits = listOf(
                                Pair(2, "2 turns (Standard)"),
                                Pair(5, "5 turns (Extended)"),
                                Pair(10, "10 turns (Conversational)"),
                                Pair(-1, "Infinite (Autonomous Loop)")
                            )
                            
                            limits.forEach { (limitVal, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.banterLimit.value = limitVal }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = banterLimit == limitVal,
                                        onClick = { viewModel.banterLimit.value = limitVal }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Edit Companions in Room:", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        allCompanions.forEach { char ->
                            val isChecked = tempSelectedParticipants.any { it.id == char.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tempSelectedParticipants = if (isChecked) {
                                            tempSelectedParticipants.filter { it.id != char.id }.toSet()
                                        } else {
                                            tempSelectedParticipants + char
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        tempSelectedParticipants = if (!checked) {
                                            tempSelectedParticipants.filter { it.id != char.id }.toSet()
                                        } else {
                                            tempSelectedParticipants + char
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(char.name, color = Color.White.copy(alpha = 0.8f))
                            }
                        }

                        val relations = remember(participants) { viewModel.loadRelations(context) }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Inter-Companion Sentiments:", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        var hasSentiments = false
                        participants.forEach { sourceChar ->
                            participants.forEach { targetChar ->
                                if (sourceChar.id != targetChar.id) {
                                    val rel = relations[sourceChar.name]?.get(targetChar.name) ?: GroupChatViewModel.CompanionRelation()
                                    Text(
                                        text = "${sourceChar.name} feels ${rel.sentiment} (${rel.affinity}/100) towards ${targetChar.name}",
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    hasSentiments = true
                                }
                            }
                        }
                        if (!hasSentiments) {
                            Text("No relationships formed yet.", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateGroupConfig(context, serverUrl, token, tempScenario, tempSystemPrompt)
                        viewModel.updateParticipants(context, serverUrl, token, tempSelectedParticipants.toList())
                        showSettingsDialog = false
                    }) {
                        Text("Apply", color = MaterialTheme.colorScheme.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF1E1035),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f)
            )
        }

        if (showGalleryDialog) {
            val sharedImages = remember(messages) {
                messages.filter { !it.imagePath.isNullOrBlank() }
            }
            val urlRegex = remember { "(https?://[^\\s/]+/uploads/[%a-zA-Z_0-9.-]+)|(/uploads/[%a-zA-Z_0-9.-]+)".toRegex(RegexOption.IGNORE_CASE) }
            var activeFullscreenImage by remember { mutableStateOf<GroupMessageEntity?>(null) }

            AlertDialog(
                onDismissRequest = { showGalleryDialog = false },
                title = { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Group Gallery", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { showGalleryDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                        if (sharedImages.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No shared images in this room yet.", color = Color.White.copy(alpha = 0.5f))
                            }
                        } else {
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(sharedImages.size) { index ->
                                    val msg = sharedImages[index]
                                    val file = java.io.File(msg.imagePath!!)
                                    
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .clickable { activeFullscreenImage = msg }
                                    ) {
                                        AsyncImage(
                                            model = file,
                                            contentDescription = "Shared photo",
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                containerColor = Color(0xFF1E1035),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            // Fullscreen lightbox view
            if (activeFullscreenImage != null) {
                val msg = activeFullscreenImage!!
                val file = java.io.File(msg.imagePath!!)
                val senderName = if (msg.sender == "user") "You" else participants.firstOrNull { it.id == msg.characterId }?.name ?: "Companion"
                
                Dialog(
                    onDismissRequest = { activeFullscreenImage = null },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.95f))
                            .clickable { activeFullscreenImage = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Shared by $senderName",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!msg.text.isNullOrBlank()) {
                                        Text(
                                            text = msg.text.replace(urlRegex, "").trim(),
                                            color = Color.White.copy(alpha = 0.8f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { activeFullscreenImage = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                }
                            }
                            
                            AsyncImage(
                                model = file,
                                contentDescription = "Fullscreen photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun GroupMessageBubble(
    message: GroupMessageEntity,
    isUser: Boolean,
    speaker: CharacterEntity?,
    serverUrl: String,
    modifier: Modifier = Modifier
) {
    if (message.sender == "system") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser && speaker != null) {
            if (speaker.avatarPath != null) {
                AsyncImage(
                    model = File(speaker.avatarPath),
                    contentDescription = speaker.name,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(speaker.name.take(1), color = MaterialTheme.colorScheme.onSecondary)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (!isUser && speaker != null) {
                Text(
                    text = speaker.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // Parse thoughts for character response
            val parsed = remember(message.text) { parseMessageText(message.text) }
            val contentParsed = remember(parsed.cleanText, serverUrl) {
                parseImageUrls(parsed.cleanText, serverUrl)
            }

            // Local image path
            if (message.imagePath != null) {
                AsyncImage(
                    model = File(message.imagePath),
                    contentDescription = "Portrait",
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.FillWidth
                )
                Spacer(modifier = Modifier.height(4.dp))
            } else if (contentParsed.imageUrl != null) {
                // Inline remote image path (loading)
                AsyncImage(
                    model = contentParsed.imageUrl,
                    contentDescription = "Generating Portrait",
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.FillWidth
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Thoughts block
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
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }

            // Speech text bubble
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Text(
                        text = formatMessageText(contentParsed.cleanText, isUser),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
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

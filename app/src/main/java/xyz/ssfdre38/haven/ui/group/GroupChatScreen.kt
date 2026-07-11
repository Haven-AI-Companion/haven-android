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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cloud
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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(serverUrl, token) {
        if (serverUrl.isNotBlank() && token.isNotBlank()) {
            viewModel.syncGroupMessages(serverUrl, token)
        }
    }

    val mainGradient = remember {
        listOf(Color(0xFF1E1035), Color(0xFF0C051A))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(mainGradient))
    ) {
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
                    )
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
                // Speaker Selector Chips
                Text(
                    text = "Active Responder:",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                items(messages, key = { it.id }) { message ->
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

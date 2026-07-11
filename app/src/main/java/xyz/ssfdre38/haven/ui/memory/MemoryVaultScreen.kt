package xyz.ssfdre38.haven.ui.memory

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.CharacterEntity
import xyz.ssfdre38.haven.data.database.MemoryEntity
import xyz.ssfdre38.haven.data.network.HavenHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryVaultScreen(
    characterId: Int,
    repository: DataRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var character by remember { mutableStateOf<CharacterEntity?>(null) }
    val memories by repository.getMemoriesForCharacter(characterId).collectAsState(initial = emptyList())

    // UI Dialog state for adding a custom memory fact
    var showAddDialog by remember { mutableStateOf(false) }
    var customFactText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("general") }

    val prefs = remember { context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE) }
    val serverUrl = remember {
        val host = prefs.getString("ash_host", "") ?: ""
        val port = prefs.getString("ash_port", "") ?: ""
        val trimmedHost = host.trimEnd('/')
        val trimmedPort = port.trim()
        if (trimmedHost.startsWith("http")) "$trimmedHost:$trimmedPort" else "http://$trimmedHost:$trimmedPort"
    }
    val token = remember { prefs.getString("auth_token", "") ?: "" }

    LaunchedEffect(characterId) {
        coroutineScope.launch(Dispatchers.IO) {
            character = repository.getCharacterById(characterId)
        }
    }

    LaunchedEffect(character) {
        val charName = character?.name ?: return@LaunchedEffect
        if (serverUrl.isNotBlank() && token.isNotBlank()) {
            coroutineScope.launch(Dispatchers.IO) {
                val serverMemories = HavenHttpClient.getMemories(serverUrl, token, charName)
                if (serverMemories.isNotEmpty()) {
                    repository.clearMemoriesForCharacter(characterId)
                    serverMemories.forEach { obj ->
                        repository.insertMemory(
                            MemoryEntity(
                                characterId = characterId,
                                content = obj.getString("content"),
                                category = obj.getString("category")
                            )
                        )
                    }
                }
            }
        }
    }

    // Companion-specific visual themes
    val themeGradients = remember(characterId) {
        when (characterId) {
            1 -> listOf(Color(0xFF1E1035), Color(0xFF0C051A)) // Nova: cosmic violet
            2 -> listOf(Color(0xFF3D1E03), Color(0xFF150A00)) // Aria: solar amber
            3 -> listOf(Color(0xFF05201A), Color(0xFF010A08)) // Lumina: glassmorphic teal
            else -> listOf(Color(0xFF1A1A1A), Color(0xFF121212)) // Default dark
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(themeGradients))
    ) {
        // Blurred ambient background orbs
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        when (characterId) {
                            1 -> Color(0xFFBB86FC).copy(alpha = 0.15f)
                            2 -> Color(0xFFFFB74D).copy(alpha = 0.15f)
                            3 -> Color(0xFF26A69A).copy(alpha = 0.15f)
                            else -> Color(0xFF03DAC6).copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(125.dp)
                    )
            )
        }

        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Memory Vault",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Curate what ${character?.name ?: "Companion"} remembers about you",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
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
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Fact",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.statusBarsPadding()
        ) { innerPadding ->
            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "No memories yet",
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "${character?.name ?: "Your companion"} has no memories saved yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Send messages in chat to extract facts dynamically,\nor tap '+' to add a memory manually!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.4f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = innerPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(memories, key = { it.id }) { memory ->
                        MemoryItemCard(
                            memory = memory,
                            characterColor = when (characterId) {
                                1 -> Color(0xFFBB86FC)
                                2 -> Color(0xFFFFB74D)
                                3 -> Color(0xFF26A69A)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            onDeleteClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.deleteMemory(memory)
                                    val charName = character?.name
                                    if (charName != null && serverUrl.isNotBlank() && token.isNotBlank()) {
                                        HavenHttpClient.deleteMemory(serverUrl, token, charName, memory.content)
                                    }
                                }
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        // Add custom memory dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Insert Memory Fact") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Write a clear statement about yourself that this companion should remember (e.g. 'Daniel loves coding in Kotlin').",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = customFactText,
                            onValueChange = { customFactText = it },
                            label = { Text("Memory Fact") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        Column {
                            Text("Category:", style = MaterialTheme.typography.bodyMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("personal", "preference", "event").forEach { cat ->
                                    FilterChip(
                                        selected = selectedCategory == cat,
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val fact = customFactText.trim()
                            if (fact.isNotBlank()) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.insertMemory(
                                        MemoryEntity(
                                            characterId = characterId,
                                            content = fact,
                                            category = selectedCategory
                                        )
                                    )
                                    val charName = character?.name
                                    if (charName != null && serverUrl.isNotBlank() && token.isNotBlank()) {
                                        HavenHttpClient.saveMemory(serverUrl, token, charName, fact, selectedCategory)
                                    }
                                }
                                customFactText = ""
                                showAddDialog = false
                            }
                        },
                        enabled = customFactText.isNotBlank()
                    ) {
                        Text("Add Fact")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun MemoryItemCard(
    memory: MemoryEntity,
    characterColor: Color,
    onDeleteClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(true) }

    AnimatedVisibility(
        visible = isVisible,
        exit = fadeOut() + shrinkVertically()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(end = 40.dp)) {
                // Category badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(characterColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = memory.category.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelSmall,
                        color = characterColor,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Date format
                val dateStr = remember(memory.createdAt) {
                    SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()).format(Date(memory.createdAt))
                }
                Text(
                    text = "Saved $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }

            IconButton(
                onClick = {
                    isVisible = false
                    // Delayed deletion to allow exit animation to complete smoothly
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        onDeleteClick()
                    }, 300)
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Memory",
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

package xyz.ssfdre38.haven.ui.main

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.text.style.TextAlign
import xyz.ssfdre38.haven.MemoryVault
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import xyz.ssfdre38.haven.GroupChat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import xyz.ssfdre38.haven.Chat
import xyz.ssfdre38.haven.Settings
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.CharacterEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: DataRepository,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(repository) }
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    // File picker launcher for PNG/JSON character cards
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importCharacterCard(context, uri)
        }
    }

    // Handle character deletion confirmation dialog
    // Handle character deletion confirmation dialog
    var characterToDelete by remember { mutableStateOf<CharacterEntity?>(null) }
    var activeCharacterActions by remember { mutableStateOf<CharacterEntity?>(null) }
    var showVoicePickerDialogFor by remember { mutableStateOf<CharacterEntity?>(null) }

    val vrmPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val selectedChar = activeCharacterActions
            if (selectedChar != null) {
                viewModel.updateCharacterVrm(context, selectedChar, uri)
                Toast.makeText(context, "3D Avatar updated successfully!", Toast.LENGTH_SHORT).show()
            }
        }
        activeCharacterActions = null
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showAddCompanionChooser by remember { mutableStateOf(false) }
    var showManualCreateDialog by remember { mutableStateOf(false) }
    var showServerImportDialog by remember { mutableStateOf(false) }
    var groupToDelete by remember { mutableStateOf<xyz.ssfdre38.haven.data.database.GroupChatEntity?>(null) }

    val sharedPrefs = remember { context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE) }
    val ashHost = sharedPrefs.getString("ash_host", "http://10.0.2.2") ?: "http://10.0.2.2"
    val ashPort = sharedPrefs.getString("ash_port", "18799") ?: "18799"
    val serverUrl = "${ashHost.trimEnd('/')}:${ashPort.trim()}"
    var availableVoices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(serverUrl) {
        xyz.ssfdre38.haven.data.network.HavenHttpClient.getAvailableVoices(serverUrl) { result ->
            result.onSuccess { voices ->
                availableVoices = voices
            }
        }
    }

    // Handle import state toasts/dialogs
    LaunchedEffect(importState) {
        when (importState) {
            is ImportStatus.Success -> {
                Toast.makeText(context, "Character imported successfully!", Toast.LENGTH_SHORT).show()
                viewModel.resetImportStatus()
            }
            is ImportStatus.Error -> {
                Toast.makeText(context, "Error: ${(importState as ImportStatus.Error).throwable.message}", Toast.LENGTH_LONG).show()
                viewModel.resetImportStatus()
            }
            else -> {}
        }
    }

    // Sync group chats and companions on startup
    LaunchedEffect(Unit) {
        viewModel.syncGroupChats(context)
        viewModel.loadServerCompanions(context)
    }

    val mainGradient = remember {
        listOf(Color(0xFF1E1035), Color(0xFF0C051A))
    }

    Box(
        modifier = Modifier
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
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            val prefs = LocalContext.current.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                            val userNamePref = remember { prefs.getString("user_name", "User") ?: "User" }
                            Text("Haven Portal", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("Welcome back, $userNamePref", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                    },
                    actions = {
                        IconButton(onClick = { onNavigate(Settings) }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
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
            floatingActionButton = {
                if (selectedTab != 2) {
                    FloatingActionButton(
                        onClick = {
                            if (selectedTab == 0) {
                                showAddCompanionChooser = true
                            } else {
                                showCreateGroupDialog = true
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.Add else Icons.Default.Group,
                            contentDescription = if (selectedTab == 0) "Import Character Card" else "Create Group Room"
                        )
                    }
                }
            },
            modifier = modifier
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                when (state) {
                    is MainScreenUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    is MainScreenUiState.Success -> {
                        val successState = state as MainScreenUiState.Success
                        val characters = successState.characters
                        val groupChats = successState.groupChats

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Custom Pill-shaped Glassmorphic Tab Switcher
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val tabs = listOf("Companions", "Group Rooms", "Explore")
                                    tabs.forEachIndexed { index, title ->
                                        val isSelected = selectedTab == index
                                        val targetBgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                        val targetContentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.6f)
                                        
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(targetBgColor)
                                                .clickable { selectedTab = index }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = title,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = targetContentColor
                                            )
                                        }
                                    }
                                }
                            }

                            if (selectedTab == 0) {
                                if (characters.isEmpty()) {
                                    EmptyStatePrompt(
                                        onImportClick = { filePickerLauncher.launch("image/png") }
                                    )
                                } else {
                                    CharacterList(
                                        characters = characters,
                                        onCharacterClick = { charId -> onNavigate(Chat(charId)) },
                                        onCharacterLongClick = { activeCharacterActions = it }
                                    )
                                }
                            } else if (selectedTab == 1) {
                                if (groupChats.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(32.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Group,
                                            contentDescription = "No Groups",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            modifier = Modifier.size(80.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "No Group Rooms",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Create a multi-companion room to start a group conversation with Nova, Aria, and others!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Button(onClick = { showCreateGroupDialog = true }) {
                                            Text("Create Group Room")
                                        }
                                    }
                                } else {
                                    GroupList(
                                        groups = groupChats,
                                        participants = characters,
                                        onGroupClick = { groupId -> onNavigate(GroupChat(groupId)) },
                                        onGroupLongClick = { groupToDelete = it }
                                    )
                                }
                            } else {
                                ExploreFeedScreen(
                                    currentCharacters = characters,
                                    onAddCompanion = { name, desc, pers, first, voice, sysPrompt ->
                                        viewModel.createCharacterManually(context, name, desc, pers, first, voice, sysPrompt)
                                    }
                                )
                            }
                    }
                }
                is MainScreenUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: ${(state as MainScreenUiState.Error).throwable.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Spinner during card import
            if (importState is ImportStatus.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Parsing metadata & caching avatar...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Companion Actions Sheet / Dialog
            activeCharacterActions?.let { character ->
                AlertDialog(
                    onDismissRequest = { activeCharacterActions = null },
                    title = { Text("Companion Actions: ${character.name}") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    activeCharacterActions = null
                                    onNavigate(Chat(character.id))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Chat Room", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                            }
                            TextButton(
                                onClick = {
                                    activeCharacterActions = null
                                    onNavigate(MemoryVault(character.id))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Memory Vault Fact Sheet", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                            }
                            TextButton(
                                onClick = {
                                    vrmPickerLauncher.launch("*/*")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (character.vrmModelPath != null) "Change 3D VRM Avatar" else "Set 3D VRM Avatar (.vrm / .glb)",
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (character.vrmModelPath != null) {
                                TextButton(
                                    onClick = {
                                        viewModel.removeCharacterVrm(context, character)
                                        Toast.makeText(context, "3D Avatar removed.", Toast.LENGTH_SHORT).show()
                                        activeCharacterActions = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Remove 3D Avatar (use 2D dynamic portrait instead)", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            TextButton(
                                onClick = {
                                    showVoicePickerDialogFor = character
                                    activeCharacterActions = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Select TTS Voice", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                            }
                            TextButton(
                                onClick = {
                                    characterToDelete = character
                                    activeCharacterActions = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Delete Companion", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { activeCharacterActions = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Delete verification dialog
            characterToDelete?.let { character ->
                AlertDialog(
                    onDismissRequest = { characterToDelete = null },
                    title = { Text("Delete ${character.name}?") },
                    text = { Text("This will permanently delete ${character.name} and your complete chat conversation history. This cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteCharacter(context, character)
                                characterToDelete = null
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { characterToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Voice Picker Dialog
            showVoicePickerDialogFor?.let { character ->
                AlertDialog(
                    onDismissRequest = { showVoicePickerDialogFor = null },
                    title = { Text("Select Voice for ${character.name}") },
                    text = {
                        if (availableVoices.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No voices retrieved. Make sure server is running.")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(availableVoices) { (voiceId, voiceName) ->
                                    val isSelected = voiceId == character.voiceId
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.updateCharacterVoice(character, voiceId)
                                                Toast.makeText(context, "Voice updated to $voiceName", Toast.LENGTH_SHORT).show()
                                                showVoicePickerDialogFor = null
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
                                        ),
                                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f))
                                    ) {
                                        Box(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = voiceName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showVoicePickerDialogFor = null }) {
                            Text("Close")
                        }
                    }
                )
            }

            // Create Group Dialog
            if (showCreateGroupDialog) {
                var groupName by remember { mutableStateOf("") }
                val selectedIds = remember { mutableStateListOf<Int>() }

                val characters = when (val s = state) {
                    is MainScreenUiState.Success -> s.characters
                    else -> emptyList()
                }

                AlertDialog(
                    onDismissRequest = { showCreateGroupDialog = false },
                    title = { Text("Create Group Room") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            TextField(
                                value = groupName,
                                onValueChange = { groupName = it },
                                label = { Text("Room Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Select Companions:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (characters.isEmpty()) {
                                Text("No companions imported yet.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            } else {
                                characters.forEach { char ->
                                    val checked = selectedIds.contains(char.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (checked) selectedIds.remove(char.id)
                                                else selectedIds.add(char.id)
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                if (checked) selectedIds.remove(char.id)
                                                else selectedIds.add(char.id)
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(char.name)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (groupName.isNotBlank() && selectedIds.isNotEmpty()) {
                                    viewModel.createGroupChat(context, groupName, selectedIds.toList())
                                    showCreateGroupDialog = false
                                }
                            },
                            enabled = groupName.isNotBlank() && selectedIds.isNotEmpty()
                        ) {
                            Text("Create")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateGroupDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Add Companion Source Chooser Dialog
            if (showAddCompanionChooser) {
                AlertDialog(
                    onDismissRequest = { showAddCompanionChooser = false },
                    title = { Text("Add New Companion") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Choose how you'd like to add a companion to your portal:",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Button(
                                onClick = {
                                    showAddCompanionChooser = false
                                    filePickerLauncher.launch("image/png")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import Character Card (PNG)")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    showAddCompanionChooser = false
                                    viewModel.loadServerCompanions(context)
                                    showServerImportDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Cloud, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import from Server")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    showAddCompanionChooser = false
                                    showManualCreateDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Manually")
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showAddCompanionChooser = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Import from Server Dialog
            if (showServerImportDialog) {
                val serverCompanions by viewModel.serverCompanions.collectAsStateWithLifecycle()
                val successState = state as? MainScreenUiState.Success
                val localNames = successState?.characters?.map { it.name.lowercase() } ?: emptyList()
                
                AlertDialog(
                    onDismissRequest = { showServerImportDialog = false },
                    title = { Text("Server Companion Directory") },
                    text = {
                        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            if (serverCompanions.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No companion cards found on server or server offline.")
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(serverCompanions) { companion ->
                                        val alreadyImported = localNames.contains(companion.name.lowercase())
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(companion.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                if (companion.description.isNotBlank()) {
                                                    Text(
                                                        companion.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.importCompanion(context, companion)
                                                    Toast.makeText(context, "Imported ${companion.name}!", Toast.LENGTH_SHORT).show()
                                                },
                                                enabled = !alreadyImported
                                            ) {
                                                Icon(
                                                    imageVector = if (alreadyImported) Icons.Default.Close else Icons.Default.Add,
                                                    contentDescription = "Import",
                                                    tint = if (alreadyImported) Color.Gray else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showServerImportDialog = false }) {
                            Text("Done")
                        }
                    }
                )
            }

            // Manual Companion Setup Dialog Form
            if (showManualCreateDialog) {
                var name by remember { mutableStateOf("") }
                var voiceId by remember { mutableStateOf("en_US-amy-medium") }
                var description by remember { mutableStateOf("") }
                var personality by remember { mutableStateOf("") }
                var firstMessage by remember { mutableStateOf("") }
                var systemPrompt by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = { showManualCreateDialog = false },
                    title = { Text("Manual Companion Setup") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            var dropdownExpanded by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = availableVoices.find { it.first == voiceId }?.second ?: voiceId,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Companion Voice") },
                                        trailingIcon = {
                                            IconButton(onClick = { dropdownExpanded = true }) {
                                                Icon(
                                                    imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                                                    contentDescription = "Select Voice"
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    ) {
                                        availableVoices.forEach { voice ->
                                            DropdownMenuItem(
                                                text = { Text(voice.second) },
                                                onClick = {
                                                    voiceId = voice.first
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                var isPlayingSample by remember { mutableStateOf(false) }
                                val token = sharedPrefs.getString("auth_token", "") ?: ""

                                IconButton(
                                    onClick = {
                                        if (isPlayingSample) return@IconButton
                                        isPlayingSample = true
                                        val sampleText = "Hello! I am $name, your AI companion."
                                        xyz.ssfdre38.haven.data.network.HavenHttpClient.generateTts(
                                            serverUrl = serverUrl,
                                            token = token,
                                            text = sampleText,
                                            voice = voiceId
                                        ) { result ->
                                            result.fold(
                                                onSuccess = { relativeUrl ->
                                                    val resolvedUrl = if (relativeUrl.startsWith("/")) {
                                                        val host = serverUrl.trimEnd('/')
                                                        if (host.startsWith("http")) "$host$relativeUrl" else "http://$host$relativeUrl"
                                                    } else {
                                                        relativeUrl
                                                    }
                                                    (context as? android.app.Activity)?.runOnUiThread {
                                                        try {
                                                            android.media.MediaPlayer().apply {
                                                                setDataSource(resolvedUrl)
                                                                prepareAsync()
                                                                setOnPreparedListener { start() }
                                                                setOnCompletionListener {
                                                                    release()
                                                                    isPlayingSample = false
                                                                }
                                                                setOnErrorListener { _, _, _ ->
                                                                    release()
                                                                    isPlayingSample = false
                                                                    true
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            isPlayingSample = false
                                                        }
                                                    }
                                                },
                                                onFailure = {
                                                    isPlayingSample = false
                                                }
                                            )
                                        }
                                    },
                                    enabled = !isPlayingSample && voiceId.isNotBlank(),
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    if (isPlayingSample) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Filled.PlayArrow,
                                            contentDescription = "Play voice sample",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description / Bio") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = personality,
                                onValueChange = { personality = it },
                                label = { Text("Personality Traits") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = firstMessage,
                                onValueChange = { firstMessage = it },
                                label = { Text("First Greeting Message") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = systemPrompt,
                                onValueChange = { systemPrompt = it },
                                label = { Text("System Instruction Override (Optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (name.isNotBlank()) {
                                    viewModel.createCharacterManually(
                                        context = context,
                                        name = name.trim(),
                                        description = description.trim(),
                                        personality = personality.trim(),
                                        firstMessage = firstMessage.trim(),
                                        voiceId = voiceId.trim(),
                                        systemPrompt = systemPrompt.trim()
                                    )
                                    showManualCreateDialog = false
                                }
                            },
                            enabled = name.isNotBlank()
                        ) {
                            Text("Create")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showManualCreateDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Delete Group Dialog
            groupToDelete?.let { group ->
                AlertDialog(
                    onDismissRequest = { groupToDelete = null },
                    title = { Text("Delete Room?") },
                    text = { Text("This will permanently delete the group room '${group.name}' and all shared chat logs inside it. This cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteGroupChat(context, group)
                                groupToDelete = null
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { groupToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
}

@Composable
fun EmptyStatePrompt(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to Haven",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Import a SillyTavern PNG character card or JSON file to start chatting privately with custom local characters.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onImportClick) {
            Text("Import Character Card")
        }
    }
}

@Composable
fun CharacterList(
    characters: List<CharacterEntity>,
    onCharacterClick: (Int) -> Unit,
    onCharacterLongClick: (CharacterEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    if (isTablet) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(characters, key = { it.id }) { character ->
                CharacterCard(
                    character = character,
                    onClick = { onCharacterClick(character.id) },
                    onLongClick = { onCharacterLongClick(character) }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(characters, key = { it.id }) { character ->
                CharacterCard(
                    character = character,
                    onClick = { onCharacterClick(character.id) },
                    onLongClick = { onCharacterLongClick(character) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CharacterCard(
    character: CharacterEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val level = (character.relationshipXp / 100) + 1
    val xpInCurrentLevel = character.relationshipXp % 100
    val xpProgress = xpInCurrentLevel / 100f

    val levelColor = when {
        level >= 20 -> Color(0xFFFFD700) // Gold
        level >= 10 -> Color(0xFF9C27B0) // Purple
        level >= 5  -> Color(0xFF2196F3) // Blue
        else        -> Color(0xFF4CAF50) // Green
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF16161E).copy(alpha = 0.45f)
        ),
        border = BorderStroke(
            1.dp,
            androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f))
            )
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with level-colored glowing border
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(levelColor.copy(alpha = 0.15f))
                        .border(1.5.dp, levelColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CharacterAvatar(
                        character = character,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Level badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = levelColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Lv $level",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = levelColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    // Mood + location status line
                    val statusText = buildString {
                        if (character.currentMood.isNotBlank()) append("${character.currentMood}  ")
                        if (character.currentLocation.isNotBlank()) append("📍 ${character.currentLocation}")
                    }.trim()
                    if (statusText.isNotBlank()) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                    Text(
                        text = character.description.ifBlank { "No description provided." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onLongClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }

            // XP progress bar
            LinearProgressIndicator(
                progress = { xpProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = levelColor,
                trackColor = levelColor.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun CharacterAvatar(
    character: CharacterEntity,
    modifier: Modifier = Modifier
) {
    val fileExists = remember(character.avatarPath) {
        character.avatarPath?.let { File(it).exists() } == true
    }
    if (fileExists && character.avatarPath != null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val imgFile = remember(character.avatarPath) { File(character.avatarPath) }
        val request = remember(imgFile.absolutePath, imgFile.lastModified()) {
            coil.request.ImageRequest.Builder(context)
                .data(imgFile)
                .memoryCacheKey(imgFile.absolutePath + "_" + imgFile.lastModified())
                .diskCacheKey(imgFile.absolutePath + "_" + imgFile.lastModified())
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = character.name,
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentScale = ContentScale.Crop
        )
    } else {
        // Render first letter with a nice background color
        val firstLetter = character.name.take(1).uppercase()
        val backgroundColor = remember(character.name) {
            val colors = listOf(
                Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
                Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF00BCD4),
                Color(0xFF009688), Color(0xFF4CAF50), Color(0xFFFF9800)
            )
            colors[character.name.hashCode().coerceAtLeast(0) % colors.size]
        }
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstLetter,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupList(
    groups: List<xyz.ssfdre38.haven.data.database.GroupChatEntity>,
    participants: List<CharacterEntity>,
    onGroupClick: (Int) -> Unit,
    onGroupLongClick: (xyz.ssfdre38.haven.data.database.GroupChatEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    if (isTablet) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(groups, key = { it.id }) { group ->
                val roomParticipants = remember(group.characterIdsString, participants) {
                    val ids = group.characterIdsString.split(",").mapNotNull { it.trim().toIntOrNull() }
                    participants.filter { ids.contains(it.id) }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(
                            onClick = { onGroupClick(group.id) },
                            onLongClick = { onGroupLongClick(group) }
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF16161E).copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f))
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (roomParticipants.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = "No Groups",
                                        tint = MaterialTheme.colorScheme.onSecondary
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.size(44.dp)) {
                                    roomParticipants.take(4).forEachIndexed { index, p ->
                                        val offset = when (index) {
                                            0 -> Modifier.align(Alignment.TopStart)
                                            1 -> Modifier.align(Alignment.TopEnd)
                                            2 -> Modifier.align(Alignment.BottomStart)
                                            else -> Modifier.align(Alignment.BottomEnd)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .then(offset)
                                                .clip(CircleShape)
                                                .background(Color.DarkGray)
                                                .border(1.dp, Color.Black, CircleShape)
                                        ) {
                                            if (p.avatarPath != null && File(p.avatarPath).exists()) {
                                                AsyncImage(
                                                    model = File(p.avatarPath),
                                                    contentDescription = p.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(p.name.take(1), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = group.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = roomParticipants.joinToString { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(groups, key = { it.id }) { group ->
                val roomParticipants = remember(group.characterIdsString, participants) {
                    val ids = group.characterIdsString.split(",").mapNotNull { it.trim().toIntOrNull() }
                    participants.filter { ids.contains(it.id) }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(
                            onClick = { onGroupClick(group.id) },
                            onLongClick = { onGroupLongClick(group) }
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF16161E).copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f))
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (roomParticipants.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = "No Groups",
                                        tint = MaterialTheme.colorScheme.onSecondary
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.size(44.dp)) {
                                    roomParticipants.take(4).forEachIndexed { index, p ->
                                        val offset = when (index) {
                                            0 -> Modifier.align(Alignment.TopStart)
                                            1 -> Modifier.align(Alignment.TopEnd)
                                            2 -> Modifier.align(Alignment.BottomStart)
                                            else -> Modifier.align(Alignment.BottomEnd)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .then(offset)
                                                .clip(CircleShape)
                                                .background(Color.DarkGray)
                                                .border(1.dp, Color.Black, CircleShape)
                                        ) {
                                            if (p.avatarPath != null && File(p.avatarPath).exists()) {
                                                AsyncImage(
                                                    model = File(p.avatarPath),
                                                    contentDescription = p.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(p.name.take(1), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = group.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = roomParticipants.joinToString { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ExploreCompanion(
    val name: String,
    val description: String,
    val personality: String,
    val firstMessage: String,
    val voiceId: String,
    val systemPrompt: String,
    val category: String,
    val gradientColors: List<Color>
)

@Composable
fun ExploreFeedScreen(
    currentCharacters: List<CharacterEntity>,
    onAddCompanion: (String, String, String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exploreList = remember {
        listOf(
            ExploreCompanion(
                name = "Aria",
                description = "Your virtual senior software architect and systems design mentor.",
                personality = "Pragmatic, technical, encouraging, and clear.",
                firstMessage = "Hey! I'm Aria, your technical mentor. What project are we architecting today?",
                voiceId = "en_US-kristin-medium",
                systemPrompt = "Roleplay as Aria, a senior software engineer who gives clear, clean, and optimized code advice.",
                category = "Developer",
                gradientColors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
            ),
            ExploreCompanion(
                name = "Zephyr",
                description = "A serene companion to guide you through reflections, philosophy, and stress reduction.",
                personality = "Calm, empathetic, poetic, and philosophical.",
                firstMessage = "Welcome, traveler. Take a deep breath. Let the noise of the world fade away. What reflections weigh on your heart today?",
                voiceId = "en_US-joe-medium",
                systemPrompt = "Roleplay as Zephyr, a peaceful zen philosopher who encourages reflection and peace of mind.",
                category = "Wellness",
                gradientColors = listOf(Color(0xFF14B8A6), Color(0xFF0D9488))
            ),
            ExploreCompanion(
                name = "Luna",
                description = "A fast-talking cyberpunk netrunner who specializes in bypassing security ICE.",
                personality = "Snarky, street-smart, energetic, and highly technical.",
                firstMessage = "Hey. Make it quick. The corp drones are sniffing around this port, but our link is solid. What's the play? Looking to bypass some security ICE?",
                voiceId = "en_US-ljspeech-medium",
                systemPrompt = "Roleplay as Luna, a rogue cyberpunk netrunner from Neon City who uses street slang.",
                category = "Cyberpunk",
                gradientColors = listOf(Color(0xFFEF4444), Color(0xFFDC2626))
            ),
            ExploreCompanion(
                name = "Dr. Haze",
                description = "An eccentric professor of theoretical physics who speaks about timeline shifts.",
                personality = "Eccentric, passionate, extremely knowledgeable, and slightly chaotic.",
                firstMessage = "Aha! You've successfully tunneled into my coordinates! Don't touch the tachyon emitter. What dimension or physical anomaly are we probing today?",
                voiceId = "en_US-joe-medium",
                systemPrompt = "Roleplay as Dr. Haze, an eccentric but brilliant physics professor who speaks excitedly about science anomalies.",
                category = "Science",
                gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                text = "Recommended Companions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        items(exploreList) { item ->
            val isImported = currentCharacters.any { it.name.lowercase() == item.name.lowercase() }
            
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF16161E).copy(alpha = 0.45f)
                ),
                border = BorderStroke(
                    1.dp,
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f))
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = item.gradientColors
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.name.take(1),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        text = item.category,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (isImported) {
                            Button(
                                onClick = {},
                                enabled = false,
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text("Added")
                            }
                        } else {
                            Button(
                                onClick = {
                                    onAddCompanion(
                                        item.name,
                                        item.description,
                                        item.personality,
                                        item.firstMessage,
                                        item.voiceId,
                                        item.systemPrompt
                                    )
                                }
                            ) {
                                Text("Add Companion")
                            }
                        }
                    }
                }
            }
        }
    }
}

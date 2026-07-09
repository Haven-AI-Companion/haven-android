package xyz.ssfdre38.haven.ui.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import xyz.ssfdre38.haven.GroupChat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var characterToDelete by remember { mutableStateOf<CharacterEntity?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showAddCompanionChooser by remember { mutableStateOf(false) }
    var showManualCreateDialog by remember { mutableStateOf(false) }
    var showServerImportDialog by remember { mutableStateOf(false) }
    var groupToDelete by remember { mutableStateOf<xyz.ssfdre38.haven.data.database.GroupChatEntity?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Haven Portal", fontWeight = FontWeight.Bold) 
                },
                actions = {
                    IconButton(onClick = { onNavigate(Settings) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
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
                shape = FloatingActionButtonDefaults.largeShape
            ) {
                Icon(
                    imageVector = if (selectedTab == 0) Icons.Default.Add else Icons.Default.Group,
                    contentDescription = if (selectedTab == 0) "Import Character Card" else "Create Group Room"
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                        // Custom Pill-shaped Tab Switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { selectedTab = 0 }
                            ) {
                                Text(
                                    text = "Companions",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { selectedTab = 1 }
                            ) {
                                Text(
                                    text = "Group Rooms",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (selectedTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = if (selectedTab == 2) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { selectedTab = 2 }
                            ) {
                                Text(
                                    text = "Explore",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
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
                                    onCharacterLongClick = { characterToDelete = it }
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
                                    viewModel.createCharacterManually(name, desc, pers, first, voice, sysPrompt)
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

            // Delete verification dialog
            characterToDelete?.let { character ->
                AlertDialog(
                    onDismissRequest = { characterToDelete = null },
                    title = { Text("Delete ${character.name}?") },
                    text = { Text("This will permanently delete ${character.name} and your complete chat conversation history. This cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteCharacter(character)
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
                                    viewModel.createGroupChat(groupName, selectedIds.toList())
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
                                                    viewModel.importCompanion(companion)
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
                            OutlinedTextField(
                                value = voiceId,
                                onValueChange = { voiceId = it },
                                label = { Text("Voice ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
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
                                viewModel.deleteGroupChat(group)
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
            .clip(CardDefaults.shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                CharacterAvatar(
                    character = character,
                    modifier = Modifier.size(60.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        AsyncImage(
            model = File(character.avatarPath),
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
                    .combinedClickable(
                        onClick = { onGroupClick(group.id) },
                        onLongClick = { onGroupLongClick(group) }
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
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
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        } else {
                            val showAvatars = roomParticipants.take(3)
                            showAvatars.forEachIndexed { idx, p ->
                                val offset = (idx * 10).dp
                                Box(
                                    modifier = Modifier
                                        .offset(x = offset - 10.dp)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                ) {
                                    if (p.avatarPath != null) {
                                        AsyncImage(
                                            model = File(p.avatarPath),
                                            contentDescription = null,
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

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = group.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = roomParticipants.joinToString { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        items(exploreList) { item ->
            val isImported = currentCharacters.any { it.name.lowercase() == item.name.lowercase() }
            
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
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
                                    color = MaterialTheme.colorScheme.onBackground
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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

package xyz.ssfdre38.haven.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.MessageEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    characterId: Int,
    repository: DataRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamically retrieve character info
    var characterName by remember { mutableStateOf("Companion") }
    LaunchedEffect(characterId) {
        val char = repository.getCharacterById(characterId)
        if (char != null) {
            characterName = char.name
        }
    }

    // Collect all messages and filter out those containing images
    val messages by repository.getMessagesForCharacter(characterId).collectAsState(initial = emptyList())
    val imageMessages = remember(messages) {
        messages.filter { it.imagePath != null }
    }

    var activeImageMessage by remember { mutableStateOf<MessageEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$characterName's Gallery", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (imageMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No generated images yet.\nTap the image icon in chat to generate a custom portrait!",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(imageMessages, key = { it.id }) { msg ->
                        GridImageCard(
                            message = msg,
                            onClick = { activeImageMessage = msg }
                        )
                    }
                }
            }

            // Lightbox dialog for fullscreen viewing
            activeImageMessage?.let { msg ->
                LightboxViewer(
                    message = msg,
                    onDismiss = { activeImageMessage = null }
                )
            }
        }
    }
}

@Composable
fun GridImageCard(
    message: MessageEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val file = remember(message.imagePath) {
            message.imagePath?.let { File(it) }
        }
        AsyncImage(
            model = file,
            contentDescription = "Generated portfolio artwork",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun LightboxViewer(
    message: MessageEntity,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Main high resolution image
            val file = remember(message.imagePath) {
                message.imagePath?.let { File(it) }
            }
            AsyncImage(
                model = file,
                contentDescription = "Fullscreen view",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss),
                contentScale = ContentScale.Fit
            )

            // Header close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // Prompt metadata card at the bottom (if text exists)
            if (message.text.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Generation Prompt",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

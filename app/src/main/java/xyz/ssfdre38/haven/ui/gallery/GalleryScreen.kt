package xyz.ssfdre38.haven.ui.gallery

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

    val mainGradient = remember {
        listOf(Color(0xFF1E1035), Color(0xFF0C051A))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(mainGradient))
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
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(alpha = 0.15f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(orb1X.dp.toPx(), orb1Y.dp.toPx()),
                    radius = 300.dp.toPx()
                ),
                radius = 300.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(orb1X.dp.toPx(), orb1Y.dp.toPx())
            )
            
            drawCircle(
                brush = Brush.radialGradient(
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
                    title = { Text("$characterName's Gallery", fontWeight = FontWeight.Bold, color = Color.White) },
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
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF16161E).copy(alpha = 0.45f)
        ),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f))
            )
        )
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

package xyz.ssfdre38.haven.ui.diary

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.DiaryEntryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    characterId: Int,
    characterName: String,
    repository: DataRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel = viewModel(key = "diary_$characterId") { DiaryViewModel(repository, characterId) }
) {
    val context = LocalContext.current
    val diaryEntries by viewModel.diaryEntries.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var selectedEntry by remember { mutableStateOf<DiaryEntryEntity?>(null) }

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

    LaunchedEffect(diaryEntries) {
        if (selectedEntry == null && diaryEntries.isNotEmpty()) {
            selectedEntry = diaryEntries.first()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(serverUrl, token) {
        if (serverUrl.isNotBlank() && token.isNotBlank() && characterName.isNotBlank()) {
            viewModel.syncDiaries(serverUrl, token, characterName)
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
                    title = {
                        Text(
                            text = "$characterName's Journal",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
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
                        IconButton(
                            onClick = {
                                if (serverUrl.isBlank() || token.isBlank()) {
                                    Toast.makeText(context, "Please configure server settings first", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                viewModel.generateTodayEntry(context, serverUrl, token, characterName)
                            },
                            enabled = !isGenerating
                        ) {
                            Icon(
                                imageVector = Icons.Default.Create,
                                contentDescription = "Write Today's Entry",
                                tint = MaterialTheme.colorScheme.primary
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
            if (isGenerating) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "$characterName is writing in her journal...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontStyle = FontStyle.Italic
                    )
                }
            } else if (diaryEntries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = "No Entries",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "The pages are empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$characterName hasn't written any reflections yet. Chat with her today, then tap the pen icon at the top to ask her to write down her thoughts!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        items(diaryEntries) { entry ->
                            val isSelected = entry.id == selectedEntry?.id
                            val dateLabel = remember(entry.dateString) {
                                try {
                                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    val outputFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                                    val date = inputFormat.parse(entry.dateString)
                                    date?.let { outputFormat.format(it) } ?: entry.dateString
                                } catch (e: Exception) {
                                    entry.dateString
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { selectedEntry = entry },
                                border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = dateLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    selectedEntry?.let { entry ->
                        val formattedDate = remember(entry.dateString) {
                            try {
                                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val outputFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                                val date = inputFormat.parse(entry.dateString)
                                date?.let { outputFormat.format(it) } ?: entry.dateString
                            } catch (e: Exception) {
                                entry.dateString
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF16161E).copy(alpha = 0.45f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f))
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = formattedDate,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    thickness = 1.dp
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = entry.content,
                                        fontSize = 16.sp,
                                        lineHeight = 26.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.fillMaxWidth()
                                    )
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

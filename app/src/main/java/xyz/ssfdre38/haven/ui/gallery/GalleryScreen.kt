package xyz.ssfdre38.haven.ui.gallery

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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AccountCircle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Structured model representing an item in the companion's gallery.
 * Combines physical files found in internal storage with optional prompt metadata from the message database.
 */
data class GalleryItem(
    val file: File,
    val prompt: String? = null,
    val messageId: Int? = null
)

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

    // Collect all messages from database to cross-reference image metadata
    val messages by repository.getMessagesForCharacter(characterId).collectAsState(initial = emptyList())
    
    val context = LocalContext.current
    var galleryItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    val failedDeletes = remember { java.util.Collections.synchronizedSet(mutableSetOf<String>()) }

    // Re-compile gallery items by combining database records with physical files on disk
    LaunchedEffect(messages, characterName) {
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val items = mutableListOf<GalleryItem>()

                // 1. Add all images from DB messages — only include files that actually exist on THIS device
                val dbItems = messages
                    .filter { it.imagePath != null && !failedDeletes.contains(it.imagePath) }
                    .mapNotNull { msg ->
                        val f = File(msg.imagePath!!)
                        if (f.exists()) GalleryItem(file = f, prompt = msg.text, messageId = msg.id) else null
                    }
                items.addAll(dbItems)

                // 2. Scan companion images directory and add files not already present
                if (characterName != "Companion") {
                    val cleanName = characterName.replace("[^a-zA-Z0-9]".toRegex(), "_")
                    val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                    val dirsToScan = listOfNotNull(
                        File(baseDir, "companion/images/$cleanName"),
                        File(context.filesDir, "companion/images/$cleanName")
                    ).distinct()
                    for (companionDir in dirsToScan) {
                        if (companionDir.exists() && companionDir.isDirectory) {
                            val files = companionDir.listFiles { file ->
                                file.isFile && (file.name.endsWith(".png") || file.name.endsWith(".jpg") || file.name.endsWith(".webp")) && !failedDeletes.contains(file.absolutePath)
                            }
                            files?.forEach { file ->
                                if (items.none { it.file.name == file.name }) {
                                    items.add(GalleryItem(file = file))
                                }
                            }
                        }
                    }
                }

                // Deduplicate by canonical path to prevent LazyGrid duplicate key crash
                val pathDeduplicated = items
                    .distinctBy { item -> runCatching { item.file.canonicalPath }.getOrElse { item.file.absolutePath } }

                // Detect and consolidate physical duplicates (files with identical content)
                val groupedBySize = pathDeduplicated.groupBy { it.file.length() }
                val duplicatesToDelete = mutableListOf<GalleryItem>()
                val itemsToKeep = mutableListOf<GalleryItem>()

                for ((size, fileList) in groupedBySize) {
                    if (fileList.size <= 1) {
                        itemsToKeep.addAll(fileList)
                        continue
                    }

                    // Compute hashes only for files that share the exact same size (fast!)
                    val groupedByHash = fileList.groupBy { it.file.calculateMd5() }
                    for ((hash, hashList) in groupedByHash) {
                        if (hashList.size <= 1) {
                            itemsToKeep.addAll(hashList)
                            continue
                        }

                        // Keep the first file as primary
                        val primary = hashList.first()
                        itemsToKeep.add(primary)

                        // Rest are duplicates
                        duplicatesToDelete.addAll(hashList.drop(1))
                    }
                }

                // If duplicates are found, update database references and clean up disk
                if (duplicatesToDelete.isNotEmpty()) {
                    var consolidatedCount = 0
                    for (dup in duplicatesToDelete) {
                        val primaryItem = itemsToKeep.firstOrNull { it.file.calculateMd5() == dup.file.calculateMd5() }
                        if (primaryItem != null) {
                            // Update Room DB message if duplicate was attached to one
                            if (dup.messageId != null) {
                                val msg = repository.getMessageById(dup.messageId)
                                if (msg != null) {
                                    repository.insertMessage(msg.copy(imagePath = primaryItem.file.absolutePath))
                                }
                            }
                            
                            // Delete duplicate file from disk
                            try {
                                if (dup.file.exists() && dup.file.absolutePath != primaryItem.file.absolutePath) {
                                    val deleted = dup.file.delete()
                                    if (deleted) {
                                        consolidatedCount++
                                    } else {
                                        failedDeletes.add(dup.file.absolutePath)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                failedDeletes.add(dup.file.absolutePath)
                            }
                        }
                    }
                    if (consolidatedCount > 0) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "Consolidated $consolidatedCount duplicate images", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                itemsToKeep.sortedByDescending { it.file.lastModified() }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
        galleryItems = result
    }


    var activeItem by remember { mutableStateOf<GalleryItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortNewestFirst by remember { mutableStateOf(true) }

    val filteredItems = remember(galleryItems, searchQuery, sortNewestFirst) {
        val filtered = if (searchQuery.isBlank()) {
            galleryItems
        } else {
            galleryItems.filter { item ->
                item.prompt?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        if (sortNewestFirst) {
            filtered.sortedByDescending { it.file.lastModified() }
        } else {
            filtered.sortedBy { it.file.lastModified() }
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
        // Subtle secondary overlay (no GPU-intensive effects for compatibility)
        Box(modifier = Modifier.fillMaxSize())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "$characterName's Gallery",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Chat"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
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
                if (galleryItems.isEmpty()) {
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search and Filter Bar Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search by prompt...", color = Color.White.copy(alpha = 0.5f)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { sortNewestFirst = !sortNewestFirst },
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Toggle Sort Order",
                                    tint = Color.White
                                )
                            }
                        }

                        if (filteredItems.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching images found.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
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
                                items(filteredItems, key = { it.file.absolutePath }) { item ->
                                    GridImageCard(
                                        item = item,
                                        onClick = { activeItem = item }
                                    )
                                }
                            }
                        }
                    }
                }

                // Lightbox dialog for fullscreen viewing
                activeItem?.let { item ->
                    LightboxViewer(
                        item = item,
                        characterId = characterId,
                        repository = repository,
                        onDeleteSuccess = {
                            failedDeletes.add(item.file.absolutePath)
                            galleryItems = galleryItems.filter { it.file.absolutePath != item.file.absolutePath }
                            activeItem = null
                        },
                        onDismiss = { activeItem = null }
                    )
                }
            }
        }
    }
}

@Composable
fun GridImageCard(
    item: GalleryItem,
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
        val context = LocalContext.current
        val request = remember(item.file.absolutePath, item.file.lastModified()) {
            coil.request.ImageRequest.Builder(context)
                .data(item.file)
                .memoryCacheKey(item.file.absolutePath + "_" + item.file.lastModified())
                .diskCacheKey(item.file.absolutePath + "_" + item.file.lastModified())
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = "Generated portfolio artwork",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun LightboxViewer(
    item: GalleryItem,
    characterId: Int,
    repository: DataRepository,
    onDeleteSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var showPrompt by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
            val context = LocalContext.current
            val request = remember(item.file.absolutePath, item.file.lastModified()) {
                coil.request.ImageRequest.Builder(context)
                    .data(item.file)
                    .memoryCacheKey(item.file.absolutePath + "_" + item.file.lastModified())
                    .diskCacheKey(item.file.absolutePath + "_" + item.file.lastModified())
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = "Fullscreen view",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss),
                contentScale = ContentScale.Fit
            )

            // Header close, download, and info buttons
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!item.prompt.isNullOrBlank()) {
                    IconButton(
                        onClick = { showPrompt = !showPrompt },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Toggle Prompt Info",
                            tint = if (showPrompt) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }

                if (item.file.exists()) {
                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            val success = saveImageToGallery(
                                context = context,
                                sourceFile = item.file,
                                displayName = "Haven_${item.file.name}"
                            )
                            if (success) {
                                Toast.makeText(context, "Saved to Pictures/Haven!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save to Gallery",
                            tint = Color.White
                        )
                    }

                    // Set as Avatar button
                    IconButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val char = repository.getCharacterById(characterId)
                                if (char != null) {
                                    val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                                    val avatarsDir = File(baseDir, "avatars")
                                    if (!avatarsDir.exists()) {
                                        avatarsDir.mkdirs()
                                    }
                                    val extension = item.file.extension.lowercase()
                                    val avatarFile = File(avatarsDir, "avatar_${char.name.replace("[^a-zA-Z0-9]".toRegex(), "_")}_${java.util.UUID.randomUUID()}.$extension")
                                    
                                    try {
                                        item.file.inputStream().use { input ->
                                            avatarFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        
                                        val updatedChar = char.copy(avatarPath = avatarFile.absolutePath)
                                        repository.updateCharacter(updatedChar)
                                        
                                        val payload = org.json.JSONObject().apply {
                                            put("name", char.name)
                                            put("voiceId", char.voiceId.ifBlank { "en_US-amy-medium" })
                                            put("description", char.description)
                                            put("personality", char.personality)
                                            put("scenario", "")
                                            put("firstMessage", char.firstMessage)
                                            put("systemPrompt", char.systemPrompt)
                                            put("avatarPath", avatarFile.absolutePath)
                                        }
                                        xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                                            context,
                                            "SAVE_COMPANION",
                                            payload
                                        )
                                        
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            Toast.makeText(context, "Avatar updated successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            Toast.makeText(context, "Failed to set avatar: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Set as Avatar",
                            tint = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Image",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Image") },
                    text = { Text("Are you sure you want to permanently delete this image from your device and chat history?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                val fileDeleted = try {
                                    item.file.delete()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    false
                                }
                                
                                if (fileDeleted) {
                                    if (item.messageId != null) {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val msg = repository.getMessageById(item.messageId)
                                            if (msg != null) {
                                                if (msg.text == "*Sends a photo.*" || msg.text.isBlank()) {
                                                    repository.deleteMessage(msg)
                                                } else {
                                                    repository.insertMessage(msg.copy(imagePath = null))
                                                }
                                            }
                                        }
                                    }
                                    Toast.makeText(context, "Image deleted successfully", Toast.LENGTH_SHORT).show()
                                    onDeleteSuccess()
                                } else {
                                    Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF1E1035),
                    titleContentColor = Color.White,
                    textContentColor = Color.White.copy(alpha = 0.8f)
                )
            }

            // Prompt metadata card at the bottom (if toggled and text exists)
            if (showPrompt && !item.prompt.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
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
                            text = item.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun saveImageToGallery(context: android.content.Context, sourceFile: File, displayName: String): Boolean {
    val resolver = context.contentResolver
    val imageDetails = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Haven")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val uri = resolver.insert(collection, imageDetails) ?: return false

    return try {
        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input ->
                input.copyTo(out)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imageDetails.clear()
            imageDetails.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, imageDetails, null, null)
        }
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

private fun File.calculateMd5(): String {
    return try {
        val md = MessageDigest.getInstance("MD5")
        inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                md.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        absolutePath
    }
}

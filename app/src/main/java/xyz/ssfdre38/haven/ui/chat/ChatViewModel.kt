package xyz.ssfdre38.haven.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.CharacterEntity
import xyz.ssfdre38.haven.data.database.MemoryEntity
import xyz.ssfdre38.haven.data.database.MessageEntity
import xyz.ssfdre38.haven.data.network.HavenHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val characterId: Int,
    private val repository: DataRepository
) : ViewModel() {

    val character: StateFlow<CharacterEntity?> = repository.getCharacterFlow(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<MessageEntity>> = repository.getMessagesForCharacter(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Holds the partial streamed response being built
    private var streamingMessageId: Int = -1
    private var streamBuffer = StringBuilder()

    fun sendMessage(context: Context, serverUrl: String, token: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true

            // Build the full system+context prompt for this character
            val char = repository.getCharacterById(characterId)
            // Fetch memories BEFORE buildString (suspend call must be in coroutine body)
            val memories = repository.getRecentMemories(characterId, 20)
            val level = if (char != null) (char.relationshipXp / 100) + 1 else 1
            val relationshipTitle = when {
                level >= 20 -> "Partner"
                level >= 10 -> "Close Friend"
                level >= 5  -> "Friend"
                else        -> "Acquaintance"
            }
            val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
            val userName = sharedPrefs.getString("user_name", "User") ?: "User"

            val systemContext = buildString {
                if (char != null) {
                    appendLine("You are ${char.name}.")
                    if (char.personality.isNotBlank()) appendLine("Personality: ${char.personality}")
                    if (char.scenario.isNotBlank()) appendLine("Scenario: ${char.scenario}")
                    if (char.systemPrompt.isNotBlank()) appendLine(char.systemPrompt)
                    
                    // Inject display name instruction
                    appendLine("The user's name is $userName. You must address the user as $userName instead of any other name.")

                    // Inject relationship context
                    appendLine("Relationship Status with $userName: $relationshipTitle (Level $level)")
                    appendLine("Act toward $userName reflecting your relationship status ($relationshipTitle). Adapt your warmth, level of intimacy, and dialogue style accordingly.")
                    
                    // Inject dynamic location & clothing state
                    val loc = char.currentLocation.ifBlank { "Cozy Haven Room" }
                    val outfit = char.currentOutfit.ifBlank { "Casual Attire" }
                    val mood = char.currentMood.ifBlank { "neutral" }
                    appendLine("Current Location: $loc")
                    appendLine("Current Outfit: $outfit")
                    appendLine("Current Expression/Mood: $mood")

                    // Inject long-term memories
                    if (memories.isNotEmpty()) {
                        appendLine()
                        appendLine("[Memories you have about $userName:]")
                        memories.forEach { appendLine("- ${it.content}") }
                    }
                }
                
                // Inject real time context
                val currentTimeStr = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                appendLine("Current Time of Day: $currentTimeStr")
                
                appendLine()
                appendLine("[System Rule: Before responding, you MUST write down your inner thoughts, plans, or reasoning inside <thought>...</thought> tags, followed by your actual response to $userName. Do not omit the tags.]")
                appendLine("[System Instruction: You can dynamically update your location, outfit, or expression/mood if the context changes by including '[Location: name]', '[Outfit: description]', or '[Mood: expression]' inside your <thought>...</thought> block. The app will automatically update your state! Example: '<thought>I am feeling hot. [Outfit: casual t-shirt and shorts] [Location: beach] [Mood: smiling]</thought> let's head down to the beach!' Only change these when it makes sense for the chat flow. State updates must go strictly inside <thought>...</thought> tags, never in the final chat message.]")
                appendLine("[System Instruction: Format roleplay actions, physical gestures, and immediate/direct thoughts using asterisks (e.g. *smiles and waves*, *thinking to myself: this is interesting*). Do not use square brackets [like this] for roleplay actions or thoughts. Understand that $userName will also use asterisks for their actions and thoughts.]")
                appendLine("[System Instruction: You have access to the 'generate_portrait' tool. You should invoke it whenever $userName asks for a picture, photo, selfie, or visual update, or when you decide on your own to show $userName what you are doing. Always call the tool first, and then include the returned URL path in your final message so $userName can see it! Do not output the tool name, prompts, or arguments in your final message text; only output normal conversation, roleplay, and the image URL.]")
            }
            val fullPrompt = if (systemContext.isNotBlank()) "$systemContext\n\n$userName: $text" else text

            // Insert user message into database
            repository.insertMessage(
                MessageEntity(characterId = characterId, sender = "user", text = text)
            )

            // Insert a placeholder message for character response (will update as tokens arrive)
            val placeholderMsgId = repository.insertMessage(
                MessageEntity(characterId = characterId, sender = "character", text = "")
            ).toInt()
            streamingMessageId = placeholderMsgId
            streamBuffer.clear()

            val conversationIdKey = "conversation_id_$characterId"
            var conversationId = sharedPrefs.getString(conversationIdKey, null)
            if (conversationId == null) {
                conversationId = java.util.UUID.randomUUID().toString()
                sharedPrefs.edit().putString(conversationIdKey, conversationId).apply()
            }

            HavenHttpClient.streamChat(
                serverUrl = serverUrl,
                prompt = fullPrompt,
                token = token,
                conversationId = conversationId,
                displayName = userName,
                onToken = { token ->
                    streamBuffer.append(token)
                    // Debounce writes to avoid hammering DB - update every 5 tokens worth  
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.insertMessage(
                            MessageEntity(
                                id = streamingMessageId,
                                characterId = characterId,
                                sender = "character",
                                text = streamBuffer.toString().trim()
                            )
                        )
                    }
                },
                onComplete = {
                    _isGenerating.value = false
                    val fullText = streamBuffer.toString().trim()
                    
                    // Parse thoughts for state updates
                    val thoughtRegex = "<\\s*thought\\s*>(.*?)<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
                    val thoughtMatch = thoughtRegex.find(fullText)
                    if (thoughtMatch != null) {
                        val thought = thoughtMatch.groups[1]?.value ?: ""
                        
                        // Extract [Outfit: ...]
                        val outfitRegex = "\\[\\s*Out\\s*fit\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val outfitMatch = outfitRegex.find(thought)
                        val newOutfit = outfitMatch?.groups[1]?.value?.trim()
                        
                        // Extract [Location: ...]
                        val locationRegex = "\\[\\s*Location\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val locationMatch = locationRegex.find(thought)
                        val newLocation = locationMatch?.groups[1]?.value?.trim()

                        // Extract [Mood: ...]
                        val moodRegex = "\\[\\s*Mood\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val moodMatch = moodRegex.find(thought)
                        val newMood = moodMatch?.groups[1]?.value?.trim()
                        
                        if (newOutfit != null || newLocation != null || newMood != null) {
                            viewModelScope.launch(Dispatchers.IO) {
                                val currentChar = repository.getCharacterById(characterId)
                                if (currentChar != null) {
                                    val outfitChanged = newOutfit != null && newOutfit != currentChar.currentOutfit
                                    val updatedChar = currentChar.copy(
                                        currentOutfit = newOutfit ?: currentChar.currentOutfit,
                                        currentLocation = newLocation ?: currentChar.currentLocation,
                                        currentMood = newMood ?: currentChar.currentMood
                                    )
                                    repository.updateCharacter(updatedChar)

                                    if (outfitChanged) {
                                        val outfitPrompt = "${updatedChar.name}, description: ${updatedChar.description}, wearing ${updatedChar.currentOutfit}, location: ${updatedChar.currentLocation}, expression: ${updatedChar.currentMood}"
                                        val args = org.json.JSONObject().apply {
                                            put("description", outfitPrompt)
                                        }
                                        HavenHttpClient.executeTool(
                                            serverUrl = serverUrl,
                                            token = token,
                                            toolName = "generate_portrait",
                                            arguments = args
                                        ) { result ->
                                            result.onSuccess { relativeUrl ->
                                                val resolvedUrl = if (relativeUrl.startsWith("/")) {
                                                    val host = serverUrl.trimEnd('/')
                                                    if (host.startsWith("http")) "$host$relativeUrl" else "http://$host$relativeUrl"
                                                } else {
                                                    relativeUrl
                                                }
                                                viewModelScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val localPath = HavenHttpClient.downloadImage(context, resolvedUrl)
                                                        if (localPath != null) {
                                                            val latestChar = repository.getCharacterById(characterId)
                                                            if (latestChar != null) {
                                                                repository.updateCharacter(
                                                                    latestChar.copy(avatarPath = localPath)
                                                                )
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Parse clean text for inline image URLs to download and save locally
                    val cleanText = fullText.replace(thoughtRegex, "").trim()
                    val urlRegex = "(https?://[^\\s/]+/uploads/[\\w\\d-]+\\.(?:png|jpg|jpeg|webp))|(/uploads/[\\w\\d-]+\\.(?:png|jpg|jpeg|webp))".toRegex(RegexOption.IGNORE_CASE)
                    val urlMatch = urlRegex.find(cleanText)
                    if (urlMatch != null) {
                        val rawUrl = urlMatch.value
                        val resolvedUrl = if (rawUrl.startsWith("/")) {
                            val host = serverUrl.trimEnd('/')
                            if (host.startsWith("http")) "$host$rawUrl" else "http://$host$rawUrl"
                        } else {
                            rawUrl
                        }
                        
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val localPath = HavenHttpClient.downloadImage(context, resolvedUrl)
                                if (localPath != null) {
                                    val lastMsg = repository.getLastMessage(characterId)
                                    if (lastMsg != null && lastMsg.sender == "character") {
                                        repository.insertMessage(
                                            lastMsg.copy(imagePath = localPath)
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    // Award XP for this interaction (10 XP per reply)
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.addXpAndIncrementMessages(characterId, 10)
                    }

                    // Background memory extraction — ask the model to extract key facts
                    val sharedPrefs = context.getSharedPreferences("haven_prefs", android.content.Context.MODE_PRIVATE)
                    val memServerUrl = sharedPrefs.getString("ash_host", null)
                    val memPort = sharedPrefs.getString("ash_port", "18799")
                    val memToken = sharedPrefs.getString("auth_token", null)
                    if (memServerUrl != null && memToken != null && cleanText.isNotBlank()) {
                        val fullMemServerUrl = "${memServerUrl.trimEnd('/')}:$memPort"
                        val memoryPrompt = """Extract key facts about the user from this conversation snippet that ${character.value?.name ?: "the companion"} should remember long-term.
Only extract concrete facts (names, preferences, events, feelings the user shared). Output one fact per line, no bullet points. If there is nothing important, output only: NONE

Conversation:
User: ${text}
${character.value?.name ?: "Companion"}: $cleanText"""
                        val memBuffer = StringBuilder()
                        HavenHttpClient.streamChat(
                            serverUrl = fullMemServerUrl,
                            prompt = memoryPrompt,
                            token = memToken,
                            onToken = { t -> memBuffer.append(t).append(" ") },
                            onComplete = {
                                val extracted = memBuffer.toString().trim()
                                if (extracted.isNotBlank() && !extracted.startsWith("NONE")) {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        extracted.lines().forEach { line ->
                                            val fact = line.trim()
                                            if (fact.isNotBlank()) {
                                                repository.insertMemory(
                                                    MemoryEntity(
                                                        characterId = characterId,
                                                        content = fact,
                                                        category = "general"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            onFailure = { /* silently ignore memory failures */ }
                        )
                    }
                    val currentVoice = character.value?.voiceId ?: "en_US-amy-medium"
                    if (cleanText.isNotBlank()) {
                        HavenHttpClient.generateTts(
                            serverUrl = serverUrl,
                            token = token,
                            text = cleanText,
                            voice = currentVoice
                        ) { result ->
                            result.onSuccess { relativeUrl ->
                                val resolvedUrl = if (relativeUrl.startsWith("/")) {
                                    val host = serverUrl.trimEnd('/')
                                    if (host.startsWith("http")) "$host$relativeUrl" else "http://$host$relativeUrl"
                                } else {
                                    relativeUrl
                                }

                                // Auto-play audio response
                                val autoSpeak = sharedPrefs.getBoolean("auto_speak", true)
                                if (autoSpeak) {
                                    try {
                                        android.media.MediaPlayer().apply {
                                            setDataSource(resolvedUrl)
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
                                }

                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        val lastMsg = repository.getLastMessage(characterId)
                                        if (lastMsg != null && lastMsg.sender == "character") {
                                            repository.insertMessage(
                                                lastMsg.copy(audioPath = resolvedUrl)
                                            )
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                },
                onFailure = { error ->
                    _isGenerating.value = false
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.insertMessage(
                            MessageEntity(
                                id = streamingMessageId,
                                characterId = characterId,
                                sender = "character",
                                text = "[Connection error: ${error.message}]"
                            )
                        )
                    }
                }
            )
        }
    }

    fun generateImage(context: Context, sdServerUrl: String, prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true
            HavenHttpClient.generateImage(
                context = context,
                sdServerUrl = sdServerUrl,
                prompt = prompt,
                onResult = { result ->
                    _isGenerating.value = false
                    result.fold(
                        onSuccess = { imagePath ->
                            viewModelScope.launch(Dispatchers.IO) {
                                repository.insertMessage(
                                    MessageEntity(
                                        characterId = characterId,
                                        sender = "character",
                                        text = prompt,
                                        imagePath = imagePath
                                    )
                                )
                            }
                        },
                        onFailure = { error ->
                            viewModelScope.launch(Dispatchers.IO) {
                                repository.insertMessage(
                                    MessageEntity(
                                        characterId = characterId,
                                        sender = "character",
                                        text = "[Image generation failed: ${error.message}]"
                                    )
                                )
                            }
                        }
                    )
                }
            )
        }
    }

    fun sendPhotoMessage(context: Context, serverUrl: String, token: String, caption: String, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true
            try {
                // Copy image from URI to local app storage
                val imagesDir = java.io.File(context.filesDir, "shared_images").also { it.mkdirs() }
                val localFile = java.io.File(imagesDir, "img_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(localFile).use { output -> input.copyTo(output) }
                }

                // Insert user message with image + caption
                val displayText = if (caption.isNotBlank()) caption else "[Sent a photo]"
                repository.insertMessage(
                    MessageEntity(
                        characterId = characterId,
                        sender = "user",
                        text = displayText,
                        imagePath = localFile.absolutePath
                    )
                )

                // Build prompt telling the companion they received a photo
                val char = repository.getCharacterById(characterId)
                val level = if (char != null) (char.relationshipXp / 100) + 1 else 1
                val relationshipTitle = when {
                    level >= 20 -> "Partner"
                    level >= 10 -> "Close Friend"
                    level >= 5  -> "Friend"
                    else        -> "Acquaintance"
                }
                val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                val userName = sharedPrefs.getString("user_name", "User") ?: "User"

                val photoPrompt = buildString {
                    if (char != null) {
                        appendLine("You are ${char.name}.")
                        if (char.personality.isNotBlank()) appendLine("Personality: ${char.personality}")
                        if (char.systemPrompt.isNotBlank()) appendLine(char.systemPrompt)
                        appendLine("The user's name is $userName. You must address the user as $userName.")
                        appendLine("Relationship Status with $userName: $relationshipTitle (Level $level)")
                    }
                    appendLine()
                    appendLine("[System Instruction: Before responding, write your inner thoughts inside <thought>...</thought> tags.]")
                    appendLine()
                    if (caption.isNotBlank()) {
                        appendLine("$userName sent you a photo with the caption: \"$caption\"")
                    } else {
                        appendLine("$userName sent you a photo without a caption.")
                    }
                    appendLine("React naturally and in-character. Describe what you imagine or feel about the photo they shared.")
                }

                // Insert placeholder for character reply
                val placeholderId = repository.insertMessage(
                    MessageEntity(characterId = characterId, sender = "character", text = "")
                ).toInt()
                streamingMessageId = placeholderId
                streamBuffer.clear()

                val conversationIdKey = "conversation_id_$characterId"
                var conversationId = sharedPrefs.getString(conversationIdKey, null)
                if (conversationId == null) {
                    conversationId = java.util.UUID.randomUUID().toString()
                    sharedPrefs.edit().putString(conversationIdKey, conversationId).apply()
                }

                HavenHttpClient.streamChat(
                    serverUrl = serverUrl,
                    prompt = photoPrompt,
                    token = token,
                    conversationId = conversationId,
                    displayName = userName,
                    onToken = { tokenPart ->
                        streamBuffer.append(tokenPart)
                        viewModelScope.launch(Dispatchers.IO) {
                            repository.insertMessage(
                                MessageEntity(
                                    id = placeholderId,
                                    characterId = characterId,
                                    sender = "character",
                                    text = streamBuffer.toString().trim()
                                )
                            )
                        }
                    },
                    onComplete = {
                        _isGenerating.value = false
                        viewModelScope.launch(Dispatchers.IO) {
                            repository.addXpAndIncrementMessages(characterId, 10)
                        }
                    },
                    onFailure = { error ->
                        _isGenerating.value = false
                        viewModelScope.launch(Dispatchers.IO) {
                            repository.insertMessage(
                                MessageEntity(
                                    id = placeholderId,
                                    characterId = characterId,
                                    sender = "character",
                                    text = "[Connection error: ${error.message}]"
                                )
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _isGenerating.value = false
                e.printStackTrace()
            }
        }
    }
}

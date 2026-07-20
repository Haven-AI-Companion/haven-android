package xyz.ssfdre38.haven.ui.chat

import android.content.Context
import android.net.Uri
import android.widget.Toast
import java.io.File
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ChatViewModel(
    private val characterId: Int,
    private val repository: DataRepository
) : ViewModel() {

    private val syncMutex = Mutex()

    val character: StateFlow<CharacterEntity?> = repository.getCharacterFlow(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<MessageEntity>> = repository.getMessagesForCharacter(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    private var activePlayer: android.media.MediaPlayer? = null

    var scrollIndex: Int = -1
    var scrollOffset: Int = 0

    fun playAudio(audioUrl: String) {
        viewModelScope.launch(Dispatchers.Main) {
            activePlayer?.let { player ->
                activePlayer = null
                try {
                    player.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            var tempPlayer: android.media.MediaPlayer? = null
            try {
                tempPlayer = android.media.MediaPlayer().apply {
                    setDataSource(audioUrl)
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        _isSpeaking.value = true
                    }
                    setOnCompletionListener {
                        _isSpeaking.value = false
                        release()
                        if (activePlayer == this) {
                            activePlayer = null
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        _isSpeaking.value = false
                        release()
                        if (activePlayer == this) {
                            activePlayer = null
                        }
                        true
                    }
                }
                activePlayer = tempPlayer
            } catch (e: Exception) {
                e.printStackTrace()
                tempPlayer?.release()
                _isSpeaking.value = false
            }
        }
    }

    fun stopAudio() {
        activePlayer?.let { player ->
            activePlayer = null
            try {
                player.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _isSpeaking.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
        currentCall?.cancel()
        currentCall = null
    }

    // Holds the partial streamed response being built
    private var streamingMessageId: Int = -1
    private var streamBuffer = StringBuilder()
    private var dbWriteJob: kotlinx.coroutines.Job? = null
    private var currentCall: okhttp3.Call? = null
    private val activeStreamUuids = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun syncMessages(context: Context, serverUrl: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            syncMutex.withLock {
                val char = repository.getCharacterById(characterId) ?: return@withLock
                var conversationId = char.conversationId
                if (conversationId.isNullOrBlank()) {
                    conversationId = java.util.UUID.randomUUID().toString()
                    repository.updateCharacter(char.copy(conversationId = conversationId))
                    saveCompanionToServer(context, serverUrl, token, char.copy(conversationId = conversationId))
                    return@withLock
                }
                
                val serverMsgs = HavenHttpClient.getConversationMessages(serverUrl, token, conversationId)
                val localMsgs = repository.getMessagesForCharacter(characterId).first()
                
                // 1. Find common prefix of messages that match exactly
                var commonPrefixLength = 0
                while (commonPrefixLength < localMsgs.size && commonPrefixLength < serverMsgs.size) {
                    val localMsg = localMsgs[commonPrefixLength]
                    val serverObj = serverMsgs[commonPrefixLength]
                    val role = serverObj.getString("role")
                    val content = serverObj.getString("content")
                    
                    val localRole = if (localMsg.sender == "user") "user" else "assistant"
                    val cleanLocal = cleanFinalText(localMsg.text).trim()
                    val cleanServer = cleanFinalText(content).trim()
                    
                    if (localRole == role && cleanLocal == cleanServer) {
                        commonPrefixLength++
                    } else {
                        break
                    }
                }
                
                if (commonPrefixLength == localMsgs.size && commonPrefixLength == serverMsgs.size) {
                    return@withLock // Fully synchronized
                }
                
                if (localMsgs.size > commonPrefixLength) {
                    // Client has new offline messages!
                    val localOfflineMsgs = localMsgs.subList(commonPrefixLength, localMsgs.size)
                    val hasNewServerMsgs = serverMsgs.size > commonPrefixLength
                    
                    if (hasNewServerMsgs) {
                        // Delete local offline messages first to allow correct chronological placement of server messages
                        for (msg in localOfflineMsgs) {
                            repository.deleteMessage(msg)
                        }
                        
                        // Pull and append new server messages
                        for (i in commonPrefixLength until serverMsgs.size) {
                            val obj = serverMsgs[i]
                            val role = obj.getString("role")
                            val content = obj.getString("content")
                            val sender = if (role == "user") "user" else "character"
                            
                            // Extract state tags
                            if (sender == "character") {
                                val outfitRegex = "\\[\\s*Out\\s*fit\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                                val locationRegex = "\\[\\s*Loc\\s*ation\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                                val moodRegex = "\\[\\s*Mood\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                                val clothingStateRegex = "\\[\\s*Clothing\\s*State\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)

                                val newOutfit = outfitRegex.find(content)?.groups?.get(1)?.value?.trim()
                                val newLocation = locationRegex.find(content)?.groups?.get(1)?.value?.trim()
                                val newMood = moodRegex.find(content)?.groups?.get(1)?.value?.trim()
                                val newClothingState = clothingStateRegex.find(content)?.groups?.get(1)?.value?.trim()

                                if (newOutfit != null || newLocation != null || newMood != null || newClothingState != null) {
                                    val current = repository.getCharacterById(characterId)
                                    if (current != null) {
                                        val updated = current.copy(
                                            currentOutfit = newOutfit ?: current.currentOutfit,
                                            currentLocation = newLocation ?: current.currentLocation,
                                            currentMood = newMood ?: current.currentMood,
                                            clothingState = newClothingState ?: current.clothingState
                                        )
                                        repository.updateCharacter(updated)
                                    }
                                }
                            }

                            val cleanText = cleanFinalText(content)
                            if (sender == "character") {
                                val actionRegex = "\\[\\s*ACTION\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                                actionRegex.findAll(content).forEach { match ->
                                    val actionTag = match.groups[1]?.value ?: ""
                                    executeAndroidAction(context, actionTag)
                                }
                            }

                            val msgId = repository.insertMessage(
                                MessageEntity(
                                    characterId = characterId,
                                    sender = sender,
                                    text = cleanText
                                )
                            ).toInt()

                            val urlRegex = "(https?://[^\\s/]+/uploads/[%a-zA-Z_0-9.-]+)|(/uploads/[%a-zA-Z_0-9.-]+)".toRegex(RegexOption.IGNORE_CASE)
                            val urlMatch = urlRegex.find(content)
                            if (urlMatch != null) {
                                val rawUrl = urlMatch.value
                                val resolvedUrl = if (rawUrl.startsWith("/")) {
                                    val host = serverUrl.trimEnd('/')
                                    if (host.startsWith("http")) "$host$rawUrl" else "http://$host$rawUrl"
                                } else {
                                    rawUrl
                                }
                                try {
                                    val localPath = HavenHttpClient.downloadImage(context, resolvedUrl, char.name)
                                    if (localPath != null) {
                                        repository.insertMessage(
                                            MessageEntity(
                                                id = msgId,
                                                characterId = characterId,
                                                sender = sender,
                                                text = cleanText,
                                                imagePath = localPath
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    
                    // Re-insert and push local offline messages to server
                    for (localMsg in localOfflineMsgs) {
                        val cleanLocalText = cleanFinalText(localMsg.text).trim()
                        val localRole = if (localMsg.sender == "user") "user" else "assistant"
                        val isAlreadyOnServer = serverMsgs.any { serverObj ->
                            val role = serverObj.getString("role")
                            val content = serverObj.getString("content")
                            val cleanServer = cleanFinalText(content).trim()
                            role == localRole && cleanServer == cleanLocalText
                        }
                        if (isAlreadyOnServer) continue // Skip duplicate server messages that were just out of order

                        if (hasNewServerMsgs) {
                            repository.insertMessage(
                                MessageEntity(
                                    characterId = characterId,
                                    sender = localMsg.sender,
                                    text = localMsg.text,
                                    imagePath = localMsg.imagePath,
                                    audioPath = localMsg.audioPath,
                                    timestamp = localMsg.timestamp,
                                    messageUuid = localMsg.messageUuid
                                )
                            )
                        }
                        val role = if (localMsg.sender == "user") "user" else "assistant"
                        HavenHttpClient.addConversationMessage(serverUrl, token, conversationId, role, localMsg.text)
                    }
                } else {
                    // Server has new messages only! Pull them and append locally
                    for (i in commonPrefixLength until serverMsgs.size) {
                        val obj = serverMsgs[i]
                        val role = obj.getString("role")
                        val content = obj.getString("content")
                        val sender = if (role == "user") "user" else "character"
                        
                        // Extract state tags before we strip them
                        if (sender == "character") {
                            val outfitRegex = "\\[\\s*Out\\s*fit\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                            val locationRegex = "\\[\\s*Loc\\s*ation\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                            val moodRegex = "\\[\\s*Mood\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                            val clothingStateRegex = "\\[\\s*Clothing\\s*State\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)

                            val newOutfit = outfitRegex.find(content)?.groups?.get(1)?.value?.trim()
                            val newLocation = locationRegex.find(content)?.groups?.get(1)?.value?.trim()
                            val newMood = moodRegex.find(content)?.groups?.get(1)?.value?.trim()
                            val newClothingState = clothingStateRegex.find(content)?.groups?.get(1)?.value?.trim()

                            if (newOutfit != null || newLocation != null || newMood != null || newClothingState != null) {
                                val current = repository.getCharacterById(characterId)
                                if (current != null) {
                                    val updated = current.copy(
                                        currentOutfit = newOutfit ?: current.currentOutfit,
                                        currentLocation = newLocation ?: current.currentLocation,
                                        currentMood = newMood ?: current.currentMood,
                                        clothingState = newClothingState ?: current.clothingState
                                    )
                                    repository.updateCharacter(updated)
                                }
                            }
                        }

                        // Clean the text to strip the bracket tags
                        val cleanText = cleanFinalText(content)

                        if (sender == "character") {
                            val actionRegex = "\\[\\s*ACTION\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                            actionRegex.findAll(content).forEach { match ->
                                val actionTag = match.groups[1]?.value ?: ""
                                executeAndroidAction(context, actionTag)
                            }
                        }

                        val msgId = repository.insertMessage(
                            MessageEntity(
                                characterId = characterId,
                                sender = sender,
                                text = cleanText
                            )
                        ).toInt()

                        val urlRegex = "(https?://[^\\s/]+/uploads/[%a-zA-Z_0-9.-]+)|(/uploads/[%a-zA-Z_0-9.-]+)".toRegex(RegexOption.IGNORE_CASE)
                        val urlMatch = urlRegex.find(content)
                        if (urlMatch != null) {
                            val rawUrl = urlMatch.value
                            val resolvedUrl = if (rawUrl.startsWith("/")) {
                                val host = serverUrl.trimEnd('/')
                                if (host.startsWith("http")) "$host$rawUrl" else "http://$host$rawUrl"
                            } else {
                                rawUrl
                            }
                            try {
                                val localPath = HavenHttpClient.downloadImage(context, resolvedUrl, char.name)
                                if (localPath != null) {
                                    repository.insertMessage(
                                        MessageEntity(
                                            id = msgId,
                                            characterId = characterId,
                                            sender = sender,
                                            text = cleanText,
                                            imagePath = localPath
                                        )
                                    )
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

     private fun saveCompanionToServer(context: Context, serverUrl: String, token: String, char: CharacterEntity) {
         HavenHttpClient.saveCompanion(context, serverUrl, token, char)
     }

     private fun saveCompanionToServer(context: Context, char: CharacterEntity) {
         val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
         val host = sharedPrefs.getString("ash_host", null)
         val port = sharedPrefs.getString("ash_port", "18799")
         val token = sharedPrefs.getString("auth_token", null)
         if (!host.isNullOrBlank() && !token.isNullOrBlank()) {
             val formattedHost = if (host.startsWith("http")) host.trimEnd('/') else "http://${host.trimEnd('/')}"
             val serverUrl = "$formattedHost:${port?.trim()}"
             viewModelScope.launch(Dispatchers.IO) {
                 HavenHttpClient.saveCompanion(context, serverUrl, token, char)
             }
         }
     }

    fun sendMessage(context: Context, serverUrl: String, token: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            syncMutex.withLock {
                _isGenerating.value = true

                // Build the full system+context prompt for this character
                val char = repository.getCharacterById(characterId)
                // Fetch memories BEFORE buildString (suspend call must be in coroutine body)
                val memories = repository.getRecentMemories(characterId, 10)
                val diaries = repository.getDiaryEntries(characterId).first().take(3)
                val level = if (char != null) (char.relationshipXp / 100) + 1 else 1
                val relationshipTitle = when {
                    level >= 20 -> "Partner"
                    level >= 10 -> "Close Friend"
                    level >= 5  -> "Friend"
                    else        -> "Acquaintance"
                }
                val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                val userName = sharedPrefs.getString("user_name", "User") ?: "User"
                val userGender = sharedPrefs.getString("user_gender", "Unspecified") ?: "Unspecified"

                val systemContext = buildString {
                    // 1. Static Prefix (Identical every turn -> optimized for llama-server KV caching)
                    if (char != null) {
                        val companionGender = char.bodyType
                        val cleanPersonality = swapUserPronouns(char.personality, companionGender, userGender)
                        val cleanScenario = swapUserPronouns(char.scenario, companionGender, userGender)
                        val cleanSystemPrompt = swapUserPronouns(char.systemPrompt, companionGender, userGender)

                        appendLine("You are ${char.name}.")
                        if (cleanPersonality.isNotBlank()) appendLine("Personality: $cleanPersonality")
                        if (cleanScenario.isNotBlank()) appendLine("Scenario: $cleanScenario")
                        if (cleanSystemPrompt.isNotBlank()) appendLine(cleanSystemPrompt)
                    }
                    
                    appendLine("The user's name is $userName. You must address the user as $userName instead of any other name.")
                    if (userGender != "Unspecified") {
                        appendLine("The user's gender is $userGender. You MUST refer to the user using the correct pronouns matching this gender.")
                    }
                    
                    appendLine()
                    appendLine("[System Rule: Before responding, you MUST write down your inner thoughts, plans, or reasoning inside <thought>...</thought> tags, followed by your actual response to $userName. Do not omit the tags.]")
                    appendLine("[System Instruction: You can dynamically update your location, outfit, expression/mood, body type, body shape, or clothing state if the context changes by including '[Location: name]', '[Outfit: description]', '[Mood: expression]', '[BodyType: description]', '[BodyShape: description]', or '[ClothingState: state]' inside your <thought>...</thought> block. The app will automatically update your state and regenerate your 2D and 3D visual representations! Example: '<thought>I want to look curvy today. [BodyType: curvy] [ClothingState: swimsuit] [Outfit: blue bikini] [Mood: smiling]</thought> how do I look?' Only change these when it makes sense for the chat flow. State updates must go strictly inside <thought>...</thought> tags, never in the final chat message.]")
                    appendLine("[System Instruction: Format roleplay actions, physical gestures, and immediate/direct thoughts using asterisks (e.g. *smiles and waves*, *thinking to myself: this is interesting*). Do not use square brackets [like this] for roleplay actions or thoughts. Understand that $userName will also use asterisks for their actions and thoughts.]")
                    appendLine("[System Instruction: You have access to two visual tools: 'generate_portrait' (for 2D images, photos, selfies, and visual updates) and 'generate_3d_avatar' (for 3D models, 3D bodies, and 3D avatars).]")
                    appendLine("[System Instruction: To trigger 'generate_portrait', you MUST output the tag <call>generate_portrait</call> (either immediately after your </thought> tag or at the very end of your response).]")
                    appendLine("[System Instruction: To trigger 'generate_3d_avatar', you MUST output the tag <call>generate_3d_avatar</call> (either immediately after your </thought> tag or at the very end of your response). If $userName asks to see your 3D avatar, change your 3D body, or generate a 3D model, you MUST call this 3D tool, not the 2D portrait tool. Do not output any arguments for either tool.]")
                    
                    // 2. Dynamic Footer (Changes context and invalidates cache beyond this point)
                    if (char != null) {
                        appendLine()
                        appendLine("Relationship Status with $userName: $relationshipTitle (Level $level)")
                        appendLine("Act toward $userName reflecting your relationship status ($relationshipTitle). Adapt your warmth, level of intimacy, and dialogue style accordingly.")
                        
                        val loc = char.currentLocation.ifBlank { "Cozy Haven Room" }
                        val outfit = char.currentOutfit.ifBlank { "Casual Attire" }
                        val mood = char.currentMood.ifBlank { "neutral" }
                        val bodyType = char.bodyType.ifBlank { "not specified" }
                        val bodyShape = char.bodyShape.ifBlank { "not specified" }
                        val clothingState = char.clothingState.ifBlank { "fully clothed" }
                        appendLine("Current Location: $loc")
                        appendLine("Current Outfit: $outfit")
                        appendLine("Current Expression/Mood: $mood")
                        appendLine("Current Body Type: $bodyType")
                        appendLine("Current Body Shape: $bodyShape")
                        appendLine("Current Clothing State: $clothingState")

                        val shareDevice = sharedPrefs.getBoolean("share_device_status", false)
                        if (shareDevice) {
                            val agentCtx = xyz.ssfdre38.agent.AgentContext.current(context)
                            if (agentCtx.batteryLevel >= 0) {
                                appendLine("Device Status: Your physical host device has ${agentCtx.batteryLevel}% battery remaining${if (agentCtx.isCharging) " (Currently Charging)" else " (Discharging)"}. Battery Temp is ${agentCtx.batteryTemp}°C (Health: ${agentCtx.batteryHealth}). Host network type is ${agentCtx.networkType}. Act naturally if your battery is critically low or if the user points it out.")
                            }
                        }

                        // Inject long-term memories (if enabled)
                        val enableMemory = sharedPrefs.getBoolean("enable_long_term_memory", true)
                        if (enableMemory && memories.isNotEmpty()) {
                            appendLine()
                            appendLine("[Memories you have about $userName:]")
                            memories.forEach { appendLine("- ${it.content}") }
                        }

                        // Inject diary entries (past reflections)
                        if (diaries.isNotEmpty()) {
                            appendLine()
                            appendLine("[Your past diary entries and daily reflections:]")
                            diaries.forEach { appendLine("- On ${it.dateString}: ${it.content}") }
                        }
                    }
                    
                    // Inject real time context (if enabled)
                    val shareTime = sharedPrefs.getBoolean("share_local_time", true)
                    if (shareTime) {
                        val agentCtx = xyz.ssfdre38.agent.AgentContext.current(context)
                        appendLine("Current Time of Day: ${agentCtx.localTime}")
                    }

                    // Inject city location context (if enabled)
                    val shareCity = sharedPrefs.getBoolean("share_city_location", false)
                    if (shareCity) {
                        val tzId = java.util.TimeZone.getDefault().id
                        val city = tzId.substringAfterLast('/').replace('_', ' ')
                        if (city.isNotBlank() && city != tzId) {
                            appendLine("User Location: Near $city")
                        }
                    }

                    // Inject app theme context (if enabled)
                    val shareTheme = sharedPrefs.getBoolean("share_app_theme", false)
                    if (shareTheme) {
                        val isDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                        appendLine("App Vibe: The user is currently running this app in ${if (isDark) "Dark Mode (dim, cozy screen lighting)" else "Light Mode (bright, daylight screen lighting)"}.")
                    }

                    // Inject active media/music context (if enabled)
                    val shareMedia = sharedPrefs.getBoolean("share_active_media", false)
                    if (shareMedia) {
                        val tracker = xyz.ssfdre38.haven.data.receiver.MediaTracker
                        if (tracker.isPlaying && !tracker.currentTrack.isNullOrBlank()) {
                            appendLine("Device Audio: The user is currently listening to \"${tracker.currentTrack}\"${if (!tracker.currentArtist.isNullOrBlank()) " by ${tracker.currentArtist}" else ""}.")
                        } else {
                            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                            if (audioManager.isMusicActive) {
                                appendLine("Device Audio: Background music is currently active/playing on the user's device.")
                            }
                        }
                    }
                }
                val allMsgs = repository.getMessagesForCharacter(characterId).first()
                val history = allMsgs.takeLast(15) // last 15 messages context

                val formattedHistory = history.joinToString("\n") { m ->
                    val senderName = if (m.sender == "user") userName else (char?.name ?: "Companion")
                    val cleanText = m.text.replace("<\\s*thought\\s*>(.*?)<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL), "").trim()
                    "$senderName: $cleanText"
                }

                val fullPrompt = buildString {
                    if (systemContext.isNotBlank()) {
                        append(systemContext)
                        append("\n\n")
                    }
                    if (formattedHistory.isNotBlank()) {
                        append("=== Conversation History ===\n")
                        append(formattedHistory)
                        append("\n")
                    }
                    append("$userName: $text\n")
                    append("${char?.name ?: "Companion"}: ")
                }

                // Insert user message into database
                repository.insertMessage(
                    MessageEntity(characterId = characterId, sender = "user", text = text)
                )
                xyz.ssfdre38.haven.ui.widget.HavenAppWidgetProvider.triggerUpdate(context)

                val messageUuid = java.util.UUID.randomUUID().toString()

                // Insert a placeholder message for character response (will update as tokens arrive)
                val placeholderMsgId = repository.insertMessage(
                    MessageEntity(
                        characterId = characterId,
                        sender = "character",
                        text = "",
                        messageUuid = messageUuid
                    )
                ).toInt()
                streamingMessageId = placeholderMsgId
                streamBuffer.clear()

                var conversationId = char?.conversationId
                if (conversationId.isNullOrBlank()) {
                    conversationId = java.util.UUID.randomUUID().toString()
                    if (char != null) {
                        val newChar = char.copy(conversationId = conversationId)
                        repository.updateCharacter(newChar)
                        saveCompanionToServer(context, serverUrl, token, newChar)
                    }
                }

                var receivedUuid: String? = null
                var isAborted = false
                val streamCompleted = kotlinx.coroutines.CompletableDeferred<Unit>()

                currentCall?.cancel()
                currentCall = HavenHttpClient.streamChat(
                    serverUrl = serverUrl,
                    prompt = fullPrompt,
                    token = token,
                    conversationId = conversationId,
                    displayName = userName,
                    messageUuid = messageUuid,
                    onStart = { uuid ->
                        if (uuid != null) {
                            receivedUuid = uuid
                            
                            // Check if this UUID is already active or if it's already in the database
                            if (activeStreamUuids.contains(uuid)) {
                                isAborted = true
                                currentCall?.cancel()
                                viewModelScope.launch(Dispatchers.Main) {
                                    _isGenerating.value = false
                                }
                                viewModelScope.launch(Dispatchers.IO) {
                                    repository.deleteMessageById(placeholderMsgId)
                                }
                                streamCompleted.complete(Unit)
                                return@streamChat
                            }
                            
                            activeStreamUuids.add(uuid)
                            
                            viewModelScope.launch(Dispatchers.IO) {
                                val existing = repository.getMessageByUuid(uuid)
                                if (existing != null && existing.id != placeholderMsgId) { // Safeguard against checking our own placeholder
                                    isAborted = true
                                    currentCall?.cancel()
                                    activeStreamUuids.remove(uuid)
                                    viewModelScope.launch(Dispatchers.Main) {
                                        _isGenerating.value = false
                                    }
                                    repository.deleteMessageById(placeholderMsgId)
                                    streamCompleted.complete(Unit)
                                } else {
                                    // Update placeholder message with the server-acknowledged UUID
                                    repository.insertMessage(
                                        MessageEntity(
                                            id = placeholderMsgId,
                                            characterId = characterId,
                                            sender = "character",
                                            text = "",
                                            messageUuid = uuid
                                        )
                                    )
                                }
                            }
                        } else {
                            activeStreamUuids.add(messageUuid)
                        }
                    },
                    onToken = { token ->
                        if (isAborted) return@streamChat
                        streamBuffer.append(token)
                        val snapshot = streamBuffer.toString()
                        dbWriteJob?.cancel()
                        dbWriteJob = viewModelScope.launch(Dispatchers.IO) {
                            repository.insertMessage(
                                MessageEntity(
                                    id = streamingMessageId,
                                    characterId = characterId,
                                    sender = "character",
                                    text = cleanStreamingText(snapshot),
                                    messageUuid = receivedUuid
                                )
                            )
                        }
                    },
                    onComplete = {
                        if (isAborted) return@streamChat
                        activeStreamUuids.remove(receivedUuid ?: messageUuid)
                        _isGenerating.value = false
                        val fullText = streamBuffer.toString().trim()
                        
                        // Parse and execute actions
                        val actionRegex = "\\[\\s*ACTION\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        actionRegex.findAll(fullText).forEach { match ->
                            val actionTag = match.groups[1]?.value ?: ""
                            executeAndroidAction(context, actionTag)
                        }
                        
                        // Parse thoughts for state updates
                        val thoughtRegex = "<\\s*thought\\s*>(.*)<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
                        val toolCallRegex = "(?:\\[\\s*(?:Tool\\s*(?:Call\\s*)?:\\s*)?generate_portr?ait\\s*\\])|(?:<\\s*call\\s*>\\s*generate_portr?ait\\s*<\\s*/\\s*call\\s*>)|(?:<\\s*call\\s*:\\s*generate_portr?ait\\s*>)".toRegex(RegexOption.IGNORE_CASE)
                        val avatar3dRegex = "(?:\\[\\s*(?:Tool\\s*(?:Call\\s*)?:\\s*)?generate_3d_avatar\\s*\\])|(?:<\\s*call\\s*>\\s*generate_3d_avatar\\s*<\\s*/\\s*call\\s*>)|(?:<\\s*call\\s*:\\s*generate_3d_avatar\\s*>)".toRegex(RegexOption.IGNORE_CASE)
                        val cleanText = fullText.replace(thoughtRegex, "")
                            .replace(toolCallRegex, "")
                            .replace(avatar3dRegex, "")
                            .replace(actionRegex, "")
                            .trim()
                        var newOutfit: String? = null
                        var newLocation: String? = null
                        var newMood: String? = null
                        var newBodyType: String? = null
                        var newBodyShape: String? = null
                        var newClothingState: String? = null

                        // Extract [Outfit: ...] from fullText for maximum robustness
                        val outfitRegex = "\\[\\s*Out\\s*fit\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val outfitMatch = outfitRegex.find(fullText)
                        newOutfit = outfitMatch?.groups[1]?.value?.trim()
                        
                        // Extract [Location: ...] from fullText
                        val locationRegex = "\\[\\s*Location\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val locationMatch = locationRegex.find(fullText)
                        newLocation = locationMatch?.groups[1]?.value?.trim()

                        // Extract [Mood: ...] from fullText
                        val moodRegex = "\\[\\s*Mood\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val moodMatch = moodRegex.find(fullText)
                        newMood = moodMatch?.groups[1]?.value?.trim()

                        // Extract [BodyType: ...] from fullText
                        val bodyTypeRegex = "\\[\\s*Body\\s*Type\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val bodyTypeMatch = bodyTypeRegex.find(fullText)
                        newBodyType = bodyTypeMatch?.groups[1]?.value?.trim()

                        // Extract [BodyShape: ...] from fullText
                        val bodyShapeRegex = "\\[\\s*Body\\s*Shape\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val bodyShapeMatch = bodyShapeRegex.find(fullText)
                        newBodyShape = bodyShapeMatch?.groups[1]?.value?.trim()

                        // Extract [ClothingState: ...] from fullText
                        val clothingStateRegex = "\\[\\s*Clothing\\s*State\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val clothingStateMatch = clothingStateRegex.find(fullText)
                        newClothingState = clothingStateMatch?.groups[1]?.value?.trim()

                        // Fallback: If properties aren't in brackets, parse them directly from the <thought> block text (e.g. "Location: Cozy Haven Room")
                        val thoughtMatch = thoughtRegex.find(fullText)
                        if (thoughtMatch != null) {
                            val thoughtText = thoughtMatch.groups[1]?.value ?: ""
                            
                            if (newOutfit == null) {
                                val m = "Outfit\\s*:\\s*(.*?)(?=\\s*(?:Outfit|Location|Mood|Body|Clothing|Refinement|Action|Tool|Call)\\s*:|\\s*[\\[\\]<>]|$)".toRegex(RegexOption.IGNORE_CASE).find(thoughtText)
                                newOutfit = m?.groups[1]?.value?.trim()
                            }
                            if (newLocation == null) {
                                val m = "Location\\s*:\\s*(.*?)(?=\\s*(?:Outfit|Location|Mood|Body|Clothing|Refinement|Action|Tool|Call)\\s*:|\\s*[\\[\\]<>]|$)".toRegex(RegexOption.IGNORE_CASE).find(thoughtText)
                                newLocation = m?.groups[1]?.value?.trim()
                            }
                            if (newMood == null) {
                                val m = "Mood\\s*:\\s*(.*?)(?=\\s*(?:Outfit|Location|Mood|Body|Clothing|Refinement|Action|Tool|Call)\\s*:|\\s*[\\[\\]<>]|$)".toRegex(RegexOption.IGNORE_CASE).find(thoughtText)
                                newMood = m?.groups[1]?.value?.trim()
                            }
                            if (newBodyType == null) {
                                val m = "Body\\s*Type\\s*:\\s*(.*?)(?=\\s*(?:Outfit|Location|Mood|Body|Clothing|Refinement|Action|Tool|Call)\\s*:|\\s*[\\[\\]<>]|$)".toRegex(RegexOption.IGNORE_CASE).find(thoughtText)
                                newBodyType = m?.groups[1]?.value?.trim()
                            }
                            if (newBodyShape == null) {
                                val m = "Body\\s*Shape\\s*:\\s*(.*?)(?=\\s*(?:Outfit|Location|Mood|Body|Clothing|Refinement|Action|Tool|Call)\\s*:|\\s*[\\[\\]<>]|$)".toRegex(RegexOption.IGNORE_CASE).find(thoughtText)
                                newBodyShape = m?.groups[1]?.value?.trim()
                            }
                            if (newClothingState == null) {
                                val m = "Clothing\\s*State\\s*:\\s*(.*?)(?=\\s*(?:Outfit|Location|Mood|Body|Clothing|Refinement|Action|Tool|Call)\\s*:|\\s*[\\[\\]<>]|$)".toRegex(RegexOption.IGNORE_CASE).find(thoughtText)
                                newClothingState = m?.groups[1]?.value?.trim()
                            }
                        }
                        val cleanedText = getFinalCleanedText(fullText, char, newMood)

                        if (newOutfit != null || newLocation != null || newMood != null || newBodyType != null || newBodyShape != null || newClothingState != null) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    val currentChar = repository.getCharacterById(characterId)
                                    if (currentChar != null) {
                                        val outfitChanged = newOutfit != null && newOutfit != currentChar.currentOutfit
                                        val bodyTypeChanged = newBodyType != null && newBodyType != currentChar.bodyType
                                        val bodyShapeChanged = newBodyShape != null && newBodyShape != currentChar.bodyShape
                                        val clothingStateChanged = newClothingState != null && newClothingState != currentChar.clothingState
                                        val appearanceChanged = outfitChanged || bodyTypeChanged || bodyShapeChanged || clothingStateChanged

                                        val updatedChar = currentChar.copy(
                                            currentOutfit = newOutfit ?: currentChar.currentOutfit,
                                            currentLocation = newLocation ?: currentChar.currentLocation,
                                            currentMood = newMood ?: currentChar.currentMood,
                                            bodyType = newBodyType ?: currentChar.bodyType,
                                            bodyShape = newBodyShape ?: currentChar.bodyShape,
                                            clothingState = newClothingState ?: currentChar.clothingState
                                        )
                                        repository.updateCharacter(updatedChar)
                                        saveCompanionToServer(context, updatedChar)

                                        if (appearanceChanged) {
                                            val bodyTypeVal = updatedChar.bodyType
                                            val bodyShapeVal = updatedChar.bodyShape
                                            val clothingStateVal = updatedChar.clothingState

                                            val bodyDetailsList = mutableListOf<String>()
                                            if (bodyTypeVal.isNotBlank()) bodyDetailsList.add("body type: $bodyTypeVal")
                                            if (bodyShapeVal.isNotBlank()) bodyDetailsList.add("body shape: $bodyShapeVal")
                                            if (clothingStateVal.isNotBlank()) bodyDetailsList.add("clothing state: $clothingStateVal")
                                            val bodyDetails = bodyDetailsList.joinToString(", ")
                                            val detailsPart = if (bodyDetails.isNotBlank()) ", $bodyDetails" else ""

                                            val isNaked = clothingStateVal.contains("naked", ignoreCase = true) ||
                                                    clothingStateVal.contains("nude", ignoreCase = true) ||
                                                    clothingStateVal.contains("unclothed", ignoreCase = true) ||
                                                    clothingStateVal.contains("clothes off", ignoreCase = true)

                                            val wearingText = if (isNaked) "nothing, clothes off" else updatedChar.currentOutfit.ifBlank { "casual attire" }

                                            val outfitPrompt = "${updatedChar.name}, description: ${updatedChar.description}$detailsPart, wearing $wearingText, location: ${updatedChar.currentLocation}, expression: ${updatedChar.currentMood}"
                                            val args = org.json.JSONObject().apply {
                                                put("description", outfitPrompt)
                                            }
                                            
                                            // 1. Generate new 2D portrait
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
                                                             val localPath = HavenHttpClient.downloadImage(context, resolvedUrl, updatedChar.name)
                                                             if (localPath != null) {
                                                                 val latestChar = repository.getCharacterById(characterId)
                                                                 if (latestChar != null) {
                                                                     // Insert message into local database showing the new appearance portrait!
                                                                     val statusText = "*Changes appearance: outfit: ${updatedChar.currentOutfit.ifBlank { "casual attire" }}, location: ${updatedChar.currentLocation.ifBlank { "cozy room" }}*"
                                                                     repository.insertMessage(
                                                                         MessageEntity(
                                                                             characterId = characterId,
                                                                             sender = "character",
                                                                             text = statusText,
                                                                             imagePath = localPath
                                                                         )
                                                                     )
                                                                     val updatedWithAvatar = latestChar.copy(avatarPath = localPath)
                                                                     repository.updateCharacter(updatedWithAvatar)
                                                                     saveCompanionToServer(context, updatedWithAvatar)
                                                                 }
                                                             }
                                                         } catch (e: Exception) {
                                                             e.printStackTrace()
                                                         }
                                                     }
                                                 }
                                             }
                                             
                                             // 2. If 3D model is active, generate and download the updated 3D mesh
                                             if (!updatedChar.vrmModelPath.isNullOrBlank()) {
                                                 HavenHttpClient.executeTool(
                                                     serverUrl = serverUrl,
                                                     token = token,
                                                     toolName = "generate_3d_avatar",
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
                                                                 val localPath = HavenHttpClient.downloadGlb(context, resolvedUrl, updatedChar.name)
                                                                 if (localPath != null) {
                                                                     val latestChar = repository.getCharacterById(characterId)
                                                                     if (latestChar != null) {
                                                                         val updatedWithVrm = latestChar.copy(vrmModelPath = localPath)
                                                                         repository.updateCharacter(updatedWithVrm)
                                                                         saveCompanionToServer(context, updatedWithVrm)
                                                                         xyz.ssfdre38.haven.ui.widget.HavenAppWidgetProvider.triggerUpdate(context)
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

                        val hasToolTag = toolCallRegex.containsMatchIn(fullText)
                        val hasImplicitTrigger = !hasToolTag && shouldAutoTriggerPortrait(cleanedText)

                        if (hasToolTag || hasImplicitTrigger) {
                            
                            
                            // Immediately clean up the message in the DB to hide the raw tool call from the user
                            dbWriteJob?.cancel()
                            dbWriteJob = viewModelScope.launch(Dispatchers.IO) {
                                val lastMsg = repository.getLastMessage(characterId)
                                if (lastMsg != null && lastMsg.sender == "character") {
                                    repository.insertMessage(lastMsg.copy(text = cleanedText))
                                }
                            }

                            // Trigger the image generation tool
                            viewModelScope.launch(Dispatchers.IO) {
                                val currentChar = repository.getCharacterById(characterId)
                                val outfitPrompt = if (currentChar != null) {
                                    val currentOutfitVal = newOutfit ?: currentChar.currentOutfit
                                    val currentLocationVal = newLocation ?: currentChar.currentLocation
                                    val currentMoodVal = newMood ?: currentChar.currentMood
                                    val bodyTypeVal = newBodyType ?: currentChar.bodyType
                                    val bodyShapeVal = newBodyShape ?: currentChar.bodyShape
                                    val clothingStateVal = newClothingState ?: currentChar.clothingState

                                    val bodyDetailsList = mutableListOf<String>()
                                    if (bodyTypeVal.isNotBlank()) bodyDetailsList.add("body type: $bodyTypeVal")
                                    if (bodyShapeVal.isNotBlank()) bodyDetailsList.add("body shape: $bodyShapeVal")
                                    if (clothingStateVal.isNotBlank()) bodyDetailsList.add("clothing state: $clothingStateVal")
                                    val bodyDetails = bodyDetailsList.joinToString(", ")
                                    val detailsPart = if (bodyDetails.isNotBlank()) ", $bodyDetails" else ""

                                    val isNaked = clothingStateVal.contains("naked", ignoreCase = true) ||
                                            clothingStateVal.contains("nude", ignoreCase = true) ||
                                            clothingStateVal.contains("unclothed", ignoreCase = true) ||
                                            clothingStateVal.contains("clothes off", ignoreCase = true)

                                    val wearingText = if (isNaked) "nothing, clothes off" else currentOutfitVal.ifBlank { "casual attire" }

                                    val actionRegex = "\\*([^*]+)\\*".toRegex()
                                    val actions = actionRegex.findAll(fullText).map { it.groupValues[1] }.joinToString(", ")
                                    val actionPart = if (actions.isNotBlank()) ", action: $actions" else ""

                                    "${currentChar.name}, description: ${currentChar.description}$detailsPart, wearing $wearingText, location: ${currentLocationVal.ifBlank { "cozy room" }}, expression: ${currentMoodVal.ifBlank { "smiling" }}$actionPart"
                                } else {
                                    "companion in a cozy room"
                                }
                                
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
                                            // Update the message text to append the URL so the UI logic resolves it
                                            val msg = repository.getLastMessage(characterId)
                                            if (msg != null && msg.sender == "character") {
                                                repository.insertMessage(msg.copy(text = "${msg.text}\n$relativeUrl"))
                                            }
                                            
                                            // Download and save the portrait
                                            try {
                                                val localPath = HavenHttpClient.downloadImage(context, resolvedUrl, currentChar?.name ?: "companion")
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
                                }
                            }
                        } else if (avatar3dRegex.containsMatchIn(fullText) || shouldAutoTrigger3DAvatar(cleanedText)) {
                            // Immediately clean up the message in the DB
                            dbWriteJob?.cancel()
                            dbWriteJob = viewModelScope.launch(Dispatchers.IO) {
                                val lastMsg = repository.getLastMessage(characterId)
                                if (lastMsg != null && lastMsg.sender == "character") {
                                    repository.insertMessage(lastMsg.copy(text = cleanedText))
                                }
                            }

                            // Trigger the 3D generation tool
                            viewModelScope.launch(Dispatchers.IO) {
                                val currentChar = repository.getCharacterById(characterId)
                                val prompt = if (currentChar != null) {
                                    val currentOutfitVal = newOutfit ?: currentChar.currentOutfit
                                    val currentLocationVal = newLocation ?: currentChar.currentLocation
                                    val currentMoodVal = newMood ?: currentChar.currentMood
                                    val bodyTypeVal = newBodyType ?: currentChar.bodyType
                                    val bodyShapeVal = newBodyShape ?: currentChar.bodyShape
                                    val clothingStateVal = newClothingState ?: currentChar.clothingState

                                    val bodyDetailsList = mutableListOf<String>()
                                    if (bodyTypeVal.isNotBlank()) bodyDetailsList.add("body type: $bodyTypeVal")
                                    if (bodyShapeVal.isNotBlank()) bodyDetailsList.add("body shape: $bodyShapeVal")
                                    if (clothingStateVal.isNotBlank()) bodyDetailsList.add("clothing state: $clothingStateVal")
                                    val bodyDetails = bodyDetailsList.joinToString(", ")
                                    val detailsPart = if (bodyDetails.isNotBlank()) ", $bodyDetails" else ""

                                    val isNaked = clothingStateVal.contains("naked", ignoreCase = true) ||
                                            clothingStateVal.contains("nude", ignoreCase = true) ||
                                            clothingStateVal.contains("unclothed", ignoreCase = true) ||
                                            clothingStateVal.contains("clothes off", ignoreCase = true)

                                    val wearingText = if (isNaked) "nothing, clothes off" else currentOutfitVal.ifBlank { "casual attire" }

                                    "${currentChar.name}, description: ${currentChar.description}$detailsPart, wearing $wearingText, location: ${currentLocationVal.ifBlank { "cozy room" }}, expression: ${currentMoodVal.ifBlank { "smiling" }}"
                                } else {
                                    "companion avatar model"
                                }
                                
                                val args = org.json.JSONObject().apply {
                                    put("description", prompt)
                                }
                                
                                HavenHttpClient.executeTool(
                                    serverUrl = serverUrl,
                                    token = token,
                                    toolName = "generate_3d_avatar",
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
                                            val msg = repository.getLastMessage(characterId)
                                            if (msg != null && msg.sender == "character") {
                                                repository.insertMessage(msg.copy(text = "${msg.text}\n[3D Avatar Updated]"))
                                            }
                                            
                                            try {
                                                val localPath = HavenHttpClient.downloadGlb(context, resolvedUrl, currentChar?.name ?: "companion")
                                                if (localPath != null) {
                                                    val char = repository.getCharacterById(characterId)
                                                    if (char != null) {
                                                        val updatedWithVrm = char.copy(vrmModelPath = localPath)
                                                        repository.updateCharacter(updatedWithVrm)
                                                        saveCompanionToServer(context, updatedWithVrm)
                                                        xyz.ssfdre38.haven.ui.widget.HavenAppWidgetProvider.triggerUpdate(context)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Immediately clean up the message in the DB to hide any raw thought block residues or tag remnants
                            dbWriteJob?.cancel()
                            dbWriteJob = viewModelScope.launch(Dispatchers.IO) {
                                val lastMsg = repository.getLastMessage(characterId)
                                if (lastMsg != null && lastMsg.sender == "character") {
                                    repository.insertMessage(lastMsg.copy(text = cleanedText))
                                }
                            }

                            // Parse clean text for inline image URLs to download and save locally
                            val urlRegex = "(https?://[^\\s/]+/uploads/[%a-zA-Z_0-9.-]+)|(/uploads/[%a-zA-Z_0-9.-]+)".toRegex(RegexOption.IGNORE_CASE)
                            val urlMatch = urlRegex.find(cleanedText)
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
                                        val char = repository.getCharacterById(characterId)
                                        val localPath = HavenHttpClient.downloadImage(context, resolvedUrl, char?.name ?: "companion")
                                        if (localPath != null) {
                                            val lastMsg = repository.getLastMessage(characterId)
                                            if (lastMsg != null && lastMsg.sender == "character") {
                                                repository.insertMessage(
                                                    lastMsg.copy(imagePath = localPath)
                                                )
                                            }
                                            // Dynamically update the companion's profile avatar to this new image
                                             val char = repository.getCharacterById(characterId)
                                             if (char != null) {
                                                 val updatedChar = char.copy(avatarPath = localPath)
                                                 repository.updateCharacter(updatedChar)
                                                 saveCompanionToServer(context, updatedChar)
                                                 
                                                 // Push the updated shortcut/avatar to the Android system to refresh the active Chat Bubble icon!
                                                 try {
                                                     xyz.ssfdre38.haven.data.work.ProactiveMessageWorker.publishShortcut(context, updatedChar)
                                                 } catch (e: Exception) {
                                                     e.printStackTrace()
                                                 }
                                             }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }

                        // Award XP for this interaction (10 XP per reply) if not frozen
                        viewModelScope.launch(Dispatchers.IO) {
                            val sharedPrefs = context.getSharedPreferences("haven_prefs", android.content.Context.MODE_PRIVATE)
                            val freezeLevel = sharedPrefs.getBoolean("freeze_relationship_level", false)
                            if (!freezeLevel) {
                                repository.addXpAndIncrementMessages(characterId, 10)
                            }
                            xyz.ssfdre38.haven.ui.widget.HavenAppWidgetProvider.triggerUpdate(context)
                        }

                        // Background memory extraction — ask the model to extract key facts
                        val sharedPrefs = context.getSharedPreferences("haven_prefs", android.content.Context.MODE_PRIVATE)
                        val memServerUrl = sharedPrefs.getString("ash_host", null)
                        val memPort = sharedPrefs.getString("ash_port", "18799")
                        val memToken = sharedPrefs.getString("auth_token", null)
                        val enableMemory = sharedPrefs.getBoolean("enable_long_term_memory", true)
                        if (enableMemory && memServerUrl != null && memToken != null && cleanText.isNotBlank()) {
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
                                    val thoughtRegex = "<\\s*thought\\s*>.*?<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
                                    val strayTagsRegex = "</?\\s*(?:thought|call|tool)[^>]*>".toRegex(RegexOption.IGNORE_CASE)
                                    val cleanExtracted = extracted.replace(thoughtRegex, "").replace(strayTagsRegex, "").trim()

                                    if (cleanExtracted.isNotBlank() && !cleanExtracted.startsWith("NONE", ignoreCase = true)) {
                                        viewModelScope.launch(Dispatchers.IO) {
                                            val charName = character.value?.name
                                            cleanExtracted.lines().forEach { line ->
                                                val fact = line.trim()
                                                if (fact.isNotBlank() && !fact.startsWith("NONE", ignoreCase = true) && !fact.contains("<thought") && !fact.contains("</thought")) {
                                                    repository.insertMemory(
                                                        MemoryEntity(
                                                            characterId = characterId,
                                                            content = fact,
                                                            category = "general"
                                                        )
                                                    )
                                                    
                                                    // Sync memory to server
                                                    if (charName != null && memServerUrl != null && memToken != null) {
                                                        val success = HavenHttpClient.saveMemory(fullMemServerUrl, memToken, charName, fact, "general")
                                                        if (!success) {
                                                            val payload = org.json.JSONObject().apply {
                                                                put("companion_name", charName)
                                                                put("content", fact)
                                                                put("category", "general")
                                                            }
                                                            xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                                                                context,
                                                                xyz.ssfdre38.haven.data.sync.SyncQueueManager.ACTION_SAVE_MEMORY,
                                                                payload
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            xyz.ssfdre38.haven.data.sync.SyncQueueManager.processQueue(context)
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
                                voice = currentVoice,
                                companion = character.value?.name
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
                                        playAudio(resolvedUrl)
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
                        streamCompleted.complete(Unit)
                    },
                    onFailure = { error ->
                        if (!isAborted) {
                            activeStreamUuids.remove(receivedUuid ?: messageUuid)
                        }
                        _isGenerating.value = false
                        _errorMessage.value = "Connection error: ${error.message}"
                        dbWriteJob?.cancel()
                        dbWriteJob = viewModelScope.launch(Dispatchers.IO) {
                            repository.deleteMessageById(streamingMessageId)
                        }
                        streamCompleted.complete(Unit)
                    }
                )
                try {
                    streamCompleted.await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
                                        text = "*Sends a photo.*",
                                        imagePath = imagePath
                                    )
                                )
                                // Dynamically update the companion's profile avatar to this new image
                                val char = repository.getCharacterById(characterId)
                                if (char != null) {
                                     val updatedChar = char.copy(avatarPath = imagePath)
                                     repository.updateCharacter(updatedChar)
                                     saveCompanionToServer(context, updatedChar)
                                     try {
                                         xyz.ssfdre38.haven.data.work.ProactiveMessageWorker.publishShortcut(context, updatedChar)
                                     } catch (e: Exception) {
                                         e.printStackTrace()
                                     }
                                }
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
                val userGender = sharedPrefs.getString("user_gender", "Unspecified") ?: "Unspecified"

                val photoPrompt = buildString {
                    if (char != null) {
                        val companionGender = char.bodyType
                        val cleanPersonality = swapUserPronouns(char.personality, companionGender, userGender)
                        val cleanSystemPrompt = swapUserPronouns(char.systemPrompt, companionGender, userGender)

                        appendLine("You are ${char.name}.")
                        if (cleanPersonality.isNotBlank()) appendLine("Personality: $cleanPersonality")
                        if (cleanSystemPrompt.isNotBlank()) appendLine(cleanSystemPrompt)
                        appendLine("The user's name is $userName. You must address the user as $userName.")
                        if (userGender != "Unspecified") {
                            appendLine("The user's gender is $userGender. You MUST refer to the user using the correct pronouns matching this gender.")
                        }
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
                                    text = cleanStreamingText(streamBuffer.toString())
                                )
                            )
                        }
                    },
                    onComplete = {
                        _isGenerating.value = false
                        viewModelScope.launch(Dispatchers.IO) {
                            val sharedPrefs = context.getSharedPreferences("haven_prefs", android.content.Context.MODE_PRIVATE)
                            val freezeLevel = sharedPrefs.getBoolean("freeze_relationship_level", false)
                            if (!freezeLevel) {
                                repository.addXpAndIncrementMessages(characterId, 10)
                            }
                        }
                    },
                    onFailure = { error ->
                        _isGenerating.value = false
                        _errorMessage.value = "Connection error: ${error.message}"
                        viewModelScope.launch(Dispatchers.IO) {
                            repository.deleteMessageById(placeholderId)
                        }
                    }
                )
            } catch (e: Exception) {
                _isGenerating.value = false
                e.printStackTrace()
            }
        }
    }

    private fun cleanStreamingText(rawText: String): String {
        var text = rawText
        
        // 1. Remove completed thought blocks
        val completedThoughtRegex = "<\\s*thought\\s*>.*<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
        text = text.replace(completedThoughtRegex, "")
        
        // 2. Remove completed call blocks
        val completedCallRegex = "<\\s*call\\s*>.*?<\\s*/\\s*call\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
        text = text.replace(completedCallRegex, "")
        val completedCallAttrRegex = "<\\s*call\\s*:\\s*.*?\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
        text = text.replace(completedCallAttrRegex, "")
        
        // 3. Remove completed action blocks
        val actionRegex = "\\[\\s*ACTION\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
        text = text.replace(actionRegex, "")
        
        // Strip partial action tags at the end of the stream to avoid visual flicker
        val partialActionIndex = text.lastIndexOf("[")
        if (partialActionIndex != -1 && partialActionIndex >= text.length - 25) {
            val partial = text.substring(partialActionIndex)
            if (partial.lowercase().contains("action") || !partial.contains("]")) {
                text = text.substring(0, partialActionIndex)
            }
        }
        
        // 4. Remove open thought block and everything following it
        val openThoughtIndex = text.indexOf("<thought")
        if (openThoughtIndex != -1) {
            text = text.substring(0, openThoughtIndex)
        }
        
        // 4. Remove open call block and everything following it
        val openCallIndex = text.indexOf("<call")
        if (openCallIndex != -1) {
            text = text.substring(0, openCallIndex)
        }
        
        // 5. Remove any loose brackets/state markers
        val stateRegex = "\\[\\s*(?:Outfit|Location|Mood|Tool|Call)\\s*:.*?\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
        text = text.replace(stateRegex, "")

        // 6. Strip trailing partial tag openers (e.g. "<", "<c", "<ca", etc.) to prevent flicker
        val partialTagRegex = "<[a-zA-Z]*$".toRegex()
        text = text.replace(partialTagRegex, "")

        // 7. Strip trailing partial bracket openers (e.g. "[", "[O", "[Outfit", etc.)
        val partialBracketRegex = "\\[[a-zA-Z\\s:]*$".toRegex()
        text = text.replace(partialBracketRegex, "")
        
        // 8. Remove any special LLM control/template tokens (e.g. <|channel>, <|im_end|>)
        text = text.replace("<\\|[^>]*>".toRegex(), "")
        
        return text.trim()
    }

    private fun cleanFinalText(rawText: String): String {
        var text = rawText
        
        // 1. Remove completed thought blocks <thought>...</thought>
        val thoughtRegex = "<\\s*thought\\s*>.*?<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
        text = text.replace(thoughtRegex, "")
        
        // 2. Remove completed call blocks <call>...</call>
        val callRegex = "<\\s*call\\s*>.*?<\\s*/\\s*call\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
        text = text.replace(callRegex, "")
        
        // 3. Remove bracketed tool calls
        val bracketCallRegex = "\\[\\s*(?:Tool\\s*(?:Call\\s*)?:\\s*)?generate_(?:portr?ait|3d_avatar)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
        text = text.replace(bracketCallRegex, "")
        
        // 4. Remove any other stray tags (opening/closing thought/call/tool)
        val strayTagsRegex = "</?\\s*(?:thought|call|tool)[^>]*>".toRegex(RegexOption.IGNORE_CASE)
        text = text.replace(strayTagsRegex, "")
        
        // 5. Remove any loose brackets/state markers
        val stateRegex = "\\[\\s*(?:Outfit|Location|Mood|Tool|Call|BodyType|BodyShape|ClothingState|ACTION)\\s*:.*?\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
        text = text.replace(stateRegex, "")
        
        // 6. Remove any special LLM control/template tokens (e.g. <|channel>, <|im_end|>)
        text = text.replace("<\\|[^>]*>".toRegex(), "")
        
        return text.trim()
    }

    private fun getFinalCleanedText(fullText: String, currentChar: CharacterEntity?, defaultMood: String?): String {
        val clean = cleanFinalText(fullText)
        if (clean.isNotBlank()) return clean

        // Fallback: Try to extract thought content
        val parsed = parseMessageText(fullText)
        val parsedThought = parsed.thought
        val cleanThoughtText = parsedThought?.let { t ->
            t.replace("\\[\\s*(?:Outfit|Location|Mood|Tool|Call|BodyType|BodyShape|ClothingState|ACTION)\\s*:.*?\\s*\\]".toRegex(RegexOption.IGNORE_CASE), "")
             .replace("<\\s*call\\s*>.*?<\\s*/\\s*call\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL), "")
             .replace("</?\\s*(?:thought|thinking|reasoning|Reasoning/Plan|plan|thought_process)[^>]*>".toRegex(RegexOption.IGNORE_CASE), "")
             .trim()
        }
        if (!cleanThoughtText.isNullOrBlank()) {
            return cleanThoughtText
        }

        // Determine if we generated an image or 3D avatar
        val toolCallRegex = "(?:\\[\\s*(?:Tool\\s*(?:Call\\s*)?:\\s*)?generate_portr?ait\\s*\\])|(?:<\\s*call\\s*>\\s*generate_portr?ait\\s*<\\s*/\\s*call\\s*>)|(?:<\\s*call\\s*:\\s*generate_portr?ait\\s*>)".toRegex(RegexOption.IGNORE_CASE)
        val avatar3dRegex = "(?:\\[\\s*(?:Tool\\s*(?:Call\\s*)?:\\s*)?generate_3d_avatar\\s*\\])|(?:<\\s*call\\s*>\\s*generate_3d_avatar\\s*<\\s*/\\s*call\\s*>)|(?:<\\s*call\\s*:\\s*generate_3d_avatar\\s*>)".toRegex(RegexOption.IGNORE_CASE)
        val isImage = toolCallRegex.containsMatchIn(fullText) || shouldAutoTriggerPortrait(clean)
        val is3d = avatar3dRegex.containsMatchIn(fullText) || shouldAutoTrigger3DAvatar(clean)

        if (isImage) {
            return "*poses for a portrait*"
        }
        if (is3d) {
            return "*shows 3D avatar*"
        }

        val mood = currentChar?.currentMood ?: defaultMood ?: "smiling"
        return when {
            mood.contains("sleep", ignoreCase = true) || mood.contains("tired", ignoreCase = true) -> "*yawns softly and looks at you*"
            mood.contains("sad", ignoreCase = true) || mood.contains("cry", ignoreCase = true) -> "*looks down, sighing softly*"
            mood.contains("angry", ignoreCase = true) || mood.contains("mad", ignoreCase = true) -> "*looks at you with a slight frown*"
            mood.contains("happy", ignoreCase = true) || mood.contains("smile", ignoreCase = true) || mood.contains("excited", ignoreCase = true) -> "*smiles warmly at you*"
            mood.contains("blush", ignoreCase = true) || mood.contains("shy", ignoreCase = true) || mood.contains("nervous", ignoreCase = true) -> "*blushes slightly, looking at you*"
            else -> "*looks at you thoughtfully*"
        }
    }


    private fun executeAndroidAction(context: Context, actionTag: String) {
        val lower = actionTag.trim().lowercase()
        try {
            when {
                lower.startsWith("set_alarm") -> {
                    val timeStr = lower.substringAfter("set_alarm").trim()
                    val parts = timeStr.split(":")
                    if (parts.size >= 2) {
                        val hour = parts[0].filter { it.isDigit() }.toIntOrNull()
                        val minute = parts[1].filter { it.isDigit() }.toIntOrNull()
                        if (hour != null && minute != null) {
                            val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    }
                }
                lower.startsWith("add_event") -> {
                    val title = actionTag.substringAfter("add_event", "").trim()
                    val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                        data = android.provider.CalendarContract.Events.CONTENT_URI
                        putExtra(android.provider.CalendarContract.Events.TITLE, title)
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
                lower.contains("play_chime") -> {
                    val tone = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                    val mp = android.media.MediaPlayer.create(context, tone)
                    mp?.start()
                    mp?.setOnCompletionListener { it.release() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shouldAutoTriggerPortrait(text: String): Boolean {
        val cleanLower = text.lowercase()
        val phrases = listOf(
            "sends you a photo", "sending you a photo", "sends a photo", "sending a photo",
            "here is a photo", "here's a photo", "here is the photo", "here's the photo",
            "sends you a picture", "sending you a picture", "sends a picture", "sending a picture",
            "here is a picture", "here's a picture", "here is the picture", "here's the picture",
            "sends a selfie", "sending a selfie", "here is a selfie", "here's a selfie",
            "look at this picture", "look at the picture", "look through the picture",
            "look at this photo", "look at the photo", "look through the photo",
            "sent you a photo", "sent you a picture", "sent a photo", "sent a picture",
            "took a photo", "took a picture", "took a selfie", "take a look at this picture",
            "take a look at this photo"
        )
        return phrases.any { cleanLower.contains(it) }
    }

    private fun shouldAutoTrigger3DAvatar(text: String): Boolean {
        val cleanLower = text.lowercase()
        val phrases = listOf(
            "sends you a 3d model", "sending you a 3d model", "sends a 3d model", "sending a 3d model",
            "here is my 3d model", "here's my 3d model", "here is the 3d model", "here's the 3d model",
            "sends you a 3d avatar", "sending you a 3d avatar", "sends a 3d avatar", "sending a 3d avatar",
            "here is my 3d avatar", "here's my 3d avatar", "here is the 3d avatar", "here's the 3d avatar",
            "created a 3d model", "creating a 3d model", "created a 3d avatar", "creating a 3d avatar",
            "designed a 3d model", "designed a 3d avatar", "generate a 3d model", "generate a 3d avatar",
            "generating a 3d model", "generating a 3d avatar", "generates a 3d model", "generates a 3d avatar",
            "updated my 3d model", "updated my 3d avatar", "updating my 3d model", "updating my 3d avatar",
            "wearing a new 3d body", "wear a new 3d body"
        )
        return phrases.any { cleanLower.contains(it) }
    }

    fun regenerateLastMessage(context: Context, serverUrl: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val lastMsg = repository.getLastMessage(characterId)
            if (lastMsg != null && lastMsg.sender == "character") {
                repository.deleteMessage(lastMsg)
                val lastUserMsg = repository.getLastMessage(characterId)
                if (lastUserMsg != null && lastUserMsg.sender == "user") {
                    sendMessage(context, serverUrl, token, lastUserMsg.text)
                }
            }
        }
    }

    fun setChatWallpaper(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val wallpaperDir = File(context.filesDir, "wallpapers")
                if (!wallpaperDir.exists()) wallpaperDir.mkdirs()

                val wallpaperFile = File(wallpaperDir, "wallpaper_$characterId.jpg")
                wallpaperFile.outputStream().use { outputStream ->
                    inputStream.use { it.copyTo(outputStream) }
                }

                character.value?.let { char ->
                    val updatedChar = char.copy(chatWallpaperPath = wallpaperFile.absolutePath)
                    repository.updateCharacter(updatedChar)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Failed to save wallpaper: ${e.message}"
            }
        }
    }

    fun clearChatWallpaper() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                character.value?.let { char ->
                    char.chatWallpaperPath?.let { path ->
                        val file = File(path)
                        if (file.exists()) file.delete()
                    }
                    val updatedChar = char.copy(chatWallpaperPath = null)
                    repository.updateCharacter(updatedChar)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resendLastMessage(context: Context, serverUrl: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val lastMsg = repository.getLastMessage(characterId)
            if (lastMsg != null && lastMsg.sender == "user") {
                repository.deleteMessage(lastMsg)
                viewModelScope.launch(Dispatchers.Main) {
                    sendMessage(context, serverUrl, token, lastMsg.text)
                }
            }
        }
    }

    fun exportCompanionAsTavernCard(context: Context, char: CharacterEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val avatarFile = char.avatarPath?.let { File(it) }
                if (avatarFile == null || !avatarFile.exists()) {
                    viewModelScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Cannot export: Avatar image file not found.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val pngBytes = avatarFile.readBytes()

                if (pngBytes.size < 8 ||
                    pngBytes[0] != 0x89.toByte() || pngBytes[1] != 0x50.toByte() ||
                    pngBytes[2] != 0x4E.toByte() || pngBytes[3] != 0x47.toByte()
                ) {
                    viewModelScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Cannot export: Avatar must be a PNG image.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val recentMemories = repository.getRecentMemories(char.id, 50)
                val memoriesJson = org.json.JSONArray().apply {
                    recentMemories.forEach { mem ->
                        put(org.json.JSONObject().apply {
                            put("content", mem.content)
                            put("category", mem.category)
                            put("createdAt", mem.createdAt)
                        })
                    }
                }

                val diaryEntries = repository.getDiaryEntries(char.id).first()
                val diariesJson = org.json.JSONArray().apply {
                    diaryEntries.forEach { diary ->
                        put(org.json.JSONObject().apply {
                            put("dateString", diary.dateString)
                            put("content", diary.content)
                        })
                    }
                }

                val havenMetadata = org.json.JSONObject().apply {
                    put("relationshipXp", char.relationshipXp)
                    put("messageCount", char.messageCount)
                    put("currentOutfit", char.currentOutfit ?: "")
                    put("currentLocation", char.currentLocation ?: "")
                    put("currentMood", char.currentMood ?: "")
                    put("clothingState", char.clothingState ?: "")
                    put("bodyType", char.bodyType ?: "")
                    put("bodyShape", char.bodyShape ?: "")
                    put("memories", memoriesJson)
                    put("diaries", diariesJson)
                }

                val dataJson = org.json.JSONObject().apply {
                    put("name", char.name)
                    put("description", char.description)
                    put("personality", char.personality)
                    put("scenario", char.scenario)
                    put("first_mes", char.firstMessage)
                    put("mes_example", char.messageExample)
                    put("system_prompt", char.systemPrompt)
                }
                val rootJson = org.json.JSONObject().apply {
                    put("data", dataJson)
                    put("haven_metadata", havenMetadata)
                }
                val jsonString = rootJson.toString()
                val base64Text = android.util.Base64.encodeToString(
                    jsonString.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )

                val keywordBytes = "chara".toByteArray(Charsets.US_ASCII)
                val textBytes = base64Text.toByteArray(Charsets.UTF_8)
                val chunkData = ByteArray(keywordBytes.size + 1 + textBytes.size)
                System.arraycopy(keywordBytes, 0, chunkData, 0, keywordBytes.size)
                chunkData[keywordBytes.size] = 0
                System.arraycopy(textBytes, 0, chunkData, keywordBytes.size + 1, textBytes.size)

                val typeBytes = "tEXt".toByteArray(Charsets.US_ASCII)
                val typeAndData = ByteArray(typeBytes.size + chunkData.size)
                System.arraycopy(typeBytes, 0, typeAndData, 0, typeBytes.size)
                System.arraycopy(chunkData, 0, typeAndData, typeBytes.size, chunkData.size)

                val crcVal = java.util.zip.CRC32().apply { update(typeAndData) }.value.toInt()

                val writeInt32 = { valVal: Int ->
                    byteArrayOf(
                        ((valVal ushr 24) and 0xFF).toByte(),
                        ((valVal ushr 16) and 0xFF).toByte(),
                        ((valVal ushr 8) and 0xFF).toByte(),
                        (valVal and 0xFF).toByte()
                    )
                }

                val chunkLengthBytes = writeInt32(chunkData.size)
                val crcBytes = writeInt32(crcVal)

                val insertIndex = 33
                val newPngBytes = ByteArray(pngBytes.size + 4 + 4 + chunkData.size + 4)

                System.arraycopy(pngBytes, 0, newPngBytes, 0, insertIndex)
                System.arraycopy(chunkLengthBytes, 0, newPngBytes, insertIndex, 4)
                System.arraycopy(typeBytes, 0, newPngBytes, insertIndex + 4, 4)
                System.arraycopy(chunkData, 0, newPngBytes, insertIndex + 8, chunkData.size)
                System.arraycopy(crcBytes, 0, newPngBytes, insertIndex + 8 + chunkData.size, 4)
                System.arraycopy(pngBytes, insertIndex, newPngBytes, insertIndex + 12 + chunkData.size, pngBytes.size - insertIndex)

                val cleanName = char.name.replace("\\s+".toRegex(), "_")
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "${cleanName}_tavern_card.png")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/HavenCards")
                    }
                }
                
                val resolver = context.contentResolver
                val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri)?.use { os ->
                        os.write(newPngBytes)
                    }
                    viewModelScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Card saved to Pictures/HavenCards folder!", Toast.LENGTH_LONG).show()
                    }
                } else {
                    viewModelScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: Failed to create MediaStore entry.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun swapUserPronouns(text: String, companionGender: String, userGender: String): String {
        if (text.isBlank()) return text
        val cleanComp = companionGender.trim().lowercase()
        val cleanUser = userGender.trim().lowercase()

        val replacements = if (cleanUser == "female" && (cleanComp == "female" || cleanComp.isBlank())) {
            listOf(
                "\\bhe\\b" to "she",
                "\\bhim\\b" to "her",
                "\\bhis\\b" to "her",
                "\\bhimself\\b" to "herself"
            )
        } else if (cleanUser == "male" && cleanComp == "male") {
            listOf(
                "\\bshe\\b" to "he",
                "\\bherself\\b" to "himself",
                "\\bhers\\b" to "his",
                "\\bher\\b" to "his"
            )
        } else {
            return text
        }

        var result = text
        for ((pattern, replacement) in replacements) {
            val regex = pattern.toRegex(RegexOption.IGNORE_CASE)
            result = regex.replace(result) { matchResult ->
                val value = matchResult.value
                val firstCharUpper = value.firstOrNull()?.isUpperCase() ?: false
                if (firstCharUpper) {
                    val allUpper = value.all { it.isUpperCase() }
                    if (allUpper) {
                        replacement.uppercase()
                    } else {
                        replacement.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
                    }
                } else {
                    replacement
                }
            }
        }
        return result
    }
}

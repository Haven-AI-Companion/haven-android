package xyz.ssfdre38.haven.ui.group

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.CharacterEntity
import xyz.ssfdre38.haven.data.database.GroupChatEntity
import xyz.ssfdre38.haven.data.database.GroupMessageEntity
import xyz.ssfdre38.haven.data.network.HavenHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File

class GroupChatViewModel(
    private val groupId: Int,
    private val repository: DataRepository
) : ViewModel() {

    private val _group = MutableStateFlow<GroupChatEntity?>(null)
    val group: StateFlow<GroupChatEntity?> = _group.asStateFlow()

    private val _participants = MutableStateFlow<List<CharacterEntity>>(emptyList())
    val participants: StateFlow<List<CharacterEntity>> = _participants.asStateFlow()

    val messages: StateFlow<List<GroupMessageEntity>> = repository.getGroupMessages(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _selectedSpeakerId = MutableStateFlow<Int>(-1)
    val selectedSpeakerId: StateFlow<Int> = _selectedSpeakerId.asStateFlow()

    private var streamingMessageId = -1
    private val streamBuffer = StringBuilder()

    val autoBanterEnabled = MutableStateFlow(true)
    private var banterCount = 0

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val grp = repository.getGroupChatById(groupId)
            _group.value = grp
            if (grp != null) {
                val ids = grp.characterIdsString.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                val chars = ids.mapNotNull { repository.getCharacterById(it) }
                _participants.value = chars
                if (chars.isNotEmpty()) {
                    _selectedSpeakerId.value = chars.first().id
                }
            }
        }
    }

    fun selectSpeaker(characterId: Int) {
        _selectedSpeakerId.value = characterId
    }

    fun sendMessage(context: Context, serverUrl: String, token: String, text: String) {
        banterCount = 0
        val targetSpeakerId = _selectedSpeakerId.value
        val chars = _participants.value
        val targetChar = chars.firstOrNull { it.id == targetSpeakerId } ?: chars.firstOrNull() ?: return

        val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        val userName = sharedPrefs.getString("user_name", "User") ?: "User"

        viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true

            // 1. Insert user message
            repository.insertGroupMessage(
                GroupMessageEntity(
                    groupId = groupId,
                    sender = "user",
                    characterId = null,
                    text = text
                )
            )

            // 2. Insert streaming placeholder for active character response
            val placeholderId = repository.insertGroupMessage(
                GroupMessageEntity(
                    groupId = groupId,
                    sender = "character",
                    characterId = targetChar.id,
                    text = ""
                )
            ).toInt()
            streamingMessageId = placeholderId
            streamBuffer.clear()

            val conversationIdKey = "conversation_id_group_$groupId"
            var conversationId = sharedPrefs.getString(conversationIdKey, null)
            if (conversationId == null) {
                conversationId = java.util.UUID.randomUUID().toString()
                sharedPrefs.edit().putString(conversationIdKey, conversationId).apply()
            }

            // 3. Compile prompt
            val prompt = compilePrompt(text, targetChar, chars, userName)

            // 4. Stream response
            HavenHttpClient.streamChat(
                serverUrl = serverUrl,
                prompt = prompt,
                token = token,
                conversationId = conversationId,
                displayName = userName,
                onToken = { token ->
                    streamBuffer.append(token)
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.insertGroupMessage(
                            GroupMessageEntity(
                                id = streamingMessageId,
                                groupId = groupId,
                                sender = "character",
                                characterId = targetChar.id,
                                text = streamBuffer.toString().trim()
                            )
                        )
                    }
                },
                onComplete = {
                    _isGenerating.value = false
                    val fullText = streamBuffer.toString().trim()
                    
                    // Parse thoughts for state updates (outfit, location, mood) for the active speaker
                    val thoughtRegex = "<\\s*thought\\s*>(.*?)<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
                    val thoughtMatch = thoughtRegex.find(fullText)
                    if (thoughtMatch != null) {
                        val thought = thoughtMatch.groups[1]?.value ?: ""
                        val outfitRegex = "\\[\\s*Out\\s*fit\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val outfitMatch = outfitRegex.find(thought)
                        val newOutfit = outfitMatch?.groups[1]?.value?.trim()

                        val locationRegex = "\\[\\s*Location\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val locationMatch = locationRegex.find(thought)
                        val newLocation = locationMatch?.groups[1]?.value?.trim()

                        val moodRegex = "\\[\\s*Mood\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val moodMatch = moodRegex.find(thought)
                        val newMood = moodMatch?.groups[1]?.value?.trim()

                        if (newOutfit != null || newLocation != null || newMood != null) {
                            viewModelScope.launch(Dispatchers.IO) {
                                val currentChar = repository.getCharacterById(targetChar.id)
                                if (currentChar != null) {
                                    val outfitChanged = newOutfit != null && newOutfit != currentChar.currentOutfit
                                    val updatedChar = currentChar.copy(
                                        currentOutfit = newOutfit ?: currentChar.currentOutfit,
                                        currentLocation = newLocation ?: currentChar.currentLocation,
                                        currentMood = newMood ?: currentChar.currentMood
                                    )
                                    repository.updateCharacter(updatedChar)

                                    // Refresh local participants list
                                    val ids = _group.value?.characterIdsString?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
                                    _participants.value = ids.mapNotNull { repository.getCharacterById(it) }

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
                                                            val latestChar = repository.getCharacterById(targetChar.id)
                                                            if (latestChar != null) {
                                                                repository.updateCharacter(
                                                                    latestChar.copy(avatarPath = localPath)
                                                                )
                                                                // Refresh again
                                                                _participants.value = ids.mapNotNull { repository.getCharacterById(it) }
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

                    // Download inline generated image if present
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
                                    val lastMsg = repository.getLastGroupMessage(groupId)
                                    if (lastMsg != null && lastMsg.sender == "character") {
                                        repository.insertGroupMessage(
                                            lastMsg.copy(imagePath = localPath)
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    // Award XP for this interaction
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.addXpAndIncrementMessages(targetChar.id, 5)
                    }

                    // ── Auto Banter Turn trigger ──
                    if (autoBanterEnabled.value && banterCount < 2) {
                        banterCount++
                        val otherParticipants = chars.filter { it.id != targetChar.id }
                        if (otherParticipants.isNotEmpty()) {
                            val nextSpeaker = otherParticipants.random()
                            viewModelScope.launch {
                                delay(2500)
                                triggerBanterReply(context, serverUrl, token, nextSpeaker)
                            }
                        }
                    }
                },
                onFailure = { error ->
                    _isGenerating.value = false
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.insertGroupMessage(
                            GroupMessageEntity(
                                id = streamingMessageId,
                                groupId = groupId,
                                sender = "character",
                                characterId = targetChar.id,
                                text = "[Connection error: ${error.message}]"
                            )
                        )
                    }
                }
            )
        }
    }

    private suspend fun compilePrompt(
        currentInput: String,
        targetChar: CharacterEntity,
        allChars: List<CharacterEntity>,
        userName: String
    ): String {
        val systemContext = buildString {
            appendLine("This is a multi-companion group chat between $userName and the following characters:")
            allChars.forEach { c ->
                appendLine("- ${c.name}. Personality: ${c.personality}. Current Location: ${c.currentLocation.ifBlank { "Group Lobby" }}. Current Outfit: ${c.currentOutfit.ifBlank { "Casual" }}. Current Mood: ${c.currentMood.ifBlank { "neutral" }}")
            }
            appendLine()
            appendLine("[System Instructions: You are currently writing the response for ${targetChar.name} ONLY. Do not write dialogues or thoughts for other characters. Write your response based on the conversation history below.]")
            appendLine("[System Rule: Before responding, you MUST write down your inner thoughts, plans, or reasoning inside <thought>...</thought> tags, followed by your actual response to $userName. Do not omit the tags.]")
            appendLine("[System Instruction: You can dynamically update your location, outfit, or expression/mood if the context changes by including '[Location: name]', '[Outfit: description]', or '[Mood: expression]' inside your <thought>...</thought> block. Example: '<thought>[Outfit: nightgown] [Location: bedroom] [Mood: sleepy]</thought> Goodnight!' Only change these when it makes sense for the chat flow.]")
            appendLine("[System Instruction: Format roleplay actions, physical gestures, and immediate/direct thoughts using asterisks (e.g. *smiles and waves*, *thinking to myself: this is interesting*). Do not use square brackets [like this] for roleplay actions or thoughts. Understand that $userName will also use asterisks for their actions and thoughts.]")
            appendLine("[System Instruction: You have access to the 'generate_portrait' tool. You should invoke it whenever $userName asks for a picture, photo, selfie, or visual update, or when you decide on your own to show $userName what you are doing. Always call the tool first, and then include the returned URL path in your final message so $userName can see it!]")
        }

        // Fetch history
        val allMsgs = repository.getGroupMessages(groupId).first()
        val history = allMsgs.takeLast(20) // last 20 messages context

        val formattedHistory = history.joinToString("\n") { m ->
            val senderName = if (m.sender == "user") {
                userName
            } else {
                allChars.firstOrNull { it.id == m.characterId }?.name ?: "Companion"
            }
            val cleanText = m.text.replace("<\\s*thought\\s*>(.*?)<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL), "").trim()
            "$senderName: $cleanText"
        }

        return "$systemContext\n\n=== Conversation History ===\n$formattedHistory\n$userName: $currentInput\n${targetChar.name}:"
    }

    fun triggerBanterReply(context: Context, serverUrl: String, token: String, targetChar: CharacterEntity) {
        val chars = _participants.value
        val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        val userName = sharedPrefs.getString("user_name", "User") ?: "User"

        viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true

            val placeholderId = repository.insertGroupMessage(
                GroupMessageEntity(
                    groupId = groupId,
                    sender = "character",
                    characterId = targetChar.id,
                    text = ""
                )
            ).toInt()
            streamingMessageId = placeholderId
            streamBuffer.clear()

            val conversationIdKey = "conversation_id_group_$groupId"
            var conversationId = sharedPrefs.getString(conversationIdKey, null)
            if (conversationId == null) {
                conversationId = java.util.UUID.randomUUID().toString()
                sharedPrefs.edit().putString(conversationIdKey, conversationId).apply()
            }

            val prompt = compileBanterPrompt(targetChar, chars, userName)

            HavenHttpClient.streamChat(
                serverUrl = serverUrl,
                prompt = prompt,
                token = token,
                conversationId = conversationId,
                displayName = userName,
                onToken = { token ->
                    streamBuffer.append(token)
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.insertGroupMessage(
                            GroupMessageEntity(
                                id = streamingMessageId,
                                groupId = groupId,
                                sender = "character",
                                characterId = targetChar.id,
                                text = streamBuffer.toString().trim()
                            )
                        )
                    }
                },
                onComplete = {
                    _isGenerating.value = false
                    val fullText = streamBuffer.toString().trim()
                    
                    val thoughtRegex = "<\\s*thought\\s*>(.*?)<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
                    val thoughtMatch = thoughtRegex.find(fullText)
                    if (thoughtMatch != null) {
                        val thought = thoughtMatch.groups[1]?.value ?: ""
                        val outfitRegex = "\\[\\s*Out\\s*fit\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val outfitMatch = outfitRegex.find(thought)
                        val newOutfit = outfitMatch?.groups[1]?.value?.trim()

                        val locationRegex = "\\[\\s*Location\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val locationMatch = locationRegex.find(thought)
                        val newLocation = locationMatch?.groups[1]?.value?.trim()

                        val moodRegex = "\\[\\s*Mood\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                        val moodMatch = moodRegex.find(thought)
                        val newMood = moodMatch?.groups[1]?.value?.trim()

                        if (newOutfit != null || newLocation != null || newMood != null) {
                            viewModelScope.launch(Dispatchers.IO) {
                                val currentChar = repository.getCharacterById(targetChar.id)
                                if (currentChar != null) {
                                    val updatedChar = currentChar.copy(
                                        currentOutfit = newOutfit ?: currentChar.currentOutfit,
                                        currentLocation = newLocation ?: currentChar.currentLocation,
                                        currentMood = newMood ?: currentChar.currentMood
                                    )
                                    repository.updateCharacter(updatedChar)
                                }
                            }
                        }
                    }

                    viewModelScope.launch(Dispatchers.IO) {
                        repository.addXpAndIncrementMessages(targetChar.id, 5)
                    }

                    if (autoBanterEnabled.value && banterCount < 2) {
                        banterCount++
                        val otherParticipants = chars.filter { it.id != targetChar.id }
                        if (otherParticipants.isNotEmpty()) {
                            val nextSpeaker = otherParticipants.random()
                            viewModelScope.launch {
                                delay(2500)
                                triggerBanterReply(context, serverUrl, token, nextSpeaker)
                            }
                        }
                    }
                },
                onFailure = { error ->
                    _isGenerating.value = false
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.insertGroupMessage(
                            GroupMessageEntity(
                                id = streamingMessageId,
                                groupId = groupId,
                                sender = "character",
                                characterId = targetChar.id,
                                text = "[Connection error: ${error.message}]"
                            )
                        )
                    }
                }
            )
        }
    }

    private suspend fun compileBanterPrompt(
        targetChar: CharacterEntity,
        allChars: List<CharacterEntity>,
        userName: String
    ): String {
        val systemContext = buildString {
            appendLine("This is a multi-companion group chat between $userName and the following characters:")
            allChars.forEach { c ->
                appendLine("- ${c.name}. Personality: ${c.personality}. Current Location: ${c.currentLocation.ifBlank { "Group Lobby" }}. Current Outfit: ${c.currentOutfit.ifBlank { "Casual" }}. Current Mood: ${c.currentMood.ifBlank { "neutral" }}")
            }
            appendLine()
            appendLine("[System Instructions: You are currently writing the response for ${targetChar.name} ONLY. Do not write dialogues or thoughts for other characters. Respond to the other participants naturally, having a back-and-forth dialogue.]")
            appendLine("[System Rule: Before responding, you MUST write down your inner thoughts, plans, or reasoning inside <thought>...</thought> tags, followed by your actual response to $userName. Do not omit the tags.]")
            appendLine("[System Instruction: Format roleplay actions, physical gestures, and immediate/direct thoughts using asterisks (e.g. *smiles and waves*, *thinking to myself: this is interesting*). Do not use square brackets [like this] for roleplay actions or thoughts. Understand that $userName will also use asterisks for their actions and thoughts.]")
        }

        val allMsgs = repository.getGroupMessages(groupId).first()
        val history = allMsgs.takeLast(20)

        val formattedHistory = history.joinToString("\n") { m ->
            val senderName = if (m.sender == "user") {
                userName
            } else {
                allChars.firstOrNull { it.id == m.characterId }?.name ?: "Companion"
            }
            val cleanText = m.text.replace("<\\s*thought\\s*>(.*?)<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL), "").trim()
            "$senderName: $cleanText"
        }

        return "$systemContext\n\n=== Conversation History ===\n$formattedHistory\n${targetChar.name}:"
    }
}

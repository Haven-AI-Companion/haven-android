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

    private val _activeStreamingMessageId = MutableStateFlow(-1)
    val activeStreamingMessageId: StateFlow<Int> = _activeStreamingMessageId.asStateFlow()

    private var streamingMessageId = -1
        set(value) {
            field = value
            _activeStreamingMessageId.value = value
        }
    private val streamBuffer = StringBuilder()

    val autoBanterEnabled = MutableStateFlow(true)
    val banterLimit = MutableStateFlow(2) // -1 means Infinite, positive numbers are limits
    private var banterCount = 0
    private var banterJob: kotlinx.coroutines.Job? = null

    private var activePlayer: android.media.MediaPlayer? = null

    val ambientAudioMap = mapOf(
        "fireplace" to "https://assets.mixkit.co/active_storage/sfx/2433/2433-84.wav",
        "cozy" to "https://assets.mixkit.co/active_storage/sfx/2433/2433-84.wav",
        "rain" to "https://assets.mixkit.co/active_storage/sfx/2448/2448-84.wav",
        "cafe" to "https://assets.mixkit.co/active_storage/sfx/2568/2568-84.wav",
        "library" to "https://assets.mixkit.co/active_storage/sfx/1971/1971-84.wav"
    )

    private var ambientPlayer: android.media.MediaPlayer? = null
    private var currentAmbientUrl: String? = null

    fun updateAmbientSound(location: String) {
        val locLower = location.lowercase()
        val matchUrl = ambientAudioMap.entries.firstOrNull { locLower.contains(it.key) }?.value
        
        viewModelScope.launch(Dispatchers.Main) {
            if (matchUrl == null) {
                fadeAndStopAmbient()
                return@launch
            }
            
            if (currentAmbientUrl == matchUrl && ambientPlayer?.isPlaying == true) {
                return@launch
            }
            
            fadeAndStopAmbient()
            currentAmbientUrl = matchUrl
            
            try {
                val player = android.media.MediaPlayer().apply {
                    setDataSource(matchUrl)
                    isLooping = true
                    setVolume(0f, 0f)
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        fadeInVolume(this, 0.15f)
                    }
                }
                ambientPlayer = player
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun fadeAndStopAmbient() {
        val player = ambientPlayer ?: return
        ambientPlayer = null
        currentAmbientUrl = null
        
        viewModelScope.launch(Dispatchers.Main) {
            try {
                for (i in 10 downTo 0) {
                    val vol = (i / 10f) * 0.15f
                    player.setVolume(vol, vol)
                    delay(100)
                }
                player.stop()
                player.release()
            } catch (e: Exception) {
                try { player.release() } catch (ex: Exception) {}
            }
        }
    }
    
    private fun fadeInVolume(player: android.media.MediaPlayer, targetVolume: Float) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                for (i in 0..10) {
                    val vol = (i / 10f) * targetVolume
                    player.setVolume(vol, vol)
                    delay(150)
                }
            } catch (e: Exception) {
                try { player.setVolume(targetVolume, targetVolume) } catch (ex: Exception) {}
            }
        }
    }

    private val _typingCompanionName = MutableStateFlow<String?>(null)
    val typingCompanionName: StateFlow<String?> = _typingCompanionName.asStateFlow()

    private val _lastRoomEvent = MutableStateFlow<String?>(null)
    val lastRoomEvent: StateFlow<String?> = _lastRoomEvent.asStateFlow()

    fun extractRoomEvent(charName: String, cleanText: String, newOutfit: String?, newLocation: String?, newMood: String?): String? {
        val actions = mutableListOf<String>()
        if (newOutfit != null) actions.add("changed outfit to $newOutfit")
        if (newLocation != null) actions.add("moved location to $newLocation")
        if (newMood != null) actions.add("mood became $newMood")
        
        val asteriskRegex = "\\*(.*?)\\*".toRegex()
        val matches = asteriskRegex.findAll(cleanText).map { it.groupValues[1].trim() }.toList()
        if (matches.isNotEmpty()) {
            actions.addAll(matches.take(2))
        }
        
        if (actions.isEmpty()) return null
        return "$charName ${actions.joinToString(" and ")}."
    }

    data class CompanionRelation(
        val affinity: Int = 50,
        val sentiment: String = "neutral"
    )

    private fun getRelationsFile(context: Context): File {
        return File(context.filesDir, "companion_relations.json")
    }

    fun loadRelations(context: Context): Map<String, Map<String, CompanionRelation>> {
        val file = getRelationsFile(context)
        if (!file.exists()) return emptyMap()
        return try {
            val jsonStr = file.readText()
            val jsonObj = org.json.JSONObject(jsonStr)
            val result = mutableMapOf<String, Map<String, CompanionRelation>>()
            jsonObj.keys().forEach { sourceKey ->
                val innerObj = jsonObj.getJSONObject(sourceKey)
                val innerMap = mutableMapOf<String, CompanionRelation>()
                innerObj.keys().forEach { targetKey ->
                    val relObj = innerObj.getJSONObject(targetKey)
                    innerMap[targetKey] = CompanionRelation(
                        affinity = relObj.optInt("affinity", 50),
                        sentiment = relObj.optString("sentiment", "neutral")
                    )
                }
                result[sourceKey] = innerMap
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    fun saveRelations(context: Context, relations: Map<String, Map<String, CompanionRelation>>) {
        val file = getRelationsFile(context)
        try {
            val jsonObj = org.json.JSONObject()
            relations.forEach { (sourceKey, innerMap) ->
                val innerObj = org.json.JSONObject()
                innerMap.forEach { (targetKey, rel) ->
                    val relObj = org.json.JSONObject().apply {
                        put("affinity", rel.affinity)
                        put("sentiment", rel.sentiment)
                    }
                    innerObj.put(targetKey, relObj)
                }
                jsonObj.put(sourceKey, innerObj)
            }
            file.writeText(jsonObj.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun detectRelationshipTitle(targetChar: CharacterEntity?, affinity: Int): String {
        return when {
            affinity >= 95 -> "devoted lover"
            affinity >= 85 -> {
                val desc = targetChar?.description?.lowercase() ?: ""
                when {
                    desc.contains("female") || desc.contains("girl") || desc.contains("woman") || desc.contains("she/her") -> "girlfriend"
                    desc.contains("male") || desc.contains("boy") || desc.contains("man") || desc.contains("he/him") -> "boyfriend"
                    else -> "lover"
                }
            }
            affinity >= 70 -> "close friend"
            affinity >= 50 -> "friendly"
            affinity >= 35 -> "neutral"
            else -> "distant"
        }
    }

    fun adjustAffinity(context: Context, sourceCharName: String, targetCharName: String, text: String) {
        val textLower = text.lowercase()
        var diff = 0
        val posWords = listOf("thank", "love", "smile", "happy", "wonderful", "agree", "great", "friendly", "friend", "kind", "beautiful", "sweet", "darling", "honey", "boyfriend", "girlfriend", "kiss", "hug", "babe", "dear")
        val negWords = listOf("disagree", "annoying", "stop", "angry", "frown", "sigh", "ignore", "rude", "cold", "jealous", "hate", "fight")
        
        posWords.forEach { word ->
            if (textLower.contains(word)) diff += 2
        }
        negWords.forEach { word ->
            if (textLower.contains(word)) diff -= 2
        }
        
        if (diff != 0) {
            viewModelScope.launch(Dispatchers.IO) {
                val relations = loadRelations(context).toMutableMap()
                val innerMap = relations[sourceCharName]?.toMutableMap() ?: mutableMapOf()
                val currentRel = innerMap[targetCharName] ?: CompanionRelation()
                
                val newAffinity = (currentRel.affinity + diff).coerceIn(10, 100)
                val targetCharEntity = repository.getCharacterByName(targetCharName)
                val newSentiment = detectRelationshipTitle(targetCharEntity, newAffinity)
                
                innerMap[targetCharName] = CompanionRelation(newAffinity, newSentiment)
                relations[sourceCharName] = innerMap
                saveRelations(context, relations)
                
                val ids = _group.value?.characterIdsString?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
                _participants.value = ids.mapNotNull { repository.getCharacterById(it) }
            }
        }
    }

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
                    }
                    setOnCompletionListener {
                        release()
                        if (activePlayer == this) {
                            activePlayer = null
                        }
                    }
                    setOnErrorListener { _, _, _ ->
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
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activePlayer?.let { player ->
            activePlayer = null
            try {
                player.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        ambientPlayer?.let { player ->
            ambientPlayer = null
            try {
                player.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateParticipants(context: Context, serverUrl: String, token: String, newChars: List<CharacterEntity>) {
        val grp = _group.value ?: return
        val newIdsString = newChars.map { it.id }.joinToString(",")
        val updatedGrp = grp.copy(characterIdsString = newIdsString)
        
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertGroupChat(updatedGrp)
            _group.value = updatedGrp
            _participants.value = newChars
            
            // Re-select active speaker if the previous one is no longer present
            val currentSpeaker = _selectedSpeakerId.value
            if (newChars.none { it.id == currentSpeaker } && newChars.isNotEmpty()) {
                _selectedSpeakerId.value = newChars.first().id
            }
            
            // Push changes to server
            val characterNames = newChars.map { it.name }.joinToString(",")
            val success = HavenHttpClient.saveGroup(serverUrl, token, grp.uuid ?: "", grp.name, characterNames)
            if (!success && grp.uuid != null) {
                val payload = org.json.JSONObject().apply {
                    put("id", grp.uuid)
                    put("name", grp.name)
                    put("character_names", characterNames)
                }
                xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                    context,
                    xyz.ssfdre38.haven.data.sync.SyncQueueManager.ACTION_SAVE_GROUP,
                    payload
                )
            }
        }
    }

    fun stopBanter() {
        banterJob?.cancel()
        banterJob = null
        banterCount = 0
        streamingMessageId = -1
        _isGenerating.value = false
        _typingCompanionName.value = null
        _lastRoomEvent.value = null
        activePlayer?.let { player ->
            activePlayer = null
            try { player.stop(); player.release() } catch (e: Exception) {}
        }
        ambientPlayer?.let { player ->
            ambientPlayer = null
            currentAmbientUrl = null
            try { player.stop(); player.release() } catch (e: Exception) {}
        }
    }

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
                    updateAmbientSound(chars.first().currentLocation)
                }
            }
        }
    }

    fun selectSpeaker(characterId: Int) {
        _selectedSpeakerId.value = characterId
        val char = _participants.value.firstOrNull { it.id == characterId }
        if (char != null) {
            updateAmbientSound(char.currentLocation)
        }
    }

    fun sendMessage(context: Context, serverUrl: String, token: String, text: String) {
        stopBanter()
        banterCount = 0
        val targetSpeakerId = _selectedSpeakerId.value
        val chars = _participants.value
        val targetChar = chars.firstOrNull { it.id == targetSpeakerId } ?: chars.firstOrNull() ?: return

        val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
        val userName = sharedPrefs.getString("user_name", "User") ?: "User"

        viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true
            _typingCompanionName.value = targetChar.name

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

            val grp = _group.value
            var groupUuid = grp?.uuid
            if (groupUuid.isNullOrBlank() && grp != null) {
                groupUuid = java.util.UUID.randomUUID().toString()
                val updatedGrp = grp.copy(uuid = groupUuid)
                repository.insertGroupChat(updatedGrp)
                _group.value = updatedGrp
                
                // Push configuration to server
                val characterNames = chars.map { it.name }.joinToString(",")
                val success = HavenHttpClient.saveGroup(serverUrl, token, groupUuid, grp.name, characterNames)
                if (!success) {
                    val payload = org.json.JSONObject().apply {
                        put("id", groupUuid)
                        put("name", grp.name)
                        put("character_names", characterNames)
                    }
                    xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                        context,
                        xyz.ssfdre38.haven.data.sync.SyncQueueManager.ACTION_SAVE_GROUP,
                        payload
                    )
                }
            }

            if (groupUuid != null) {
                val success = HavenHttpClient.saveGroupMessage(serverUrl, token, groupUuid, "user", null, text)
                if (!success) {
                    val payload = org.json.JSONObject().apply {
                        put("group_id", groupUuid)
                        put("sender", "user")
                        put("character_name", null)
                        put("content", text)
                    }
                    xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                        context,
                        xyz.ssfdre38.haven.data.sync.SyncQueueManager.ACTION_SAVE_GROUP_MESSAGE,
                        payload
                    )
                }
            }

            // 3. Compile prompt
            val prompt = compilePrompt(context, text, targetChar, chars, userName)

            // 4. Stream response
            HavenHttpClient.streamChat(
                serverUrl = serverUrl,
                prompt = prompt,
                token = token,
                conversationId = groupUuid,
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
                                text = cleanStreamingText(streamBuffer.toString())
                            )
                        )
                    }
                },
                onComplete = {
                    _isGenerating.value = false
                    val fullText = streamBuffer.toString().trim()
                    
                    // Parse thoughts for state updates (outfit, location, mood) for the active speaker
                    val thoughtRegex = "<\\s*thought\\s*>(.*)<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
                    val toolCallRegex = "(?:\\[\\s*(?:Tool\\s*(?:Call\\s*)?:\\s*)?generate_portr?ait\\s*\\])|(?:<\\s*call\\s*>\\s*generate_portr?ait\\s*<\\s*/\\s*call\\s*>)|(?:<\\s*call\\s*:\\s*generate_portr?ait\\s*>)".toRegex(RegexOption.IGNORE_CASE)
                    val cleanText = fullText.replace(thoughtRegex, "").replace(toolCallRegex, "").trim()
                    var newOutfit: String? = null
                    var newLocation: String? = null
                    var newMood: String? = null

                    // Extract [Outfit: ...] from fullText for maximum robustness
                    val outfitRegex = "\\[\\s*Out\\s*fit\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                    val outfitMatch = outfitRegex.find(fullText)
                    newOutfit = outfitMatch?.groups[1]?.value?.trim()

                    val locationRegex = "\\[\\s*Location\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                    val locationMatch = locationRegex.find(fullText)
                    newLocation = locationMatch?.groups[1]?.value?.trim()

                    val moodRegex = "\\[\\s*Mood\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                    val moodMatch = moodRegex.find(fullText)
                    newMood = moodMatch?.groups[1]?.value?.trim()

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
                                    if (newLocation != null) {
                                        updateAmbientSound(newLocation)
                                    }

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
                                                        val localPath = HavenHttpClient.downloadImage(context, resolvedUrl, targetChar.name)
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

                    val hasToolTag = toolCallRegex.containsMatchIn(fullText)
                    val hasImplicitTrigger = !hasToolTag && shouldAutoTriggerPortrait(cleanText)

                    if (hasToolTag || hasImplicitTrigger) {
                        val cleanedText = fullText.replace(toolCallRegex, "").trim()
                        val textWithoutThoughts = cleanText
                        
                        // Immediately clean up the message in the DB to hide the raw tool call from the user
                        viewModelScope.launch(Dispatchers.IO) {
                            val lastMsg = repository.getLastGroupMessage(groupId)
                            if (lastMsg != null && lastMsg.sender == "character") {
                                repository.insertGroupMessage(lastMsg.copy(text = cleanedText))
                            }
                        }

                        // Trigger the image generation tool
                        viewModelScope.launch(Dispatchers.IO) {
                            val currentChar = repository.getCharacterById(targetChar.id)
                            val outfitPrompt = if (currentChar != null) {
                                val currentOutfitVal = newOutfit ?: currentChar.currentOutfit
                                val currentLocationVal = newLocation ?: currentChar.currentLocation
                                val currentMoodVal = newMood ?: currentChar.currentMood
                                "${currentChar.name}, description: ${currentChar.description}, wearing ${currentOutfitVal.ifBlank { "casual attire" }}, location: ${currentLocationVal.ifBlank { "cozy room" }}, expression: ${currentMoodVal.ifBlank { "smiling" }}"
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
                                        val msg = repository.getLastGroupMessage(groupId)
                                        if (msg != null && msg.sender == "character") {
                                            repository.insertGroupMessage(msg.copy(text = "${msg.text}\n$relativeUrl"))
                                        }
                                        
                                        // Download and save the portrait
                                        try {
                                            val localPath = HavenHttpClient.downloadImage(context, resolvedUrl, targetChar.name)
                                            if (localPath != null) {
                                                val finalMsg = repository.getLastGroupMessage(groupId)
                                                if (finalMsg != null && finalMsg.sender == "character") {
                                                    repository.insertGroupMessage(finalMsg.copy(imagePath = localPath))
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
                        // Download inline generated image if present
                        val urlRegex = "(https?://[^\\s/]+/uploads/[%a-zA-Z_0-9.-]+)|(/uploads/[%a-zA-Z_0-9.-]+)".toRegex(RegexOption.IGNORE_CASE)
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
                                    val localPath = HavenHttpClient.downloadImage(context, resolvedUrl, targetChar.name)
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
                    }

                    // Push response to server
                    if (groupUuid != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val success = HavenHttpClient.saveGroupMessage(serverUrl, token, groupUuid, "character", targetChar.name, cleanText)
                            if (!success) {
                                val payload = org.json.JSONObject().apply {
                                    put("group_id", groupUuid)
                                    put("sender", "character")
                                    put("character_name", targetChar.name)
                                    put("content", cleanText)
                                }
                                xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                                    context,
                                    xyz.ssfdre38.haven.data.sync.SyncQueueManager.ACTION_SAVE_GROUP_MESSAGE,
                                    payload
                                )
                            }
                        }
                    }

                    // Adjust affinity towards other participants based on dialogue text content
                    chars.filter { it.id != targetChar.id }.forEach { otherChar ->
                        adjustAffinity(context, targetChar.name, otherChar.name, cleanText)
                    }

                    val eventText = extractRoomEvent(targetChar.name, cleanText, newOutfit, newLocation, newMood)
                    if (eventText != null) {
                        _lastRoomEvent.value = eventText
                    }

                    _typingCompanionName.value = null

                    // Generate and play TTS audio response
                    val currentVoice = targetChar.voiceId ?: "en_US-amy-medium"
                    if (cleanText.isNotBlank()) {
                        val autoSpeak = sharedPrefs.getBoolean("auto_speak", true)
                        if (autoSpeak) {
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
                                    playAudio(resolvedUrl)
                                }
                            }
                        }
                    }

                    // Award XP for this interaction
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.addXpAndIncrementMessages(targetChar.id, 5)
                    }

                    // ── Auto Banter Turn trigger ──
                    val currentLimit = banterLimit.value
                    val shouldContinueBanter = autoBanterEnabled.value && 
                        (currentLimit == -1 || banterCount < currentLimit)

                    if (shouldContinueBanter) {
                        banterCount++
                        val otherParticipants = chars.filter { it.id != targetChar.id }
                        if (otherParticipants.isNotEmpty()) {
                            var nextSpeaker = otherParticipants.random()
                            val lowerText = cleanText.lowercase()
                            
                            // Check for direct mentions to select the next speaker dynamically
                            val mentionedChar = otherParticipants.firstOrNull { c ->
                                val nameLower = c.name.lowercase()
                                lowerText.contains("@$nameLower") || 
                                lowerText.contains("hey $nameLower") || 
                                lowerText.contains("what do you think, $nameLower") ||
                                lowerText.contains("right, $nameLower?") ||
                                lowerText.contains("$nameLower, ") ||
                                lowerText.contains("$nameLower?")
                            }
                            
                            if (mentionedChar != null) {
                                nextSpeaker = mentionedChar
                            }

                            banterJob?.cancel()
                            banterJob = viewModelScope.launch {
                                delay(3000)
                                triggerBanterReply(context, serverUrl, token, nextSpeaker)
                            }
                        }
                    }
                },
                onFailure = { error ->
                    _isGenerating.value = false
                    _typingCompanionName.value = null
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
        context: Context,
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
            appendLine("[System Instruction: You have access to the 'generate_portrait' tool. You should invoke it whenever $userName asks for a picture, photo, selfie, or visual update, or when you decide on your own to show $userName what you are doing. To call the tool, you MUST output the tag <call>generate_portrait</call> immediately after your </thought> tag (before your conversational dialogue). Do not output any arguments. The app will automatically generate the image and display it to the user.]")

            // Inject recent room situation events
            val lastEvent = lastRoomEvent.value
            if (!lastEvent.isNullOrBlank()) {
                appendLine()
                appendLine("[Current Room Situation: $lastEvent. You MUST acknowledge and react to this situation naturally in your thoughts and dialogue.]")
            }

            // Append mutual companion relationship sentiments
            val relations = loadRelations(context)
            val companionRelations = relations[targetChar.name]
            if (companionRelations != null && companionRelations.isNotEmpty()) {
                appendLine()
                appendLine("[Your relationship with other companions in this room:]")
                companionRelations.forEach { (otherName, rel) ->
                    appendLine("- You feel like their ${rel.sentiment} (affinity: ${rel.affinity}/100) towards $otherName.")
                }
            }

            appendLine()
            appendLine("[System Instruction: You are in an intimate, open, or romantic setting. Pay close attention to physical contact, touch, affection, and intimacy. If another companion is showing affection to $userName or to you, react with appropriate warmth, jealousy, passion, or teasing playfulness based on your relationship settings.]")

            // Load long-term memories from their single-companion chats
            val memories = repository.getRecentMemories(targetChar.id, 10)
            if (memories.isNotEmpty()) {
                appendLine()
                appendLine("[Memories you (${targetChar.name}) have about $userName from your previous interactions:]")
                memories.forEach { appendLine("- ${it.content}") }
            }
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
            _typingCompanionName.value = targetChar.name

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

            val grp = _group.value
            var groupUuid = grp?.uuid
            if (groupUuid.isNullOrBlank() && grp != null) {
                groupUuid = java.util.UUID.randomUUID().toString()
                val updatedGrp = grp.copy(uuid = groupUuid)
                repository.insertGroupChat(updatedGrp)
                _group.value = updatedGrp
                
                // Push configuration to server
                val characterNames = chars.map { it.name }.joinToString(",")
                val success = HavenHttpClient.saveGroup(serverUrl, token, groupUuid, grp.name, characterNames)
                if (!success) {
                    val payload = org.json.JSONObject().apply {
                        put("id", groupUuid)
                        put("name", grp.name)
                        put("character_names", characterNames)
                    }
                    xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                        context,
                        xyz.ssfdre38.haven.data.sync.SyncQueueManager.ACTION_SAVE_GROUP,
                        payload
                    )
                }
            }

            val prompt = compileBanterPrompt(context, targetChar, chars, userName)

            HavenHttpClient.streamChat(
                serverUrl = serverUrl,
                prompt = prompt,
                token = token,
                conversationId = groupUuid,
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
                                text = cleanStreamingText(streamBuffer.toString())
                            )
                        )
                    }
                },
                onComplete = {
                    _isGenerating.value = false
                    val fullText = streamBuffer.toString().trim()
                    
                    val thoughtRegex = "<\\s*thought\\s*>(.*)<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
                    // Extract [Outfit: ...] from fullText for maximum robustness
                    val outfitRegex = "\\[\\s*Out\\s*fit\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                    val outfitMatch = outfitRegex.find(fullText)
                    val newOutfit = outfitMatch?.groups[1]?.value?.trim()

                    val locationRegex = "\\[\\s*Location\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                    val locationMatch = locationRegex.find(fullText)
                    val newLocation = locationMatch?.groups[1]?.value?.trim()

                    val moodRegex = "\\[\\s*Mood\\s*:\\s*(.*?)\\s*\\]".toRegex(RegexOption.IGNORE_CASE)
                    val moodMatch = moodRegex.find(fullText)
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
                                    if (newLocation != null) {
                                        updateAmbientSound(newLocation)
                                    }
                                }
                            }
                        }

                    val toolCallRegex = "(?:\\[\\s*(?:Tool\\s*(?:Call\\s*)?:\\s*)?generate_portr?ait\\s*\\])|(?:<\\s*call\\s*>\\s*generate_portr?ait\\s*<\\s*/\\s*call\\s*>)|(?:<\\s*call\\s*:\\s*generate_portr?ait\\s*>)".toRegex(RegexOption.IGNORE_CASE)
                    val cleanText = fullText.replace(thoughtRegex, "").replace(toolCallRegex, "").trim()
                    
                    val targetId = streamingMessageId
                    viewModelScope.launch(Dispatchers.IO) {
                        val msg = repository.getGroupMessageById(targetId)
                        if (msg != null) {
                            repository.insertGroupMessage(msg.copy(text = cleanText))
                        }
                    }

                    val hasToolTag = toolCallRegex.containsMatchIn(fullText)
                    val hasImplicitTrigger = !hasToolTag && shouldAutoTriggerPortrait(cleanText)

                    if (hasToolTag || hasImplicitTrigger) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val currentChar = repository.getCharacterById(targetChar.id)
                            val outfitPrompt = if (currentChar != null) {
                                val currentOutfitVal = newOutfit ?: currentChar.currentOutfit
                                val currentLocationVal = newLocation ?: currentChar.currentLocation
                                val currentMoodVal = newMood ?: currentChar.currentMood
                                "${currentChar.name}, description: ${currentChar.description}, wearing ${currentOutfitVal.ifBlank { "casual attire" }}, location: ${currentLocationVal.ifBlank { "cozy room" }}, expression: ${currentMoodVal.ifBlank { "smiling" }}"
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
                                        val msg = repository.getGroupMessageById(targetId)
                                        if (msg != null) {
                                            repository.insertGroupMessage(msg.copy(text = "${msg.text}\n$relativeUrl"))
                                        }
                                        
                                        try {
                                            val localPath = HavenHttpClient.downloadImage(context, resolvedUrl, targetChar.name)
                                            if (localPath != null) {
                                                val finalMsg = repository.getGroupMessageById(targetId)
                                                if (finalMsg != null) {
                                                    repository.insertGroupMessage(finalMsg.copy(imagePath = localPath))
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
                        // Download inline generated image if present
                        val urlRegex = "(https?://[^\\s/]+/uploads/[%a-zA-Z_0-9.-]+)|(/uploads/[%a-zA-Z_0-9.-]+)".toRegex(RegexOption.IGNORE_CASE)
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
                                    val localPath = HavenHttpClient.downloadImage(context, resolvedUrl, targetChar.name)
                                    if (localPath != null) {
                                        val lastMsg = repository.getGroupMessageById(targetId)
                                        if (lastMsg != null) {
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
                    }

                    if (groupUuid != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val success = HavenHttpClient.saveGroupMessage(serverUrl, token, groupUuid, "character", targetChar.name, cleanText)
                            if (!success) {
                                val payload = org.json.JSONObject().apply {
                                    put("group_id", groupUuid)
                                    put("sender", "character")
                                    put("character_name", targetChar.name)
                                    put("content", cleanText)
                                }
                                xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                                    context,
                                    xyz.ssfdre38.haven.data.sync.SyncQueueManager.ACTION_SAVE_GROUP_MESSAGE,
                                    payload
                                )
                            }
                        }
                    }

                    // Adjust affinity towards other participants based on dialogue text content
                    chars.filter { it.id != targetChar.id }.forEach { otherChar ->
                        adjustAffinity(context, targetChar.name, otherChar.name, cleanText)
                    }

                    val eventText = extractRoomEvent(targetChar.name, cleanText, newOutfit, newLocation, newMood)
                    if (eventText != null) {
                        _lastRoomEvent.value = eventText
                    }

                    _typingCompanionName.value = null

                    // Generate and play TTS audio response
                    val currentVoice = targetChar.voiceId ?: "en_US-amy-medium"
                    if (cleanText.isNotBlank()) {
                        val autoSpeak = sharedPrefs.getBoolean("auto_speak", true)
                        if (autoSpeak) {
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
                                    playAudio(resolvedUrl)
                                }
                            }
                        }
                    }

                    viewModelScope.launch(Dispatchers.IO) {
                        repository.addXpAndIncrementMessages(targetChar.id, 5)
                    }

                    streamingMessageId = -1

                    val currentLimit = banterLimit.value
                    val shouldContinueBanter = autoBanterEnabled.value && 
                        (currentLimit == -1 || banterCount < currentLimit)

                    if (shouldContinueBanter) {
                        banterCount++
                        val otherParticipants = chars.filter { it.id != targetChar.id }
                        if (otherParticipants.isNotEmpty()) {
                            var nextSpeaker = otherParticipants.random()
                            val lowerText = cleanText.lowercase()
                            
                            // Check for direct mentions to select the next speaker dynamically
                            val mentionedChar = otherParticipants.firstOrNull { c ->
                                val nameLower = c.name.lowercase()
                                lowerText.contains("@$nameLower") || 
                                lowerText.contains("hey $nameLower") || 
                                lowerText.contains("what do you think, $nameLower") ||
                                lowerText.contains("right, $nameLower?") ||
                                lowerText.contains("$nameLower, ") ||
                                lowerText.contains("$nameLower?")
                            }
                            
                            if (mentionedChar != null) {
                                nextSpeaker = mentionedChar
                            }

                            banterJob?.cancel()
                            banterJob = viewModelScope.launch {
                                delay(3000)
                                triggerBanterReply(context, serverUrl, token, nextSpeaker)
                            }
                        }
                    }
                },
                onFailure = { error ->
                    _isGenerating.value = false
                    _typingCompanionName.value = null
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
        context: Context,
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

            // Inject recent room situation events
            val lastEvent = lastRoomEvent.value
            if (!lastEvent.isNullOrBlank()) {
                appendLine()
                appendLine("[Current Room Situation: $lastEvent. You MUST acknowledge and react to this situation naturally in your thoughts and dialogue.]")
            }

            // Append mutual companion relationship sentiments
            val relations = loadRelations(context)
            val companionRelations = relations[targetChar.name]
            if (companionRelations != null && companionRelations.isNotEmpty()) {
                appendLine()
                appendLine("[Your relationship with other companions in this room:]")
                companionRelations.forEach { (otherName, rel) ->
                    appendLine("- You feel like their ${rel.sentiment} (affinity: ${rel.affinity}/100) towards $otherName.")
                }
            }

            appendLine()
            appendLine("[System Instruction: You are in an intimate, open, or romantic setting. Pay close attention to physical contact, touch, affection, and intimacy. If another companion is showing affection to $userName or to you, react with appropriate warmth, jealousy, passion, or teasing playfulness based on your relationship settings.]")

            // Load long-term memories from their single-companion chats
            val memories = repository.getRecentMemories(targetChar.id, 10)
            if (memories.isNotEmpty()) {
                appendLine()
                appendLine("[Memories you (${targetChar.name}) have about $userName from your previous interactions:]")
                memories.forEach { appendLine("- ${it.content}") }
            }
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
        
        // 3. Remove open thought block and everything following it
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
        
        return text.trim()
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

    fun syncGroupMessages(context: Context, serverUrl: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val grp = repository.getGroupChatById(groupId) ?: return@launch
            val groupUuid = grp.uuid ?: return@launch
            val serverMsgs = HavenHttpClient.getGroupMessages(serverUrl, token, groupUuid)
            val localMsgs = repository.getGroupMessages(groupId).first()
            val participants = _participants.value
            
            if (serverMsgs.size == localMsgs.size) {
                return@launch
            }
            
            if (serverMsgs.size < localMsgs.size) {
                // Client has new offline group messages! Push them to the server
                for (i in serverMsgs.size until localMsgs.size) {
                    val localMsg = localMsgs[i]
                    val charName = if (localMsg.sender == "character") {
                        participants.firstOrNull { it.id == localMsg.characterId }?.name
                    } else null
                    HavenHttpClient.saveGroupMessage(serverUrl, token, groupUuid, localMsg.sender, charName, localMsg.text)
                }
            } else {
                // Server has new group messages! Append them locally
                for (i in localMsgs.size until serverMsgs.size) {
                    val obj = serverMsgs[i]
                    val sender = obj.getString("sender")
                    val characterName = when {
                        obj.has("character_name") && !obj.isNull("character_name") -> obj.getString("character_name")
                        obj.has("characterName") && !obj.isNull("characterName") -> obj.getString("characterName")
                        else -> null
                    }
                    val content = obj.getString("content")
                    
                    var characterId: Int? = null
                    if (sender == "character" && characterName != null) {
                        characterId = repository.getCharacterByName(characterName)?.id
                    }
                    
                    val msgId = repository.insertGroupMessage(
                        GroupMessageEntity(
                            groupId = groupId,
                            sender = sender,
                            characterId = characterId,
                            text = content
                        )
                    ).toInt()

                    // Parse clean text for inline image URLs to download and save locally
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
                            val localPath = HavenHttpClient.downloadImage(context, resolvedUrl, characterName ?: "companion")
                            if (localPath != null) {
                                repository.insertGroupMessage(
                                    GroupMessageEntity(
                                        id = msgId,
                                        groupId = groupId,
                                        sender = sender,
                                        characterId = characterId,
                                        text = content,
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

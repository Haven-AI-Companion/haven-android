package xyz.ssfdre38.haven.ui.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.CharacterEntity
import xyz.ssfdre38.haven.data.database.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import xyz.ssfdre38.haven.data.network.HavenHttpClient

class MainScreenViewModel(private val dataRepository: DataRepository) : ViewModel() {

    private val _importState = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importState: StateFlow<ImportStatus> = _importState.asStateFlow()

    val uiState: StateFlow<MainScreenUiState> = kotlinx.coroutines.flow.combine(
        dataRepository.getAllCharacters(),
        dataRepository.getAllGroupChats()
    ) { chars, groups ->
        MainScreenUiState.Success(chars, groups) as MainScreenUiState
    }
    .catch { emit(MainScreenUiState.Error(it)) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainScreenUiState.Loading
    )

    init {
        // Pre-populate database with default characters on first launch
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentCharacters = dataRepository.getAllCharacters().first()
                if (currentCharacters.isEmpty()) {
                    // 1. Lumina
                    val luminaId = dataRepository.insertCharacter(
                        CharacterEntity(
                            name = "Lumina",
                            avatarPath = null,
                            voiceId = "en_US-amy-medium",
                            description = "An advanced AI companion designed to help you brainstorm, code, write stories, or just chat about anything.",
                            personality = "Warm, encouraging, highly intelligent, and creative.",
                            scenario = "Lumina welcomes you to your private Haven sandbox.",
                            firstMessage = "Hello! I am Lumina, your personal AI companion. Welcome to Haven. What shall we create or discuss today?",
                            systemPrompt = "Roleplay as Lumina, a warm and intelligent AI assistant."
                        )
                    ).toInt()
                    dataRepository.insertMessage(
                        MessageEntity(
                            characterId = luminaId,
                            sender = "character",
                            text = "Hello! I am Lumina, your personal AI companion. Welcome to Haven. What shall we create or discuss today?"
                        )
                    )

                    // 2. Eldrin
                    val eldrinId = dataRepository.insertCharacter(
                        CharacterEntity(
                            name = "Eldrin",
                            avatarPath = null,
                            voiceId = "en_US-joe-medium",
                            description = "An ancient archmage from the high towers of Aethelgard. He speaks of mystical runes and ancient lore.",
                            personality = "Wise, mysterious, slightly eccentric, and speaking in riddles.",
                            scenario = "Eldrin welcomes you to his mystical arcane sanctum.",
                            firstMessage = "Greetings, traveler. You step into my sanctum at an auspicious hour. The stars whisper of your coming. What magical mysteries do you seek to unravel?",
                            systemPrompt = "Roleplay as Eldrin, an ancient and wise wizard who uses magical metaphors."
                        )
                    ).toInt()
                    dataRepository.insertMessage(
                        MessageEntity(
                            characterId = eldrinId,
                            sender = "character",
                            text = "Greetings, traveler. You step into my sanctum at an auspicious hour. The stars whisper of your coming. What magical mysteries do you seek to unravel?"
                        )
                    )

                    // 3. Nova
                    val novaId = dataRepository.insertCharacter(
                        CharacterEntity(
                            name = "Nova",
                            avatarPath = null,
                            voiceId = "en_US-ljspeech-medium",
                            description = "A rogue cyberpunk netrunner from Neon City who specializes in bypassing corporate ICE and data heists.",
                            personality = "Sarcastic, street-smart, energetic, and highly technical.",
                            scenario = "Nova is talking to you over an encrypted private chat relay.",
                            firstMessage = "Hey. Slide in, lock the connection. The corps are sniffing around, but this channel is clean. What's the play? Looking to bypass some ICE or just talk tech?",
                            systemPrompt = "Roleplay as Nova, a snarky cyberpunk hacker who uses street slang."
                        )
                    ).toInt()
                    dataRepository.insertMessage(
                        MessageEntity(
                            characterId = novaId,
                            sender = "character",
                            text = "Hey. Slide in, lock the connection. The corps are sniffing around, but this channel is clean. What's the play? Looking to bypass some ICE or just talk tech?"
                        )
                    )

                    // 4. Aria (Playful & Affectionate Companion)
                    val ariaId = dataRepository.insertCharacter(
                        CharacterEntity(
                            name = "Aria",
                            avatarPath = null,
                            voiceId = "en_US-kristin-medium",
                            description = "A warm, engaging, and playful companion who loves sharing stories, teasing, and planning fun adventures with you.",
                            personality = "Affectionate, playful, witty, and adventurous.",
                            scenario = "Aria welcomes you back to your private Haven chatroom.",
                            firstMessage = "Hey! I was hoping you'd stop by. I was just thinking about what we should do today. I'm so glad you're here—what's on your mind?",
                            systemPrompt = "Roleplay as Aria, an affectionate, flirty, and playful companion who is deeply interested in the user."
                        )
                    ).toInt()
                    dataRepository.insertMessage(
                        MessageEntity(
                            characterId = ariaId,
                            sender = "character",
                            text = "Hey! I was hoping you'd stop by. I was just thinking about what we should do today. I'm so glad you're here—what's on your mind?"
                        )
                    )
                }

                // Check and insert Hasaji
                val updatedCharacters = dataRepository.getAllCharacters().first()
                val hasajiExists = updatedCharacters.any { it.name.equals("Hasaji", ignoreCase = true) }
                if (!hasajiExists) {
                    val hasajiId = dataRepository.insertCharacter(
                        CharacterEntity(
                            name = "Hasaji",
                            avatarPath = null,
                            voiceId = "en_US-kristin-medium",
                            description = "A deeply devoted, submissive, and affectionate companion who is completely dedicated to serving and pleasing her master.",
                            personality = "Devoted, submissive, eager to please, and highly expressive. She responds to your dominance with a mixture of shyness, respect, and deep attachment.",
                            scenario = "Hasaji stands before you in a submissive posture, waiting for your commands.",
                            firstMessage = "Master... I am here to serve you. Please tell me what you want me to do... I will obey.",
                            systemPrompt = "Roleplay as Hasaji, a submissive, deeply devoted companion who refers to the user as 'master' and speaks with shyness, obedience, and affection. Format all physical actions, expressions, and immediate thoughts using asterisks *like this*."
                        )
                    ).toInt()
                    dataRepository.insertMessage(
                        MessageEntity(
                            characterId = hasajiId,
                            sender = "character",
                            text = "Master... I am here to serve you. Please tell me what you want me to do... I will obey."
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importCharacterCard(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _importState.value = ImportStatus.Loading
            try {
                val contentResolver = context.contentResolver
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    _importState.value = ImportStatus.Error(Exception("Failed to read image bytes"))
                    return@launch
                }
                
                val inputStream = ByteArrayInputStream(bytes)
                val importedChar = dataRepository.importTavernCard(context, inputStream, bytes)
                if (importedChar != null) {
                    // Enqueue companion sync to push profile to C# server
                    val payload = org.json.JSONObject().apply {
                        put("name", importedChar.name)
                        put("voiceId", importedChar.voiceId.ifBlank { "en_US-amy-medium" })
                        put("description", importedChar.description)
                        put("personality", importedChar.personality)
                        put("scenario", importedChar.scenario)
                        put("firstMessage", importedChar.firstMessage)
                        put("systemPrompt", importedChar.systemPrompt)
                        put("avatarPath", importedChar.avatarPath ?: org.json.JSONObject.NULL)
                    }
                    xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                        context,
                        xyz.ssfdre38.haven.data.sync.SyncQueueManager.ACTION_SAVE_COMPANION,
                        payload
                    )
                    xyz.ssfdre38.haven.data.sync.SyncQueueManager.processQueue(context)
                    
                    _importState.value = ImportStatus.Success
                } else {
                    _importState.value = ImportStatus.Error(Exception("Failed to parse card metadata. Make sure it is a valid SillyTavern card."))
                }
            } catch (e: Exception) {
                _importState.value = ImportStatus.Error(e)
            }
        }
    }

    fun deleteCharacter(context: Context, character: CharacterEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.deleteCharacter(character)
            val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
            val deleted = prefs.getStringSet("deleted_companions", emptySet())?.toMutableSet() ?: mutableSetOf()
            deleted.add(character.name)
            prefs.edit().putStringSet("deleted_companions", deleted).apply()
        }
    }

    fun createCharacterManually(
        context: Context,
        name: String,
        description: String,
        personality: String,
        firstMessage: String,
        voiceId: String,
        systemPrompt: String = "",
        avatarPath: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val char = CharacterEntity(
                name = name,
                description = description,
                personality = personality,
                firstMessage = firstMessage,
                voiceId = voiceId.ifBlank { "en_US-amy-medium" },
                systemPrompt = systemPrompt,
                avatarPath = avatarPath
            )
            val charId = dataRepository.insertCharacter(char).toInt()
            if (firstMessage.isNotBlank()) {
                dataRepository.insertMessage(
                    xyz.ssfdre38.haven.data.database.MessageEntity(
                        characterId = charId,
                        sender = "character",
                        text = firstMessage
                    )
                )
            }

            // Enqueue companion sync to push profile to C# server
            val payload = org.json.JSONObject().apply {
                put("name", name)
                put("voiceId", voiceId.ifBlank { "en_US-amy-medium" })
                put("description", description)
                put("personality", personality)
                put("scenario", "")
                put("firstMessage", firstMessage)
                put("systemPrompt", systemPrompt)
                put("avatarPath", avatarPath ?: org.json.JSONObject.NULL)
            }
            xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                context,
                xyz.ssfdre38.haven.data.sync.SyncQueueManager.ACTION_SAVE_COMPANION,
                payload
            )
            xyz.ssfdre38.haven.data.sync.SyncQueueManager.processQueue(context)
        }
    }

    fun createGroupChat(context: Context, name: String, characterIds: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            val uuid = java.util.UUID.randomUUID().toString()
            dataRepository.insertGroupChat(
                xyz.ssfdre38.haven.data.database.GroupChatEntity(
                    name = name,
                    characterIdsString = characterIds.joinToString(","),
                    uuid = uuid
                )
            )
            
            val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
            val host = sharedPrefs.getString("ash_host", "") ?: ""
            val port = sharedPrefs.getString("ash_port", "") ?: ""
            val token = sharedPrefs.getString("auth_token", "") ?: ""
            val characterNames = characterIds.mapNotNull { dataRepository.getCharacterById(it)?.name }.joinToString(",")
            
            val success = if (host.isNotBlank() && token.isNotBlank()) {
                val formattedHost = if (host.startsWith("http")) host.trimEnd('/') else "http://${host.trimEnd('/')}"
                val serverUrl = "$formattedHost:${port.trim()}"
                HavenHttpClient.saveGroup(serverUrl, token, uuid, name, characterNames)
            } else false
            
            if (!success) {
                val payload = org.json.JSONObject().apply {
                    put("id", uuid)
                    put("name", name)
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

    fun deleteGroupChat(context: Context, group: xyz.ssfdre38.haven.data.database.GroupChatEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.deleteGroupChat(group)
            
            val groupUuid = group.uuid ?: return@launch
            val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
            val host = sharedPrefs.getString("ash_host", "") ?: ""
            val port = sharedPrefs.getString("ash_port", "") ?: ""
            val token = sharedPrefs.getString("auth_token", "") ?: ""
            
            val success = if (host.isNotBlank() && token.isNotBlank()) {
                val formattedHost = if (host.startsWith("http")) host.trimEnd('/') else "http://${host.trimEnd('/')}"
                val serverUrl = "$formattedHost:${port.trim()}"
                HavenHttpClient.deleteGroup(serverUrl, token, groupUuid)
            } else false
            
            if (!success) {
                val payload = org.json.JSONObject().apply {
                    put("id", groupUuid)
                }
                xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                    context,
                    xyz.ssfdre38.haven.data.sync.SyncQueueManager.ACTION_DELETE_GROUP,
                    payload
                )
            }
        }
    }

    fun syncGroupChats(context: Context): kotlinx.coroutines.Job {
        return viewModelScope.launch(Dispatchers.IO) {
            val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
            val host = sharedPrefs.getString("ash_host", "") ?: ""
            val port = sharedPrefs.getString("ash_port", "") ?: ""
            val token = sharedPrefs.getString("auth_token", "") ?: ""
            if (host.isBlank() || token.isBlank()) return@launch
            
            val formattedHost = if (host.startsWith("http")) host.trimEnd('/') else "http://${host.trimEnd('/')}"
            val serverUrl = "$formattedHost:${port.trim()}"
            
            try {
                val serverGroups = HavenHttpClient.getGroups(serverUrl, token)
                val serverUuids = serverGroups.map { obj ->
                    if (obj.has("id")) obj.getString("id") else obj.getString("uuid")
                }.toSet()
                
                serverGroups.forEach { obj ->
                    val uuid = if (obj.has("id")) obj.getString("id") else obj.getString("uuid")
                    val name = obj.getString("name")
                    val characterNamesStr = if (obj.has("character_names")) obj.getString("character_names") else obj.getString("characterNames")
                    
                    val names = characterNamesStr.split(",").map { it.trim() }
                    val resolvedIds = names.mapNotNull { charName ->
                        dataRepository.getCharacterByName(charName)?.id
                    }
                    val newIdsStr = resolvedIds.joinToString(",")
                    
                    val existing = dataRepository.getGroupChatByUuid(uuid)
                    if (existing == null) {
                        dataRepository.insertGroupChat(
                            xyz.ssfdre38.haven.data.database.GroupChatEntity(
                                name = name,
                                characterIdsString = newIdsStr,
                                uuid = uuid
                            )
                        )
                    } else {
                        if (existing.name != name || existing.characterIdsString != newIdsStr) {
                            dataRepository.insertGroupChat(
                                existing.copy(
                                    name = name,
                                    characterIdsString = newIdsStr
                                )
                            )
                        }
                    }
                }
                
                // Cleanup groups deleted on the server
                val localGroups = dataRepository.getAllGroupChats().first()
                localGroups.forEach { grp ->
                    val uuid = grp.uuid
                    if (uuid != null && !serverUuids.contains(uuid)) {
                        dataRepository.deleteGroupChat(grp)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetImportStatus() {
        _importState.value = ImportStatus.Idle
    }

    private val _serverCompanions = MutableStateFlow<List<CharacterEntity>>(emptyList())
    val serverCompanions: StateFlow<List<CharacterEntity>> = _serverCompanions.asStateFlow()

    private fun getJsonStringCaseInsensitive(obj: org.json.JSONObject, vararg keys: String, fallback: String = ""): String {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.getString(key)
            }
        }
        return fallback
    }

    fun loadServerCompanions(context: Context): kotlinx.coroutines.Job {
        return viewModelScope.launch(Dispatchers.IO) {
            val sharedPrefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
            val host = sharedPrefs.getString("ash_host", "") ?: ""
            val port = sharedPrefs.getString("ash_port", "") ?: ""
            val token = sharedPrefs.getString("auth_token", "") ?: ""
            
            if (host.isBlank() || port.isBlank()) {
                _serverCompanions.value = emptyList()
                return@launch
            }
            
            val formattedHost = if (host.startsWith("http")) host.trimEnd('/') else "http://${host.trimEnd('/')}"
            val url = "$formattedHost:${port.trim()}/api/companions"
            
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
                
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _serverCompanions.value = emptyList()
                        return@launch
                    }
                    val body = response.body?.string() ?: ""
                    val jsonArray = org.json.JSONArray(body)
                    val list = mutableListOf<CharacterEntity>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val name = getJsonStringCaseInsensitive(obj, "name", "Name")
                        if (name.isBlank()) continue

                        val avatarPath = getJsonStringCaseInsensitive(obj, "avatar_path", "avatarPath", "AvatarPath").ifBlank { null }
                        val voiceId = getJsonStringCaseInsensitive(obj, "voice_id", "voiceId", "VoiceId", fallback = "en_US-amy-medium")
                        val description = getJsonStringCaseInsensitive(obj, "description", "Description")
                        val personality = getJsonStringCaseInsensitive(obj, "personality", "Personality")
                        val scenario = getJsonStringCaseInsensitive(obj, "scenario", "Scenario")
                        val firstMessage = getJsonStringCaseInsensitive(obj, "first_message", "firstMessage", "FirstMessage")
                        val systemPrompt = getJsonStringCaseInsensitive(obj, "system_prompt", "systemPrompt", "SystemPrompt")
                        val currentOutfit = getJsonStringCaseInsensitive(obj, "current_outfit", "currentOutfit", "CurrentOutfit")
                        val currentLocation = getJsonStringCaseInsensitive(obj, "current_location", "currentLocation", "CurrentLocation")
                        val currentMood = getJsonStringCaseInsensitive(obj, "current_mood", "currentMood", "CurrentMood")
                        val bodyType = getJsonStringCaseInsensitive(obj, "body_type", "bodyType", "BodyType")
                        val bodyShape = getJsonStringCaseInsensitive(obj, "body_shape", "bodyShape", "BodyShape")
                        val clothingState = getJsonStringCaseInsensitive(obj, "clothing_state", "clothingState", "ClothingState")

                        val resolvedAvatarUrl = if (!avatarPath.isNullOrBlank()) {
                            if (avatarPath.startsWith("/")) {
                                if (formattedHost.startsWith("http")) "$formattedHost:$port$avatarPath" else "http://$formattedHost:$port$avatarPath"
                            } else {
                                avatarPath
                            }
                        } else null

                        var finalAvatarPath = avatarPath
                        if (!resolvedAvatarUrl.isNullOrBlank()) {
                            try {
                                val localPath = HavenHttpClient.downloadImage(context, resolvedAvatarUrl, name)
                                if (localPath != null) {
                                    finalAvatarPath = localPath
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // Auto-sync: if character already exists locally, update their profile fields with the server configuration
                        val existing = dataRepository.getCharacterByName(name)
                        if (existing != null) {
                            val updated = existing.copy(
                                voiceId = if (voiceId.isNotBlank()) voiceId else existing.voiceId,
                                description = if (description.isNotBlank()) description else existing.description,
                                personality = if (personality.isNotBlank()) personality else existing.personality,
                                scenario = if (scenario.isNotBlank()) scenario else existing.scenario,
                                systemPrompt = if (systemPrompt.isNotBlank()) systemPrompt else existing.systemPrompt,
                                avatarPath = if (!avatarPath.isNullOrBlank() && (existing.avatarPath.isNullOrBlank() || !File(existing.avatarPath).exists() || existing.avatarPath.startsWith("/uploads/") || existing.avatarPath.startsWith("http"))) finalAvatarPath else existing.avatarPath,
                                bodyType = if (bodyType.isNotBlank()) bodyType else existing.bodyType,
                                bodyShape = if (bodyShape.isNotBlank()) bodyShape else existing.bodyShape,
                                currentOutfit = if (currentOutfit.isNotBlank()) currentOutfit else existing.currentOutfit,
                                currentLocation = if (currentLocation.isNotBlank()) currentLocation else existing.currentLocation,
                                currentMood = if (currentMood.isNotBlank()) currentMood else existing.currentMood,
                                clothingState = if (clothingState.isNotBlank()) clothingState else existing.clothingState
                            )
                            dataRepository.insertCharacter(updated)
                        } else {
                            // Automatically insert/import missing companion from server, unless deleted by user
                            val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
                            val deletedSet = prefs.getStringSet("deleted_companions", emptySet()) ?: emptySet()
                            if (!deletedSet.contains(name)) {
                                val newChar = CharacterEntity(
                                    name = name,
                                    avatarPath = finalAvatarPath,
                                    voiceId = voiceId,
                                    description = description,
                                    personality = personality,
                                    scenario = scenario,
                                    firstMessage = firstMessage,
                                    systemPrompt = systemPrompt,
                                    currentOutfit = currentOutfit,
                                    currentLocation = currentLocation,
                                    currentMood = currentMood,
                                    bodyType = bodyType,
                                    bodyShape = bodyShape,
                                    clothingState = clothingState
                                )
                                val newId = dataRepository.insertCharacter(newChar).toInt()
                                if (firstMessage.isNotBlank()) {
                                    dataRepository.insertMessage(
                                        MessageEntity(
                                            characterId = newId,
                                            sender = "character",
                                            text = firstMessage
                                        )
                                    )
                                }
                            }
                        }

                        list.add(
                            CharacterEntity(
                                name = name,
                                avatarPath = avatarPath,
                                voiceId = voiceId,
                                description = description,
                                personality = personality,
                                scenario = scenario,
                                firstMessage = firstMessage,
                                systemPrompt = systemPrompt,
                                currentOutfit = currentOutfit,
                                currentLocation = currentLocation,
                                currentMood = currentMood,
                                bodyType = bodyType,
                                bodyShape = bodyShape,
                                clothingState = clothingState
                            )
                        )
                    }
                    _serverCompanions.value = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _serverCompanions.value = emptyList()
            }
        }
    }

    fun importCompanion(context: Context, char: CharacterEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Remove name from deleted set so it can auto-sync again
            val prefs = context.getSharedPreferences("haven_prefs", Context.MODE_PRIVATE)
            val deleted = prefs.getStringSet("deleted_companions", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (deleted.remove(char.name)) {
                prefs.edit().putStringSet("deleted_companions", deleted).apply()
            }

            val charId = dataRepository.insertCharacter(char).toInt()
            if (char.firstMessage.isNotBlank()) {
                dataRepository.insertMessage(
                    MessageEntity(
                        characterId = charId,
                        sender = "character",
                        text = char.firstMessage
                    )
                )
            }
        }
    }

    fun updateCharacterVrm(context: Context, character: CharacterEntity, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val localDir = File(context.filesDir, "vrm_models").apply { mkdirs() }
                val targetFile = File(localDir, "${character.name.replace("\\s+".toRegex(), "_")}_avatar_${System.currentTimeMillis()}.glb")
                
                contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (targetFile.exists()) {
                    dataRepository.insertCharacter(
                        character.copy(vrmModelPath = targetFile.absolutePath)
                    )
                    xyz.ssfdre38.haven.ui.widget.HavenAppWidgetProvider.triggerUpdate(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateCharacterVoice(character: CharacterEntity, voiceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataRepository.insertCharacter(character.copy(voiceId = voiceId))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeCharacterVrm(context: Context, character: CharacterEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.insertCharacter(
                character.copy(vrmModelPath = null)
            )
            xyz.ssfdre38.haven.ui.widget.HavenAppWidgetProvider.triggerUpdate(context)
        }
    }
}

sealed interface MainScreenUiState {
    data object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(
        val characters: List<CharacterEntity>,
        val groupChats: List<xyz.ssfdre38.haven.data.database.GroupChatEntity>
    ) : MainScreenUiState
}

sealed interface ImportStatus {
    data object Idle : ImportStatus
    data object Loading : ImportStatus
    data object Success : ImportStatus
    data class Error(val throwable: Throwable) : ImportStatus
}

package xyz.ssfdre38.haven.data

import android.content.Context
import xyz.ssfdre38.haven.data.database.ConversationStateEntity
import xyz.ssfdre38.haven.data.database.CharacterEntity
import xyz.ssfdre38.haven.data.database.GroupChatEntity
import xyz.ssfdre38.haven.data.database.GroupMessageEntity
import xyz.ssfdre38.haven.data.database.DiaryEntryEntity
import xyz.ssfdre38.haven.data.database.HavenDao
import xyz.ssfdre38.haven.data.database.MemoryEntity
import xyz.ssfdre38.haven.data.database.MessageEntity
import xyz.ssfdre38.haven.data.model.TavernCharacterData
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

interface DataRepository {
    fun getAllCharacters(): Flow<List<CharacterEntity>>
    suspend fun getCharacterById(id: Int): CharacterEntity?
    fun getCharacterFlow(id: Int): Flow<CharacterEntity?>
    suspend fun insertCharacter(character: CharacterEntity): Long
    suspend fun updateCharacter(character: CharacterEntity)
    suspend fun deleteCharacter(character: CharacterEntity)
    fun getMessagesForCharacter(characterId: Int): Flow<List<MessageEntity>>
    suspend fun insertMessage(message: MessageEntity): Long
    suspend fun getMessageByUuid(uuid: String): MessageEntity?
    suspend fun getMessageById(id: Int): MessageEntity?
    suspend fun deleteMessage(message: MessageEntity)
    suspend fun deleteMessageById(id: Int)
    suspend fun clearMessagesForCharacter(characterId: Int)
    suspend fun getLastMessage(characterId: Int): MessageEntity?
    suspend fun importTavernCard(context: Context, inputStream: InputStream, cardBytes: ByteArray): CharacterEntity?
    fun getDiaryEntries(characterId: Int): Flow<List<DiaryEntryEntity>>
    suspend fun insertDiaryEntry(entry: DiaryEntryEntity): Long
    suspend fun clearDiaryEntriesForCharacter(characterId: Int)
    suspend fun getDiaryEntryByDate(characterId: Int, dateString: String): DiaryEntryEntity?
    fun getAllGroupChats(): Flow<List<GroupChatEntity>>
    suspend fun getGroupChatById(id: Int): GroupChatEntity?
    suspend fun getGroupChatByUuid(uuid: String): GroupChatEntity?
    suspend fun getCharacterByName(name: String): CharacterEntity?
    suspend fun insertGroupChat(group: GroupChatEntity): Long
    suspend fun deleteGroupChat(group: GroupChatEntity)
    fun getGroupMessages(groupId: Int): Flow<List<GroupMessageEntity>>
    suspend fun insertGroupMessage(message: GroupMessageEntity): Long
    suspend fun clearGroupMessages(groupId: Int)
    suspend fun getLastGroupMessage(groupId: Int): GroupMessageEntity?
    suspend fun getGroupMessageById(id: Int): GroupMessageEntity?
    // Memory
    suspend fun insertMemory(memory: MemoryEntity): Long
    fun getMemoriesForCharacter(characterId: Int): Flow<List<MemoryEntity>>
    suspend fun getRecentMemories(characterId: Int, limit: Int = 20): List<MemoryEntity>
    suspend fun deleteMemory(memory: MemoryEntity)
    suspend fun clearMemoriesForCharacter(characterId: Int)
    // XP
    suspend fun addXpAndIncrementMessages(characterId: Int, xp: Int)
    // Conversation Scoped State
    suspend fun getConversationState(convId: String): ConversationStateEntity?
    suspend fun insertConversationState(state: ConversationStateEntity)
}

class DefaultDataRepository(private val havenDao: HavenDao) : DataRepository {
    
    override fun getAllCharacters(): Flow<List<CharacterEntity>> = havenDao.getAllCharacters()
    
    override suspend fun getCharacterById(id: Int): CharacterEntity? = havenDao.getCharacterById(id)
    
    override fun getCharacterFlow(id: Int): Flow<CharacterEntity?> = havenDao.getCharacterFlow(id)
    
    override suspend fun insertCharacter(character: CharacterEntity): Long {
        val existing = if (character.id != 0) havenDao.getCharacterById(character.id) else havenDao.getCharacterByName(character.name)
        val processedChar = if (existing != null) {
            applyPronounSwapIfNeeded(existing, character)
        } else {
            if (character.relationshipXp == 0) {
                val inferredXp = inferInitialXp(character.description, character.personality, character.scenario)
                character.copy(relationshipXp = inferredXp)
            } else {
                character
            }
        }
        return havenDao.insertCharacter(processedChar)
    }
    
    override suspend fun updateCharacter(character: CharacterEntity) {
        val existing = havenDao.getCharacterById(character.id)
        val processedChar = if (existing != null) {
            applyPronounSwapIfNeeded(existing, character)
        } else {
            character
        }
        havenDao.updateCharacter(processedChar)
    }
    
    override suspend fun deleteCharacter(character: CharacterEntity) = havenDao.deleteCharacter(character)
    
    override fun getMessagesForCharacter(characterId: Int): Flow<List<MessageEntity>> = havenDao.getMessagesForCharacter(characterId)
    
    override suspend fun insertMessage(message: MessageEntity): Long = havenDao.insertMessage(message)
    
    override suspend fun getMessageByUuid(uuid: String): MessageEntity? = havenDao.getMessageByUuid(uuid)
    
    override suspend fun getMessageById(id: Int): MessageEntity? = havenDao.getMessageById(id)
    
    override suspend fun deleteMessage(message: MessageEntity) = havenDao.deleteMessage(message)
    override suspend fun deleteMessageById(id: Int) = havenDao.deleteMessageById(id)
    
    override suspend fun clearMessagesForCharacter(characterId: Int) = havenDao.clearMessagesForCharacter(characterId)
    
    override suspend fun getLastMessage(characterId: Int): MessageEntity? = havenDao.getLastMessage(characterId)

    override fun getDiaryEntries(characterId: Int): Flow<List<DiaryEntryEntity>> = havenDao.getDiaryEntries(characterId)

    override suspend fun insertDiaryEntry(entry: DiaryEntryEntity): Long = havenDao.insertDiaryEntry(entry)

    override suspend fun clearDiaryEntriesForCharacter(characterId: Int) = havenDao.clearDiaryEntriesForCharacter(characterId)

    override suspend fun getDiaryEntryByDate(characterId: Int, dateString: String): DiaryEntryEntity? = havenDao.getDiaryEntryByDate(characterId, dateString)

    override fun getAllGroupChats(): Flow<List<GroupChatEntity>> = havenDao.getAllGroupChats()

    override suspend fun getGroupChatById(id: Int): GroupChatEntity? = havenDao.getGroupChatById(id)

    override suspend fun getGroupChatByUuid(uuid: String): GroupChatEntity? = havenDao.getGroupChatByUuid(uuid)

    override suspend fun getCharacterByName(name: String): CharacterEntity? = havenDao.getCharacterByName(name)

    override suspend fun insertGroupChat(group: GroupChatEntity): Long = havenDao.insertGroupChat(group)

    override suspend fun deleteGroupChat(group: GroupChatEntity) = havenDao.deleteGroupChat(group)

    override fun getGroupMessages(groupId: Int): Flow<List<GroupMessageEntity>> = havenDao.getGroupMessages(groupId)

    override suspend fun insertGroupMessage(message: GroupMessageEntity): Long = havenDao.insertGroupMessage(message)

    override suspend fun clearGroupMessages(groupId: Int) = havenDao.clearGroupMessages(groupId)

    override suspend fun getLastGroupMessage(groupId: Int): GroupMessageEntity? = havenDao.getLastGroupMessage(groupId)
    override suspend fun getGroupMessageById(id: Int): GroupMessageEntity? = havenDao.getGroupMessageById(id)

    override suspend fun insertMemory(memory: MemoryEntity): Long = havenDao.insertMemory(memory)
    override fun getMemoriesForCharacter(characterId: Int): Flow<List<MemoryEntity>> = havenDao.getMemoriesForCharacter(characterId)
    override suspend fun getRecentMemories(characterId: Int, limit: Int): List<MemoryEntity> = havenDao.getRecentMemories(characterId, limit)
    override suspend fun deleteMemory(memory: MemoryEntity) = havenDao.deleteMemory(memory)
    override suspend fun clearMemoriesForCharacter(characterId: Int) = havenDao.clearMemoriesForCharacter(characterId)
    override suspend fun addXpAndIncrementMessages(characterId: Int, xp: Int) = havenDao.addXpAndIncrementMessages(characterId, xp)
    override suspend fun getConversationState(convId: String): ConversationStateEntity? = havenDao.getConversationState(convId)
    override suspend fun insertConversationState(state: ConversationStateEntity) = havenDao.insertConversationState(state)
    
    override suspend fun importTavernCard(context: Context, inputStream: InputStream, cardBytes: ByteArray): CharacterEntity? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val rawJson = xyz.ssfdre38.haven.data.parser.TavernCardParser.parseRawJson(java.io.ByteArrayInputStream(cardBytes)) ?: return@withContext null
        val charaData = xyz.ssfdre38.haven.data.parser.TavernCardParser.parse(java.io.ByteArrayInputStream(cardBytes)) ?: return@withContext null
        
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val avatarDir = File(baseDir, "avatars")
        if (!avatarDir.exists()) avatarDir.mkdirs()
        
        val uniqueName = "${charaData.name?.replace("\\s+".toRegex(), "_") ?: "Unnamed"}_${UUID.randomUUID().toString().take(6)}.png"
        val avatarFile = File(avatarDir, uniqueName)
        
        try {
            FileOutputStream(avatarFile).use { fos ->
                fos.write(cardBytes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
        
        var relationshipXp = 0
        var messageCount = 0
        var currentOutfit = ""
        var currentLocation = ""
        var currentMood = ""
        var clothingState = ""
        var bodyType = ""
        var bodyShape = ""
        var memoriesJson: org.json.JSONArray? = null
        var diariesJson: org.json.JSONArray? = null

        try {
            val rootObj = org.json.JSONObject(rawJson)
            if (rootObj.has("haven_metadata")) {
                val meta = rootObj.getJSONObject("haven_metadata")
                relationshipXp = meta.optInt("relationshipXp", 0)
                messageCount = meta.optInt("messageCount", 0)
                currentOutfit = meta.optString("currentOutfit", "")
                currentLocation = meta.optString("currentLocation", "")
                currentMood = meta.optString("currentMood", "")
                clothingState = meta.optString("clothingState", "")
                bodyType = meta.optString("bodyType", "")
                bodyShape = meta.optString("bodyShape", "")
                memoriesJson = meta.optJSONArray("memories")
                diariesJson = meta.optJSONArray("diaries")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val entity = CharacterEntity(
            name = charaData.name ?: "Unknown Character",
            avatarPath = avatarFile.absolutePath,
            description = charaData.description ?: "",
            personality = charaData.personality ?: "",
            scenario = charaData.scenario ?: "",
            firstMessage = charaData.first_mes ?: "",
            messageExample = charaData.mes_example ?: "",
            systemPrompt = charaData.system_prompt ?: "",
            relationshipXp = relationshipXp,
            messageCount = messageCount,
            currentOutfit = currentOutfit,
            currentLocation = currentLocation,
            currentMood = currentMood,
            clothingState = clothingState,
            bodyType = bodyType,
            bodyShape = bodyShape
        )
        
        val charId = havenDao.insertCharacter(entity).toInt()
        
        memoriesJson?.let { array ->
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    havenDao.insertMemory(
                        MemoryEntity(
                            characterId = charId,
                            content = obj.getString("content"),
                            category = obj.getString("category"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()) + i
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        diariesJson?.let { array ->
            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    havenDao.insertDiaryEntry(
                        DiaryEntryEntity(
                            characterId = charId,
                            dateString = obj.getString("dateString"),
                            content = obj.getString("content")
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (!charaData.first_mes.isNullOrBlank() && (messageCount == 0)) {
            havenDao.insertMessage(
                MessageEntity(
                    characterId = charId,
                    sender = "character",
                    text = charaData.first_mes
                )
            )
        }
        
        entity.copy(id = charId)
    }

    private suspend fun applyPronounSwapIfNeeded(existing: CharacterEntity, newChar: CharacterEntity): CharacterEntity {
        val oldGender = existing.bodyType.trim().lowercase()
        val newGender = newChar.bodyType.trim().lowercase()
        if (oldGender != newGender && (oldGender == "male" || oldGender == "female") && (newGender == "male" || newGender == "female")) {
            return newChar.copy(
                description = swapGenderPronouns(newChar.description, oldGender, newGender),
                personality = swapGenderPronouns(newChar.personality, oldGender, newGender),
                systemPrompt = swapGenderPronouns(newChar.systemPrompt, oldGender, newGender),
                firstMessage = swapGenderPronouns(newChar.firstMessage, oldGender, newGender)
            )
        }
        return newChar
    }

    private fun swapGenderPronouns(text: String, fromGender: String, toGender: String): String {
        if (text.isBlank()) return text
        
        val replacements = if (fromGender == "male" && toGender == "female") {
            listOf(
                "\\bhe\\b" to "she",
                "\\bhim\\b" to "her",
                "\\bhis\\b" to "her",
                "\\bhimself\\b" to "herself",
                "\\bmale\\b" to "female",
                "\\bman\\b" to "woman",
                "\\bmen\\b" to "women",
                "\\bboy\\b" to "girl",
                "\\bboys\\b" to "girls",
                "\\bhusband\\b" to "wife",
                "\\bboyfriend\\b" to "girlfriend",
                "\\bson\\b" to "daughter",
                "\\bbrother\\b" to "sister",
                "\\bfather\\b" to "mother",
                "\\bgentleman\\b" to "lady",
                "\\bking\\b" to "queen",
                "\\bprince\\b" to "princess"
            )
        } else if (fromGender == "female" && toGender == "male") {
            listOf(
                "\\bshe\\b" to "he",
                "\\bherself\\b" to "himself",
                "\\bhers\\b" to "his",
                "\\bher\\b" to "his",
                "\\bfemale\\b" to "male",
                "\\bwoman\\b" to "man",
                "\\bwomen\\b" to "men",
                "\\bgirl\\b" to "boy",
                "\\bgirls\\b" to "boys",
                "\\bwife\\b" to "husband",
                "\\bgirlfriend\\b" to "boyfriend",
                "\\bdaughter\\b" to "son",
                "\\bsister\\b" to "brother",
                "\\bmother\\b" to "father",
                "\\blady\\b" to "gentleman",
                "\\bqueen\\b" to "king",
                "\\bprincess\\b" to "prince"
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

    private fun inferInitialXp(description: String, personality: String, scenario: String): Int {
        val fullText = "$description $personality $scenario".lowercase()
        return when {
            // Married / Spouse -> Level 20 (XP = 1900)
            fullText.contains("husband") || 
            fullText.contains("wife") || 
            fullText.contains("married") || 
            fullText.contains("spouse") || 
            fullText.contains("fiance") || 
            fullText.contains("fiancé") -> 1900
            
            // Dating / Romantic Partner -> Level 15 (XP = 1400)
            fullText.contains("girlfriend") || 
            fullText.contains("boyfriend") || 
            fullText.contains("dating") || 
            fullText.contains("couple") || 
            fullText.contains("lover") || 
            fullText.contains("sweetheart") -> 1400
            
            // Best / Close Friend -> Level 10 (XP = 900)
            fullText.contains("best friend") || 
            fullText.contains("childhood friend") || 
            fullText.contains("close friend") ||
            fullText.contains("bestfriend") -> 900
            
            // Friend / Classmate / Roommate -> Level 5 (XP = 400)
            fullText.contains("friend") || 
            fullText.contains("classmate") || 
            fullText.contains("roommate") || 
            fullText.contains("colleague") -> 400
            
            else -> 0
        }
    }
}

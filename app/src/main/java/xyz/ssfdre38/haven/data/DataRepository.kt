package xyz.ssfdre38.haven.data

import android.content.Context
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
    suspend fun deleteMessage(message: MessageEntity)
    suspend fun clearMessagesForCharacter(characterId: Int)
    suspend fun getLastMessage(characterId: Int): MessageEntity?
    suspend fun importTavernCard(context: Context, inputStream: InputStream, cardBytes: ByteArray): Boolean
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
    // Memory
    suspend fun insertMemory(memory: MemoryEntity): Long
    fun getMemoriesForCharacter(characterId: Int): Flow<List<MemoryEntity>>
    suspend fun getRecentMemories(characterId: Int, limit: Int = 20): List<MemoryEntity>
    suspend fun deleteMemory(memory: MemoryEntity)
    suspend fun clearMemoriesForCharacter(characterId: Int)
    // XP
    suspend fun addXpAndIncrementMessages(characterId: Int, xp: Int)
}

class DefaultDataRepository(private val havenDao: HavenDao) : DataRepository {
    
    override fun getAllCharacters(): Flow<List<CharacterEntity>> = havenDao.getAllCharacters()
    
    override suspend fun getCharacterById(id: Int): CharacterEntity? = havenDao.getCharacterById(id)
    
    override fun getCharacterFlow(id: Int): Flow<CharacterEntity?> = havenDao.getCharacterFlow(id)
    
    override suspend fun insertCharacter(character: CharacterEntity): Long = havenDao.insertCharacter(character)
    
    override suspend fun updateCharacter(character: CharacterEntity) = havenDao.updateCharacter(character)
    
    override suspend fun deleteCharacter(character: CharacterEntity) = havenDao.deleteCharacter(character)
    
    override fun getMessagesForCharacter(characterId: Int): Flow<List<MessageEntity>> = havenDao.getMessagesForCharacter(characterId)
    
    override suspend fun insertMessage(message: MessageEntity): Long = havenDao.insertMessage(message)
    
    override suspend fun deleteMessage(message: MessageEntity) = havenDao.deleteMessage(message)
    
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

    override suspend fun insertMemory(memory: MemoryEntity): Long = havenDao.insertMemory(memory)
    override fun getMemoriesForCharacter(characterId: Int): Flow<List<MemoryEntity>> = havenDao.getMemoriesForCharacter(characterId)
    override suspend fun getRecentMemories(characterId: Int, limit: Int): List<MemoryEntity> = havenDao.getRecentMemories(characterId, limit)
    override suspend fun deleteMemory(memory: MemoryEntity) = havenDao.deleteMemory(memory)
    override suspend fun clearMemoriesForCharacter(characterId: Int) = havenDao.clearMemoriesForCharacter(characterId)
    override suspend fun addXpAndIncrementMessages(characterId: Int, xp: Int) = havenDao.addXpAndIncrementMessages(characterId, xp)
    
    override suspend fun importTavernCard(context: Context, inputStream: InputStream, cardBytes: ByteArray): Boolean {
        // Parse metadata
        val charaData = xyz.ssfdre38.haven.data.parser.TavernCardParser.parse(inputStream) ?: return false
        
        // Save the avatar image bytes locally
        val avatarDir = File(context.filesDir, "avatars")
        if (!avatarDir.exists()) avatarDir.mkdirs()
        
        val uniqueName = "${charaData.name?.replace("\\s+".toRegex(), "_") ?: "Unnamed"}_${UUID.randomUUID().toString().take(6)}.png"
        val avatarFile = File(avatarDir, uniqueName)
        
        try {
            FileOutputStream(avatarFile).use { fos ->
                fos.write(cardBytes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        
        // Save to DB
        val entity = CharacterEntity(
            name = charaData.name ?: "Unknown Character",
            avatarPath = avatarFile.absolutePath,
            description = charaData.description ?: "",
            personality = charaData.personality ?: "",
            scenario = charaData.scenario ?: "",
            firstMessage = charaData.first_mes ?: "",
            messageExample = charaData.mes_example ?: "",
            systemPrompt = charaData.system_prompt ?: ""
        )
        
        val charId = havenDao.insertCharacter(entity).toInt()
        
        // If character has a first message, insert it as the starter message
        if (!charaData.first_mes.isNullOrBlank()) {
            havenDao.insertMessage(
                MessageEntity(
                    characterId = charId,
                    sender = "character",
                    text = charaData.first_mes
                )
            )
        }
        
        return true
    }
}

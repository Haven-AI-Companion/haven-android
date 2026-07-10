package xyz.ssfdre38.haven.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HavenDao {

    @Query("SELECT * FROM characters ORDER BY name ASC")
    fun getAllCharacters(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getCharacterById(id: Int): CharacterEntity?

    @Query("SELECT * FROM characters WHERE id = :id")
    fun getCharacterFlow(id: Int): Flow<CharacterEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterEntity): Long

    @Update
    suspend fun updateCharacter(character: CharacterEntity)

    @Delete
    suspend fun deleteCharacter(character: CharacterEntity)

    @Query("SELECT * FROM messages WHERE characterId = :characterId ORDER BY timestamp ASC")
    fun getMessagesForCharacter(characterId: Int): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE characterId = :characterId")
    suspend fun clearMessagesForCharacter(characterId: Int)

    @Query("SELECT * FROM messages WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(characterId: Int): MessageEntity?

    @Query("SELECT * FROM diary_entries WHERE characterId = :characterId ORDER BY dateString DESC")
    fun getDiaryEntries(characterId: Int): Flow<List<DiaryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiaryEntry(entry: DiaryEntryEntity): Long

    @Query("SELECT * FROM diary_entries WHERE characterId = :characterId AND dateString = :dateString LIMIT 1")
    suspend fun getDiaryEntryByDate(characterId: Int, dateString: String): DiaryEntryEntity?

    @Query("SELECT * FROM group_chats ORDER BY name ASC")
    fun getAllGroupChats(): Flow<List<GroupChatEntity>>

    @Query("SELECT * FROM group_chats WHERE id = :id")
    suspend fun getGroupChatById(id: Int): GroupChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupChat(group: GroupChatEntity): Long

    @Delete
    suspend fun deleteGroupChat(group: GroupChatEntity)

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getGroupMessages(groupId: Int): Flow<List<GroupMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMessage(message: GroupMessageEntity): Long

    @Query("DELETE FROM group_messages WHERE groupId = :groupId")
    suspend fun clearGroupMessages(groupId: Int)

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastGroupMessage(groupId: Int): GroupMessageEntity?

    // --- Memory / Long-term Recall ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Query("SELECT * FROM memories WHERE characterId = :characterId ORDER BY createdAt DESC")
    fun getMemoriesForCharacter(characterId: Int): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE characterId = :characterId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMemories(characterId: Int, limit: Int = 20): List<MemoryEntity>

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE characterId = :characterId")
    suspend fun clearMemoriesForCharacter(characterId: Int)

    // --- Relationship XP ---
    @Query("UPDATE characters SET relationshipXp = relationshipXp + :xp, messageCount = messageCount + 1 WHERE id = :characterId")
    suspend fun addXpAndIncrementMessages(characterId: Int, xp: Int)
}

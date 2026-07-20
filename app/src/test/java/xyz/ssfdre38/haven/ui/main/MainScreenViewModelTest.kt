package xyz.ssfdre38.haven.ui.main

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.*
import java.io.InputStream

class MainScreenViewModelTest {
  @Test
  fun testLoadingState() {
    val viewModel = MainScreenViewModel(FakeMyModelRepository())
    // Placeholder test
  }
}

private class FakeMyModelRepository : DataRepository {
    override fun getAllCharacters(): Flow<List<CharacterEntity>> = flow { emit(emptyList()) }
    override suspend fun getCharacterById(id: Int): CharacterEntity? = null
    override fun getCharacterFlow(id: Int): Flow<CharacterEntity?> = flow { emit(null) }
    override suspend fun insertCharacter(character: CharacterEntity): Long = 0L
    override suspend fun updateCharacter(character: CharacterEntity) {}
    override suspend fun deleteCharacter(character: CharacterEntity) {}
    override fun getMessagesForCharacter(characterId: Int): Flow<List<MessageEntity>> = flow { emit(emptyList()) }
    override suspend fun insertMessage(message: MessageEntity): Long = 0L
    override suspend fun getMessageByUuid(uuid: String): MessageEntity? = null
    override suspend fun getMessageById(id: Int): MessageEntity? = null
    override suspend fun deleteMessage(message: MessageEntity) {}
    override suspend fun deleteMessageById(id: Int) {}
    override suspend fun clearMessagesForCharacter(characterId: Int) {}
    override suspend fun getLastMessage(characterId: Int): MessageEntity? = null
    override suspend fun importTavernCard(context: Context, inputStream: InputStream, cardBytes: ByteArray): CharacterEntity? = null
    override fun getDiaryEntries(characterId: Int): Flow<List<DiaryEntryEntity>> = flow { emit(emptyList()) }
    override suspend fun insertDiaryEntry(entry: DiaryEntryEntity): Long = 0L
    override suspend fun clearDiaryEntriesForCharacter(characterId: Int) {}
    override suspend fun getDiaryEntryByDate(characterId: Int, dateString: String): DiaryEntryEntity? = null
    override fun getAllGroupChats(): Flow<List<GroupChatEntity>> = flow { emit(emptyList()) }
    override suspend fun getGroupChatById(id: Int): GroupChatEntity? = null
    override suspend fun getGroupChatByUuid(uuid: String): GroupChatEntity? = null
    override suspend fun getCharacterByName(name: String): CharacterEntity? = null
    override suspend fun insertGroupChat(group: GroupChatEntity): Long = 0L
    override suspend fun deleteGroupChat(group: GroupChatEntity) {}
    override fun getGroupMessages(groupId: Int): Flow<List<GroupMessageEntity>> = flow { emit(emptyList()) }
    override suspend fun insertGroupMessage(message: GroupMessageEntity): Long = 0L
    override suspend fun clearGroupMessages(groupId: Int) {}
    override suspend fun getLastGroupMessage(groupId: Int): GroupMessageEntity? = null
    override suspend fun getGroupMessageById(id: Int): GroupMessageEntity? = null
    override suspend fun insertMemory(memory: MemoryEntity): Long = 0L
    override fun getMemoriesForCharacter(characterId: Int): Flow<List<MemoryEntity>> = flow { emit(emptyList()) }
    override suspend fun getRecentMemories(characterId: Int, limit: Int): List<MemoryEntity> = emptyList()
    override suspend fun deleteMemory(memory: MemoryEntity) {}
    override suspend fun clearMemoriesForCharacter(characterId: Int) {}
    override suspend fun addXpAndIncrementMessages(characterId: Int, xp: Int) {}
}

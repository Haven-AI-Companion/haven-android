package xyz.ssfdre38.haven

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import xyz.ssfdre38.haven.data.DefaultDataRepository
import xyz.ssfdre38.haven.data.database.AppDatabase
import xyz.ssfdre38.haven.ui.chat.ChatScreen
import xyz.ssfdre38.haven.ui.main.MainScreen
import xyz.ssfdre38.haven.ui.settings.SettingsScreen
import xyz.ssfdre38.haven.ui.voice.VoiceCallScreen

@Composable
fun MainNavigation(
    startCharacterId: Int? = null,
    startVoiceCall: Boolean = false
) {
    val context = LocalContext.current.applicationContext
    val database = AppDatabase.getInstance(context)
    val repository = DefaultDataRepository(database.havenDao())

    val backStack = rememberNavBackStack(Main)

    // Handle deep navigation when launched from status bar notification or wake word
    LaunchedEffect(startCharacterId) {
        if (startCharacterId != null) {
            backStack.add(Chat(startCharacterId))
            if (startVoiceCall) {
                backStack.add(VoiceCall(startCharacterId))
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        },
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    repository = repository,
                    onNavigate = { navKey -> backStack.add(navKey) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Chat> { chatKey ->
                ChatScreen(
                    characterId = chatKey.characterId,
                    repository = repository,
                    onBackClick = { backStack.removeLastOrNull() },
                    onGalleryClick = { backStack.add(Gallery(chatKey.characterId)) },
                    onDiaryClick = { name -> backStack.add(Diary(chatKey.characterId, name)) },
                    onVoiceCallClick = { backStack.add(VoiceCall(chatKey.characterId)) },
                    onMemoryVaultClick = { backStack.add(MemoryVault(chatKey.characterId)) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Settings> {
                SettingsScreen(
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Gallery> { galleryKey ->
                xyz.ssfdre38.haven.ui.gallery.GalleryScreen(
                    characterId = galleryKey.characterId,
                    repository = repository,
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Diary> { diaryKey ->
                xyz.ssfdre38.haven.ui.diary.DiaryScreen(
                    characterId = diaryKey.characterId,
                    characterName = diaryKey.characterName,
                    repository = repository,
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<GroupChat> { groupKey ->
                xyz.ssfdre38.haven.ui.group.GroupChatScreen(
                    groupId = groupKey.groupId,
                    repository = repository,
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<VoiceCall> { voiceKey ->
                VoiceCallScreen(
                    characterId = voiceKey.characterId,
                    repository = repository,
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<MemoryVault> { vaultKey ->
                xyz.ssfdre38.haven.ui.memory.MemoryVaultScreen(
                    characterId = vaultKey.characterId,
                    repository = repository,
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

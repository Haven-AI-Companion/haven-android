package xyz.ssfdre38.haven.ui.diary

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xyz.ssfdre38.haven.data.DataRepository
import xyz.ssfdre38.haven.data.database.DiaryEntryEntity
import xyz.ssfdre38.haven.data.network.HavenHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiaryViewModel(
    private val repository: DataRepository,
    private val characterId: Int
) : ViewModel() {

    val diaryEntries: StateFlow<List<DiaryEntryEntity>> = repository.getDiaryEntries(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun generateTodayEntry(context: Context, serverUrl: String, token: String, characterName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isGenerating.value = true
            _error.value = null

            val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // 1. Check if today's entry already exists
            val existing = repository.getDiaryEntryByDate(characterId, todayDateStr)
            if (existing != null) {
                _isGenerating.value = false
                _error.value = "Today's diary entry has already been written!"
                return@launch
            }

            // 2. Fetch recent messages
            val allMessages = repository.getMessagesForCharacter(characterId).first()
            
            // Get the last 30 messages to capture today's conversation context
            val recentMessages = allMessages.takeLast(30)
            if (recentMessages.isEmpty()) {
                _isGenerating.value = false
                _error.value = "You haven't chatted with $characterName today yet!"
                return@launch
            }

            val chatLogs = recentMessages.joinToString("\n") { msg ->
                val sender = if (msg.sender == "user") "User" else characterName
                val thoughtRegex = "<\\s*thought\\s*>(.*)<\\s*/\\s*thought\\s*>".toRegex(RegexOption.DOT_MATCHES_ALL)
                val cleanText = msg.text.replace(thoughtRegex, "").trim()
                "$sender: $cleanText"
            }

            // 3. Assemble Prompt
            val diaryPrompt = """
                [System Instruction: You are $characterName. Today is $todayDateStr. Below is the log of your conversations with the User today. Write a private, secret journal/diary entry from your perspective about your conversations with the User, how you felt, what you thought, and what plans you have. Write it in the first person ("I"). Keep it intimate, reflective, and detailed. Do NOT include any thoughts, system tags, or bracketed states. Only output the raw diary entry text itself.]
                
                Chat Logs:
                $chatLogs
            """.trimIndent()

            val streamBuffer = StringBuilder()

            HavenHttpClient.streamChat(
                serverUrl = serverUrl,
                prompt = diaryPrompt,
                token = token,
                onToken = { token ->
                    streamBuffer.append(token).append(" ")
                },
                onComplete = {
                    val finalContent = streamBuffer.toString().replace("\\s+".toRegex(), " ").trim()
                    if (finalContent.isNotBlank()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            repository.insertDiaryEntry(
                                DiaryEntryEntity(
                                    characterId = characterId,
                                    dateString = todayDateStr,
                                    content = finalContent
                                )
                            )
                            val success = HavenHttpClient.saveDiary(serverUrl, token, characterName, todayDateStr, finalContent)
                            if (!success) {
                                val payload = org.json.JSONObject().apply {
                                    put("companion_name", characterName)
                                    put("date_string", todayDateStr)
                                    put("content", finalContent)
                                }
                                xyz.ssfdre38.haven.data.sync.SyncQueueManager.enqueue(
                                    context,
                                    xyz.ssfdre38.haven.data.sync.SyncQueueManager.ACTION_SAVE_DIARY,
                                    payload
                                )
                            }
                            _isGenerating.value = false
                        }
                    } else {
                        _isGenerating.value = false
                        _error.value = "Received empty response from companion."
                    }
                },
                onFailure = { e ->
                    _isGenerating.value = false
                    _error.value = "Failed to generate: ${e.message}"
                }
            )
        }
    }

    fun syncDiaries(serverUrl: String, token: String, characterName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val serverDiaries = HavenHttpClient.getDiaries(serverUrl, token, characterName)
            if (serverDiaries.isNotEmpty()) {
                repository.clearDiaryEntriesForCharacter(characterId)
                serverDiaries.forEach { obj ->
                    repository.insertDiaryEntry(
                        DiaryEntryEntity(
                            characterId = characterId,
                            dateString = if (obj.has("date_string")) obj.getString("date_string") else obj.getString("dateString"),
                            content = obj.getString("content")
                        )
                    )
                }
            }
        }
    }
}

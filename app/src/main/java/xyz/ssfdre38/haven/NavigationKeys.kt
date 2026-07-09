package xyz.ssfdre38.haven

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data class Chat(val characterId: Int) : NavKey
@Serializable data object Settings : NavKey
@Serializable data class Gallery(val characterId: Int) : NavKey
@Serializable data class Diary(val characterId: Int, val characterName: String) : NavKey
@Serializable data class GroupChat(val groupId: Int) : NavKey
@Serializable data class VoiceCall(val characterId: Int) : NavKey
@Serializable data class MemoryVault(val characterId: Int) : NavKey



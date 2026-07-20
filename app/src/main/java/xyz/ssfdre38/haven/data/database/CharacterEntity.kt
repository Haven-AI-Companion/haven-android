package xyz.ssfdre38.haven.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val avatarPath: String? = null,
    val voiceId: String = "en_US-amy-medium",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val messageExample: String = "",
    val systemPrompt: String = "",
    val currentLocation: String = "",
    val currentOutfit: String = "",
    val currentMood: String = "",
    val relationshipXp: Int = 0,
    val messageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val conversationId: String? = null,
    val vrmModelPath: String? = null,
    val bodyType: String = "",
    val bodyShape: String = "",
    val clothingState: String = "",
    val chatWallpaperPath: String? = null
)

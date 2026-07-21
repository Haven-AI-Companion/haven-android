package xyz.ssfdre38.haven.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_states")
data class ConversationStateEntity(
    @PrimaryKey val convId: String,
    val location: String? = null,
    val outfit: String? = null,
    val mood: String? = null,
    val clothingState: String? = null,
    val bodyType: String? = null,
    val bodyShape: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

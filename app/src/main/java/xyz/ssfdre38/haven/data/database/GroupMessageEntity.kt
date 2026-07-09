package xyz.ssfdre38.haven.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_messages")
data class GroupMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: Int,
    val sender: String, // "user" or "character"
    val characterId: Int?, // Null if sender == "user", otherwise the character ID of the speaker
    val text: String,
    val imagePath: String? = null,
    val audioPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

package xyz.ssfdre38.haven.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_chats")
data class GroupChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val characterIdsString: String, // Comma-separated list of IDs, e.g., "1,2"
    val createdAt: Long = System.currentTimeMillis()
)

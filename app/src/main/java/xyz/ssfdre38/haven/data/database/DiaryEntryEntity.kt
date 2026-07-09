package xyz.ssfdre38.haven.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_entries")
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val characterId: Int,
    val dateString: String, // Format: YYYY-MM-DD
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

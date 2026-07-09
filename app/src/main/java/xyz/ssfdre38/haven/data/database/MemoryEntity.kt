package xyz.ssfdre38.haven.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["characterId"])]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val characterId: Int,
    val content: String,           // The extracted memory fact (e.g. "User's name is Alex")
    val category: String = "general", // e.g. "personal", "preference", "event", "general"
    val createdAt: Long = System.currentTimeMillis()
)

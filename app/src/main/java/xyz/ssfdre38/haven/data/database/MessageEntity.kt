package xyz.ssfdre38.haven.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
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
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val characterId: Int,
    val sender: String, // "user" or "character"
    val text: String,
    val imagePath: String? = null,
    val audioPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

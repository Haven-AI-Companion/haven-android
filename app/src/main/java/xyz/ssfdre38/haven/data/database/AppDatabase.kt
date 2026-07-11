package xyz.ssfdre38.haven.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CharacterEntity::class, MessageEntity::class, DiaryEntryEntity::class, GroupChatEntity::class, GroupMessageEntity::class, MemoryEntity::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun havenDao(): HavenDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "haven_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

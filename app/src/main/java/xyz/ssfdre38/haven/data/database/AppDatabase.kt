package xyz.ssfdre38.haven.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CharacterEntity::class, MessageEntity::class, DiaryEntryEntity::class, GroupChatEntity::class, GroupMessageEntity::class, MemoryEntity::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun havenDao(): HavenDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN messageUuid TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "haven_database"
                )
                .addMigrations(MIGRATION_10_11)
                // Removed fallbackToDestructiveMigration to prevent silent, catastrophic loss of local companion logs, XP, and diaries on schema updates.
                // Write explicit migrations when schema changes are introduced.
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

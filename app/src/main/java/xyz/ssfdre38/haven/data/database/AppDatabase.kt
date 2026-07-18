package xyz.ssfdre38.haven.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CharacterEntity::class, MessageEntity::class, DiaryEntryEntity::class, GroupChatEntity::class, GroupMessageEntity::class, MemoryEntity::class], version = 13, exportSchema = false)
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

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE group_chats ADD COLUMN scenario TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE group_chats ADD COLUMN systemPrompt TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE group_chats ADD COLUMN backdropType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE group_chats ADD COLUMN ambientType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE group_chats ADD COLUMN banterDelay INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "haven_database"
                )
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8, 9)
                // Removed fallbackToDestructiveMigration to prevent silent, catastrophic loss of local companion logs, XP, and diaries on schema updates.
                // Write explicit migrations when schema changes are introduced.
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

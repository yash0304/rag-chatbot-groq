package com.mindquest.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class,
        XpEventEntity::class,
        QuestEntity::class,
        HabitEntity::class,
        HabitCheckinEntity::class,
        GoalEntity::class,
        MilestoneEntity::class,
        AchievementEntity::class,
        SkillEntity::class,
        CollectibleEntity::class,
        DocumentEntity::class,
        ChunkEntity::class,
        ChatMessageEntity::class,
        WeeklyReviewEntity::class,
        NoteEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class MindQuestDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun xpEventDao(): XpEventDao
    abstract fun questDao(): QuestDao
    abstract fun habitDao(): HabitDao
    abstract fun goalDao(): GoalDao
    abstract fun catalogDao(): CatalogDao
    abstract fun documentDao(): DocumentDao
    abstract fun chatDao(): ChatDao
    abstract fun reviewDao(): ReviewDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var instance: MindQuestDatabase? = null

        /** v1→v2: add documents + chunks (Phase 3). Additive, preserves all existing data. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `documents` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`filename` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`error` TEXT, `summary` TEXT, `domain` TEXT, `tagsCsv` TEXT NOT NULL, " +
                        "`ocrUsed` INTEGER NOT NULL, `charCount` INTEGER NOT NULL, `chunkCount` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chunks` (`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, " +
                        "`seq` INTEGER NOT NULL, `text` TEXT NOT NULL, `location` TEXT, `vectorCsv` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chunks_documentId` ON `chunks` (`documentId`)")
            }
        }

        /** v2→v3: add chat_messages + weekly_reviews (Phase 4). Additive. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` TEXT NOT NULL, `role` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, `citationsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_createdAt` ON `chat_messages` (`createdAt`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weekly_reviews` (`weekStart` TEXT NOT NULL, `statsJson` TEXT NOT NULL, " +
                        "`narrative` TEXT NOT NULL, `suggestionsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`weekStart`))",
                )
            }
        }

        /** v3→v4: add the quick-capture notes inbox. Additive — existing data preserved. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL, `remindAt` INTEGER, `questId` TEXT, `docId` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_createdAt` ON `notes` (`createdAt`)")
            }
        }

        /** v5→v6: quests gain the same life category as notes. Additive — data preserved. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `quests` ADD COLUMN `category` TEXT")
            }
        }

        /** v4→v5: notes gain a life category (travel/shopping/...). Additive — data preserved. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `category` TEXT")
            }
        }

        fun get(context: Context): MindQuestDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MindQuestDatabase::class.java,
                    "mindquest.db",
                )
                    // Real additive migrations preserve data on upgrade (MQ-20). Destructive only
                    // as a last resort on downgrade, which shouldn't happen in normal use.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
    }
}

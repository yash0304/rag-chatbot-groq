package com.mindquest.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
        FolderEntity::class,
        AttachmentEntity::class,
        NoteVectorEntity::class,
        GoalProgressEntity::class,
        GoalCheckpointEntity::class,
    ],
    version = 14,
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
    abstract fun folderDao(): FolderDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun noteVectorDao(): NoteVectorDao

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

        /**
         * Quests became Inbox items. Each active quest turns into a note with the same id, its
         * deadline as the reminder and a star if it was hard or epic; drafts are dropped. Run by
         * the v13→v14 migration and again after restoring a backup made before it.
         */
        fun foldQuestsIntoInbox(db: SupportSQLiteDatabase) {
            // Notes that were promoted to a hard or epic quest keep that weight as a star.
            db.execSQL(
                "UPDATE `notes` SET `starred` = 1 WHERE `questId` IN " +
                    "(SELECT `id` FROM `quests` WHERE `status` = 'active' AND `difficulty` IN ('hard', 'epic'))",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `notes` (`id`, `text`, `done`, `remindAt`, `questId`, `docId`, " +
                    "`createdAt`, `category`, `categoryLocked`, `folderId`, `updatedAt`, `repeat`, " +
                    "`starred`, `completedAt`) " +
                    "SELECT `id`, `title`, 0, `dueAt`, `id`, NULL, `createdAt`, `category`, `categoryLocked`, " +
                    "NULL, NULL, NULL, CASE WHEN `difficulty` IN ('hard', 'epic') THEN 1 ELSE 0 END, NULL " +
                    "FROM `quests` WHERE `status` = 'active' AND `id` NOT IN " +
                    "(SELECT `questId` FROM `notes` WHERE `questId` IS NOT NULL)",
            )
            db.execSQL("UPDATE `quests` SET `status` = 'migrated' WHERE `status` = 'active'")
            db.execSQL("UPDATE `quests` SET `status` = 'abandoned' WHERE `status` = 'draft'")
        }

        /**
         * v13→v14: the Inbox becomes the one to-do list. Notes gain a star (a bigger task,
         * more XP) and a first-completed stamp. Every active quest becomes an Inbox item —
         * same id, title, category and deadline, starred if it was hard or epic — unless it
         * already has one, because it was promoted from a note in the first place. Drafts the
         * Questmaster suggested and nobody accepted are set aside. Completed quests stay as
         * they are: they are the history the XP and achievements were earned from.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `starred` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `completedAt` INTEGER")
                foldQuestsIntoInbox(db)
            }
        }

        /** v12→v13: checkpoints — mini goals inside a target goal. Additive. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goal_checkpoints` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, " +
                        "`value` REAL NOT NULL, `dueDate` TEXT NOT NULL, `planned` INTEGER NOT NULL, " +
                        "`reachedAt` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_checkpoints_goalId` ON `goal_checkpoints` (`goalId`)")
            }
        }

        /**
         * v11→v12: goals can be targets — a number by a date — with a history of readings and
         * a check-in nudge. Additive: existing story arcs are untouched, new columns are null.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `goals` ADD COLUMN `targetValue` REAL")
                db.execSQL("ALTER TABLE `goals` ADD COLUMN `changeValue` REAL")
                db.execSQL("ALTER TABLE `goals` ADD COLUMN `unit` TEXT")
                db.execSQL("ALTER TABLE `goals` ADD COLUMN `deadline` TEXT")
                db.execSQL("ALTER TABLE `goals` ADD COLUMN `checkinMinuteOfDay` INTEGER")
                db.execSQL("ALTER TABLE `goals` ADD COLUMN `checkinCadence` TEXT")
                db.execSQL("ALTER TABLE `goals` ADD COLUMN `updatedAt` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goal_progress` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, " +
                        "`value` REAL NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_progress_goalId` ON `goal_progress` (`goalId`)")
            }
        }

        /**
         * v10→v11: repeating reminders on notes, and the vectors that let search find a
         * note by what it means. Additive; the vectors fill in on their own afterwards.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `repeat` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_vectors` (`noteId` TEXT NOT NULL, " +
                        "`vectorCsv` TEXT NOT NULL, `textHash` INTEGER NOT NULL, " +
                        "`embedder` TEXT NOT NULL, PRIMARY KEY(`noteId`))",
                )
            }
        }

        /**
         * v9→v10: photos on quests and missions, a target for a mission to count down to,
         * and an edited-at stamp on everything the user can now change. Additive.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (`id` TEXT NOT NULL, " +
                        "`ownerKind` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `path` TEXT NOT NULL, " +
                        "`caption` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_attachments_ownerKind_ownerId` " +
                        "ON `attachments` (`ownerKind`, `ownerId`)",
                )
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `updatedAt` INTEGER")
                db.execSQL("ALTER TABLE `quests` ADD COLUMN `updatedAt` INTEGER")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `updatedAt` INTEGER")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `targetNote` TEXT")
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `targetDate` TEXT")
            }
        }

        /** v8→v9: user-made folders, and the note's link to one. Additive. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`icon` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_createdAt` ON `folders` (`createdAt`)")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `folderId` TEXT")
            }
        }

        /** v7→v8: daily missions gain a time of day to nudge at. Additive. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `remindMinuteOfDay` INTEGER")
            }
        }

        /**
         * v6→v7: remember which categories the user set by hand, so improving the classifier
         * can re-sort its own guesses without ever overwriting a human decision.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `categoryLocked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `quests` ADD COLUMN `categoryLocked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `domainLocked` INTEGER NOT NULL DEFAULT 0")
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

        const val NAME = "mindquest.db"

        fun get(context: Context): MindQuestDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(app: Context): MindQuestDatabase {
            // Bring the file into the state the user chose (encrypted or not) before Room
            // opens it. With encryption off — the default — this reads one flag and returns.
            val passphrase = DbEncryption.prepare(app, app.getDatabasePath(NAME))
            return Room.databaseBuilder(app, MindQuestDatabase::class.java, NAME)
                // Real additive migrations preserve data on upgrade (MQ-20). Destructive only
                // as a last resort on downgrade, which shouldn't happen in normal use.
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .fallbackToDestructiveMigrationOnDowngrade()
                .apply { passphrase?.let { openHelperFactory(SupportOpenHelperFactory(it)) } }
                .build()
        }
    }
}

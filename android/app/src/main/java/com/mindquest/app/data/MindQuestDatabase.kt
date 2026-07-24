package com.mindquest.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    ],
    version = 3,
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

    companion object {
        @Volatile
        private var instance: MindQuestDatabase? = null

        fun get(context: Context): MindQuestDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MindQuestDatabase::class.java,
                    "mindquest.db",
                )
                    // PRE-RELEASE ONLY: schema still evolving across phases. Replace with
                    // real migrations before the first release (DECISIONS.md, skill rule).
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}

package com.mindquest.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface XpEventDao {
    @Insert
    suspend fun insert(event: XpEventEntity)

    @Query("SELECT * FROM xp_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<XpEventEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE createdAt >= :since")
    suspend fun sumSince(since: Long): Long

    @Query("SELECT COUNT(*) FROM xp_events WHERE kind = :kind")
    suspend fun countKind(kind: String): Int

    @Query("SELECT * FROM xp_events WHERE createdAt >= :since ORDER BY createdAt")
    suspend fun since(since: Long): List<XpEventEntity>
}

@Dao
interface QuestDao {
    @Upsert
    suspend fun upsert(quest: QuestEntity)

    @Query("SELECT * FROM quests WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QuestEntity>>

    @Query("SELECT * FROM quests WHERE id = :id")
    suspend fun get(id: String): QuestEntity?

    @Query("SELECT COUNT(*) FROM quests WHERE status = 'completed'")
    suspend fun completedCount(): Int

    @Query("SELECT COUNT(*) FROM quests WHERE status = 'completed' AND difficulty = 'epic'")
    suspend fun epicCompletedCount(): Int
}

@Dao
interface HabitDao {
    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM habits ORDER BY createdAt")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: String): HabitEntity?

    @Query("SELECT MAX(streak) FROM habits")
    suspend fun maxStreak(): Int?

    @Query("SELECT MAX(bestStreak) FROM habits")
    suspend fun maxBestStreak(): Int?

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckin(checkin: HabitCheckinEntity): Long

    @Query("SELECT COUNT(*) FROM habit_checkins WHERE habitId = :habitId AND date = :date")
    suspend fun checkinExists(habitId: String, date: String): Int

    @Query("SELECT COUNT(*) FROM habit_checkins")
    suspend fun totalCheckins(): Int
}

@Dao
interface GoalDao {
    @Upsert
    suspend fun upsertGoal(goal: GoalEntity)

    @Upsert
    suspend fun upsertMilestone(milestone: MilestoneEntity)

    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoal(id: String): GoalEntity?

    @Query("SELECT * FROM milestones WHERE goalId = :goalId ORDER BY seq")
    fun observeMilestones(goalId: String): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones ORDER BY goalId, seq")
    fun observeAllMilestones(): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones WHERE goalId = :goalId ORDER BY seq")
    suspend fun milestonesOf(goalId: String): List<MilestoneEntity>

    @Query("SELECT * FROM milestones WHERE id = :id")
    suspend fun getMilestone(id: String): MilestoneEntity?
}

@Dao
interface DocumentDao {
    @Upsert
    suspend fun upsertDocument(doc: DocumentEntity)

    @Insert
    suspend fun insertChunks(chunks: List<ChunkEntity>)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("DELETE FROM chunks WHERE documentId = :documentId")
    suspend fun deleteChunksOf(documentId: String)

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE status = 'ready'")
    suspend fun readyDocuments(): List<DocumentEntity>

    @Query("SELECT * FROM chunks")
    suspend fun allChunks(): List<ChunkEntity>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocument(id: String): DocumentEntity?

    @Query("SELECT COUNT(*) FROM documents WHERE status = 'ready'")
    suspend fun readyCount(): Int

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun anyCount(): Int

    @Query("SELECT COUNT(DISTINCT domain) FROM documents WHERE status = 'ready' AND domain IS NOT NULL")
    suspend fun domainCount(): Int
}

@Dao
interface CatalogDao {
    @Upsert
    suspend fun upsertAchievements(items: List<AchievementEntity>)

    @Update
    suspend fun updateAchievement(item: AchievementEntity)

    @Query("SELECT * FROM achievements")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun achievements(): List<AchievementEntity>

    @Query("SELECT COUNT(*) FROM achievements")
    suspend fun achievementCount(): Int

    @Upsert
    suspend fun upsertSkills(items: List<SkillEntity>)

    @Update
    suspend fun updateSkill(item: SkillEntity)

    @Query("SELECT * FROM skills ORDER BY tree, tier")
    fun observeSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE code = :code")
    suspend fun skill(code: String): SkillEntity?

    @Query("SELECT COUNT(*) FROM skills")
    suspend fun skillCount(): Int

    @Upsert
    suspend fun upsertCollectibles(items: List<CollectibleEntity>)

    @Update
    suspend fun updateCollectible(item: CollectibleEntity)

    @Query("SELECT * FROM collectibles WHERE acquiredAt IS NOT NULL ORDER BY acquiredAt DESC")
    fun observeOwnedCollectibles(): Flow<List<CollectibleEntity>>

    @Query("SELECT * FROM collectibles WHERE code = :code")
    suspend fun collectible(code: String): CollectibleEntity?

    @Query("SELECT COUNT(*) FROM collectibles")
    suspend fun collectibleCount(): Int
}

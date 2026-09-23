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

    @Query("SELECT * FROM xp_events ORDER BY createdAt")
    suspend fun allEvents(): List<XpEventEntity>
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

    @Query("SELECT * FROM quests")
    suspend fun allQuests(): List<QuestEntity>
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

    @Query("SELECT * FROM habits")
    suspend fun allHabits(): List<HabitEntity>

    @Query("SELECT * FROM habit_checkins")
    suspend fun allCheckins(): List<HabitCheckinEntity>
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

    @Query("SELECT * FROM goals")
    suspend fun allGoals(): List<GoalEntity>

    @Query("SELECT * FROM milestones")
    suspend fun allMilestones(): List<MilestoneEntity>

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: String)

    @Query("DELETE FROM milestones WHERE goalId = :goalId")
    suspend fun deleteMilestonesOf(goalId: String)

    // ---- progress readings for target goals ----

    @Upsert
    suspend fun upsertProgress(entry: GoalProgressEntity)

    @Query("SELECT * FROM goal_progress ORDER BY createdAt")
    fun observeAllProgress(): Flow<List<GoalProgressEntity>>

    @Query("SELECT * FROM goal_progress WHERE goalId = :goalId ORDER BY createdAt")
    suspend fun progressOf(goalId: String): List<GoalProgressEntity>

    @Query("DELETE FROM goal_progress WHERE id = :id")
    suspend fun deleteProgress(id: String)

    @Query("DELETE FROM goal_progress WHERE goalId = :goalId")
    suspend fun deleteProgressOf(goalId: String)

    @Query("SELECT * FROM goal_progress")
    suspend fun allProgress(): List<GoalProgressEntity>

    // ---- checkpoints: mini goals inside a target goal ----

    @Upsert
    suspend fun upsertCheckpoint(checkpoint: GoalCheckpointEntity)

    @Query("SELECT * FROM goal_checkpoints ORDER BY dueDate")
    fun observeAllCheckpoints(): Flow<List<GoalCheckpointEntity>>

    @Query("SELECT * FROM goal_checkpoints WHERE goalId = :goalId ORDER BY dueDate")
    suspend fun checkpointsOf(goalId: String): List<GoalCheckpointEntity>

    @Query("DELETE FROM goal_checkpoints WHERE id = :id")
    suspend fun deleteCheckpoint(id: String)

    @Query("DELETE FROM goal_checkpoints WHERE goalId = :goalId")
    suspend fun deleteCheckpointsOf(goalId: String)

    /** Clear a plan's steps that haven't been reached; hit ones stay as history. */
    @Query("DELETE FROM goal_checkpoints WHERE goalId = :goalId AND planned = 1 AND reachedAt IS NULL")
    suspend fun deleteOpenPlannedOf(goalId: String)

    @Query("SELECT * FROM goal_checkpoints")
    suspend fun allCheckpoints(): List<GoalCheckpointEntity>
}

@Dao
interface DocumentDao {
    @Upsert
    suspend fun upsertDocument(doc: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: String): DocumentEntity?

    @Insert
    suspend fun insertChunks(chunks: List<ChunkEntity>)

    /** Rewrites existing rows — used when re-embedding after an embedder change. */
    @Upsert
    suspend fun upsertChunks(chunks: List<ChunkEntity>)

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

    @Query("SELECT * FROM documents")
    suspend fun allDocuments(): List<DocumentEntity>

    @Query("SELECT COUNT(*) FROM documents WHERE status = 'ready'")
    suspend fun readyCount(): Int

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun anyCount(): Int

    @Query("SELECT COUNT(DISTINCT domain) FROM documents WHERE status = 'ready' AND domain IS NOT NULL")
    suspend fun domainCount(): Int
}

@Dao
interface ChatDao {
    @Insert
    suspend fun insert(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages ORDER BY createdAt")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt")
    suspend fun allMessages(): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Dao
interface FolderDao {
    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM folders ORDER BY createdAt")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY createdAt")
    suspend fun all(): List<FolderEntity>

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :id")
    suspend fun detachNotes(id: String)
}

@Dao
interface NoteVectorDao {
    @Upsert
    suspend fun upsert(vector: NoteVectorEntity)

    @Query("SELECT * FROM note_vectors")
    suspend fun all(): List<NoteVectorEntity>

    @Query("DELETE FROM note_vectors WHERE noteId = :noteId")
    suspend fun delete(noteId: String)
}

@Dao
interface AttachmentDao {
    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE ownerKind = :kind ORDER BY createdAt")
    fun observeOfKind(kind: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE ownerKind = :kind AND ownerId = :ownerId ORDER BY createdAt")
    suspend fun of(kind: String, ownerId: String): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE ownerKind = :kind AND ownerId = :ownerId")
    suspend fun deleteAllOf(kind: String, ownerId: String)

    @Query("SELECT * FROM attachments")
    suspend fun allAttachments(): List<AttachmentEntity>
}

@Dao
interface NoteDao {
    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY createdAt")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM notes")
    suspend fun allNotes(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE done = 0")
    suspend fun openCount(): Int

    /** Items ever ticked off — they count towards the achievements quests used to. */
    @Query("SELECT COUNT(*) FROM notes WHERE completedAt IS NOT NULL")
    suspend fun completedCount(): Int

    @Query("SELECT COUNT(*) FROM notes WHERE completedAt IS NOT NULL AND starred = 1")
    suspend fun starredCompletedCount(): Int
}

@Dao
interface ReviewDao {
    @Upsert
    suspend fun upsert(review: WeeklyReviewEntity)

    @Query("SELECT * FROM weekly_reviews ORDER BY weekStart DESC")
    fun observeReviews(): Flow<List<WeeklyReviewEntity>>

    @Query("SELECT * FROM weekly_reviews WHERE weekStart = :weekStart")
    suspend fun get(weekStart: String): WeeklyReviewEntity?

    @Query("SELECT * FROM weekly_reviews")
    suspend fun allReviews(): List<WeeklyReviewEntity>
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

    @Query("SELECT * FROM skills")
    suspend fun allSkills(): List<SkillEntity>

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

    @Query("SELECT * FROM collectibles")
    suspend fun allCollectibles(): List<CollectibleEntity>

    @Query("SELECT COUNT(*) FROM collectibles")
    suspend fun collectibleCount(): Int
}

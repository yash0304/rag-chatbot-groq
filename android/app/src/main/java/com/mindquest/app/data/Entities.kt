package com.mindquest.app.data

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * On-device schema (Room). Single-user offline app, so "catalog" rows (achievements,
 * skills, collectibles) carry their own unlocked/acquired timestamp instead of a
 * separate ownership table. Timestamps are epoch-millis Longs; calendar dates are
 * ISO "yyyy-MM-dd" Strings — keeps Room free of custom type converters.
 */

@Entity(tableName = "profile")
@Serializable
data class ProfileEntity(
    @PrimaryKey val id: Int = 1, // single local hero
    val heroName: String,
    val xp: Long = 0,
    val level: Int = 1,
    val skillPoints: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "xp_events", indices = [Index("createdAt")])
@Serializable
data class XpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val amount: Int,
    val refId: String? = null,
    val meta: String? = null, // small JSON blob, optional
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "quests")
@Serializable
data class QuestEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String? = null,
    val difficulty: String = "normal", // trivial|easy|normal|hard|epic
    val xpReward: Int = 50,
    val status: String = "active", // draft|active|completed|abandoned
    val source: String = "manual", // manual|ai
    val goalId: String? = null,
    val dueAt: Long? = null,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "habits")
@Serializable
data class HabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val cadence: String = "daily", // daily|weekdays|weekly
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastCheckinDate: String? = null, // ISO yyyy-MM-dd
    val xpBase: Int = 15,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "habit_checkins",
    indices = [Index(value = ["habitId", "date"], unique = true)],
)
@Serializable
data class HabitCheckinEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val date: String, // ISO yyyy-MM-dd
    val xpAwarded: Int,
)

@Entity(tableName = "goals")
@Serializable
data class GoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val narrative: String? = null,
    val arcTheme: String? = null,
    val status: String = "active", // active|completed|archived
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "milestones", indices = [Index("goalId")])
@Serializable
data class MilestoneEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val seq: Int,
    val title: String,
    val completed: Boolean = false,
    val completedAt: Long? = null,
)

@Entity(tableName = "achievements")
@Serializable
data class AchievementEntity(
    @PrimaryKey val code: String,
    val name: String,
    val description: String,
    val icon: String,
    val xpBonus: Int,
    val secret: Boolean = false,
    val unlockedAt: Long? = null, // null = locked
)

@Entity(tableName = "skills")
@Serializable
data class SkillEntity(
    @PrimaryKey val code: String,
    val tree: String, // scholar|explorer|strategist|forger
    val tier: Int,
    val name: String,
    val description: String,
    val cost: Int,
    val parentCode: String? = null,
    val unlockedAt: Long? = null, // null = locked
)

@Entity(tableName = "collectibles")
@Serializable
data class CollectibleEntity(
    @PrimaryKey val code: String,
    val name: String,
    val rarity: String, // common|rare|epic|legendary
    val lore: String,
    val acquiredAt: Long? = null, // null = not owned
    val source: String? = null,
)

@Entity(tableName = "documents")
@Serializable
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val filename: String,
    val mimeType: String,
    val status: String = "processing", // processing|ready|failed
    val error: String? = null,
    val summary: String? = null,
    val domain: String? = null,
    val tagsCsv: String = "", // comma-separated tags (single-user; avoids a join table)
    val ocrUsed: Boolean = false,
    val charCount: Int = 0,
    val chunkCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "chunks", indices = [Index("documentId")])
@Serializable
data class ChunkEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val seq: Int,
    val text: String,
    val location: String? = null,
    val vectorCsv: String, // comma-separated floats (hashing embedding, dim 256)
)

@Entity(tableName = "chat_messages", indices = [Index("createdAt")])
@Serializable
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val role: String, // user | assistant
    val content: String,
    val citationsJson: String = "[]", // JSON array of {index,title,snippet,location}
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "weekly_reviews")
@Serializable
data class WeeklyReviewEntity(
    @PrimaryKey val weekStart: String, // ISO yyyy-MM-dd (Monday)
    val statsJson: String,
    val narrative: String,
    val suggestionsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

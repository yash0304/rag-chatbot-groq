package com.mindquest.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * On-device schema (Room). Single-user offline app, so "catalog" rows (achievements,
 * skills, collectibles) carry their own unlocked/acquired timestamp instead of a
 * separate ownership table. Timestamps are epoch-millis Longs; calendar dates are
 * ISO "yyyy-MM-dd" Strings — keeps Room free of custom type converters.
 */

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1, // single local hero
    val heroName: String,
    val xp: Long = 0,
    val level: Int = 1,
    val skillPoints: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "xp_events", indices = [Index("createdAt")])
data class XpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val amount: Int,
    val refId: String? = null,
    val meta: String? = null, // small JSON blob, optional
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "quests")
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
data class HabitCheckinEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val date: String, // ISO yyyy-MM-dd
    val xpAwarded: Int,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val narrative: String? = null,
    val arcTheme: String? = null,
    val status: String = "active", // active|completed|archived
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "milestones", indices = [Index("goalId")])
data class MilestoneEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val seq: Int,
    val title: String,
    val completed: Boolean = false,
    val completedAt: Long? = null,
)

@Entity(tableName = "achievements")
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
data class CollectibleEntity(
    @PrimaryKey val code: String,
    val name: String,
    val rarity: String, // common|rare|epic|legendary
    val lore: String,
    val acquiredAt: Long? = null, // null = not owned
    val source: String? = null,
)

package com.mindquest.app.data

import androidx.room.ColumnInfo
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
    val category: String? = null, // life category, same vocabulary as notes and documents
    @ColumnInfo(defaultValue = "0") val categoryLocked: Boolean = false,
    val dueAt: Long? = null,
    val completedAt: Long? = null,
    /** When the user last changed the wording or the terms. Null = never edited. */
    val updatedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "habits")
@Serializable
data class HabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val cadence: String = "daily", // see Cadences.all — daily through yearly
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastCheckinDate: String? = null, // ISO yyyy-MM-dd
    /** Minutes past midnight for the nudge (21:00 = 1260). Null = no reminder. */
    val remindMinuteOfDay: Int? = null,
    /**
     * What this mission is working towards — "80 kg" — and by when, as an ISO date.
     * Together they turn a recurring nudge into a countdown: a monthly mission with a target
     * of March 2027 can say how many months are left every time it comes round.
     */
    val targetNote: String? = null,
    val targetDate: String? = null, // ISO yyyy-MM-dd
    val updatedAt: Long? = null,
    val xpBase: Int = 15,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A photo kept against a quest or a mission — the monthly weighing-scale reading, the
 * receipt, the before-and-after.
 *
 * The file is copied into the app's own storage rather than referenced where it was picked
 * from, so clearing your gallery can't empty the record. [ownerKind] keeps one table serving
 * quests, missions and notes instead of three that would drift apart.
 */
@Entity(tableName = "attachments", indices = [Index(value = ["ownerKind", "ownerId"])])
@Serializable
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val ownerKind: String, // quest|habit|note
    val ownerId: String,
    val path: String, // absolute path inside filesDir
    val caption: String? = null,
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
    /** For a target goal, the sentence it was created from — "1crore inr earn by March 2027". */
    val narrative: String? = null,
    val arcTheme: String? = null,
    val status: String = "active", // active|completed|archived
    /**
     * A goal is either a story arc (a list of milestones, the original kind) or a target:
     * a number to reach by a date — "80 kg by March 2027". A target goal has [targetValue]
     * or [changeValue] set; an arc has neither.
     */
    val targetValue: Double? = null,
    /**
     * "Lose 10 kg" before its first weigh-in: a signed change with no starting point yet.
     * The first reading turns it into an absolute [targetValue] and clears this.
     */
    val changeValue: Double? = null,
    val unit: String? = null, // "kg", "₹", "books"…
    val deadline: String? = null, // ISO yyyy-MM-dd
    /** Minutes past midnight for the check-in nudge; null = off. */
    val checkinMinuteOfDay: Int? = null,
    /**
     * How often to check in, as a Cadences id; null means monthly. Nullable rather than
     * defaulted so the migration can add it as a plain column — a text default has to match
     * Room's expectations character for character, and a mismatch refuses to open the data.
     */
    val checkinCadence: String? = null,
    val updatedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A number to reach by a date, as opposed to a story arc of milestones. */
val GoalEntity.isTarget: Boolean get() = targetValue != null || changeValue != null

/** The check-in cadence with its default applied. */
val GoalEntity.cadence: String get() = checkinCadence ?: "monthly"

/**
 * One reading against a target goal: the scales on the 1st, the savings total at month end.
 * The history is the point — a goal you can't see moving is a goal you stop believing in.
 */
@Entity(tableName = "goal_progress", indices = [Index("goalId")])
@Serializable
data class GoalProgressEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val value: Double,
    val note: String? = null,
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
    val domain: String? = null, // life category id (Categories)
    @ColumnInfo(defaultValue = "0") val domainLocked: Boolean = false,
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

/**
 * A folder the user made themselves, e.g. "Tuesday vegetable market". Distinct from the
 * built-in life categories: those are guessed and describe what a note is about, whereas a
 * folder is declared and describes where the user has decided it belongs.
 */
@Entity(tableName = "folders", indices = [Index("createdAt")])
@Serializable
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String = "🗂",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Quick-capture inbox note: a line of text, optionally with a reminder time.
 * Can graduate into a Quest (questId) or into the Archives (docId).
 */
@Entity(tableName = "notes", indices = [Index("createdAt")])
@Serializable
data class NoteEntity(
    @PrimaryKey val id: String,
    val text: String,
    val done: Boolean = false,
    val remindAt: Long? = null, // epoch millis; null = no reminder
    val questId: String? = null, // set once promoted to a quest
    val docId: String? = null, // set once saved to the archives
    val category: String? = null, // Categories.classify() guess; user can override
    /** True once the user has picked a category by hand — auto-classification must not undo that. */
    @ColumnInfo(defaultValue = "0") val categoryLocked: Boolean = false,
    /** A user-made folder, when the note has been filed into one. Sits over the category. */
    val folderId: String? = null,
    /** When the user last changed the wording or the reminder. Null = never edited. */
    val updatedAt: Long? = null,
    /**
     * How often the reminder comes back, as a Cadences id ("monthly"), or null for once.
     * A repeating note is never left ticked: marking it done rolls the reminder forward to
     * the next date, so "rent on the 5th" is one note for as long as you pay rent.
     */
    val repeat: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A note's meaning, as a vector, so search can find "that restaurant in Colaba" from a note
 * that only says "Gokul Dhaba — must visit".
 *
 * Its own table rather than a column on the note: every change to any note re-emits the
 * whole Inbox list, and carrying a few kilobytes of numbers per note through every one of
 * those would be paid for on each keystroke. [textHash] and [embedder] say what the vector
 * was computed from, so an edited note or a switched-in embedder is noticed and redone.
 * Not exported — it is derived data and rebuilds itself after a restore.
 */
@Entity(tableName = "note_vectors")
data class NoteVectorEntity(
    @PrimaryKey val noteId: String,
    val vectorCsv: String,
    val textHash: Int,
    val embedder: String,
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

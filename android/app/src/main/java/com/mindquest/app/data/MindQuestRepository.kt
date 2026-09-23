package com.mindquest.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.mindquest.app.domain.Cadences
import com.mindquest.app.domain.Catalogs
import com.mindquest.app.domain.Categories
import com.mindquest.app.domain.DateParse
import com.mindquest.app.domain.FolderMatch
import com.mindquest.app.domain.Embeddings
import com.mindquest.app.domain.GameEngine
import com.mindquest.app.domain.GoalCheckins
import com.mindquest.app.domain.GoalMath
import com.mindquest.app.domain.GoalParse
import com.mindquest.app.domain.Ingestion
import com.mindquest.app.domain.Narrator
import com.mindquest.app.domain.Embedders
import com.mindquest.app.domain.HashingEmbedder
import com.mindquest.app.domain.Reminders
import com.mindquest.app.domain.Retrieval
import com.mindquest.app.domain.SarvamClient
import com.mindquest.app.widget.TodayWidget
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class UnlockedAchievement(val code: String, val name: String, val icon: String, val xpBonus: Int)

data class AwardResult(
    val xpAwarded: Int = 0,
    val levelUp: Boolean = false,
    val newLevel: Int = 1,
    val achievementsUnlocked: List<UnlockedAchievement> = emptyList(),
)

data class CheckinResult(
    val alreadyDone: Boolean = false,
    val xpAwarded: Int = 0,
    val multiplier: Double = 1.0,
    val newStreak: Int = 0,
    val levelUp: Boolean = false,
    val achievementsUnlocked: List<UnlockedAchievement> = emptyList(),
)

data class MilestoneResult(
    val alreadyDone: Boolean = false,
    val xpAwarded: Int = 0,
    val goalCompleted: Boolean = false,
    val levelUp: Boolean = false,
    val achievementsUnlocked: List<UnlockedAchievement> = emptyList(),
)

data class SkillUnlockResult(val ok: Boolean, val message: String)

data class DayXp(val date: String, val xp: Int)
data class DayCount(val date: String, val count: Int)

data class SummaryStats(
    val xpTotal: Long = 0, val level: Int = 1, val xp7d: Long = 0,
    val questsCompleted: Int = 0, val habits: Int = 0, val checkins: Int = 0, val bestStreak: Int = 0,
)

data class PersonalBests(
    val highestLevel: Int = 1, val totalXp: Long = 0, val bestStreakEver: Int = 0,
    val mostXpInADay: Int = 0, val questsCompleted: Int = 0, val missionsCompleted: Int = 0,
)

data class SearchHit(val title: String, val snippet: String, val location: String?, val score: Float)

/** What one captured sentence turned into, so the caller can say so out loud. */
data class CaptureResult(
    val id: String,
    val text: String,
    val dueAt: Long?,
    val category: String,
    val folderId: String?,
    val repeat: String? = null,
    /** The folder's name when the line was filed into one of the user's own folders. */
    val folderName: String? = null,
    /** Set when the line was a goal and went to Goals instead of the Inbox. */
    val goal: GoalParse.Goal? = null,
    /** Set when the line was a checkpoint and was added to the goal named [checkpointOf]. */
    val checkpoint: GoalParse.Checkpoint? = null,
    val checkpointOf: String? = null,
    val checkpointMet: Boolean = false,
) {
    /** One line saying where it went — for the toast after the mic, the widget or Share. */
    fun describe(stamp: (Long) -> String): String {
        checkpoint?.let { cp ->
            val date = cp.date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
            return "🏁 Checkpoint ${GoalParse.format(cp.value, cp.unit)} by $date → $checkpointOf" +
                if (checkpointMet) " · already there ✓" else ""
        }
        goal?.let {
            return "🎯 ${it.title} by ${Cadences.formatTarget(it.deadline.toString())} → Goals · check-in on the 1st"
        }
        return buildString {
            append(text)
            append(" → ")
            append(folderName?.let { "🗂 $it" } ?: Categories.of(category).label)
            dueAt?.let { append(" · ⏰ ${stamp(it)}") }
            repeat?.let { append(" · 🔁 ${Cadences.of(it).label}") }
        }
    }
}

/** One line on the home-screen widget. Carries its id so the row can be ticked off there. */
data class AgendaItem(
    val id: String,
    val title: String,
    val dueAt: Long,
    val overdue: Boolean,
    val icon: String,
    val isQuest: Boolean,
)

/** Where a global-search hit lives, so the result can say which screen to open. */
enum class GlobalKind(val label: String, val icon: String) {
    Note("Inbox", "📥"),
    Document("Archives", "📜"),
    Quest("Quests", "⚔️"),
    Habit("Missions", "🔥"),
    Goal("Goals", "🎯"),
    Folder("Inbox folder", "🗂"),
}

data class GlobalHit(
    val kind: GlobalKind,
    val title: String,
    val subtitle: String,
    val refId: String?,
    val score: Float = 0f,
)

data class GraphNode(val id: String, val label: String, val type: String, val size: Int)
data class GraphEdge(val source: String, val target: String, val weight: Int)
data class GraphData(val nodes: List<GraphNode>, val edges: List<GraphEdge>)

@Serializable
data class Citation(val index: Int, val title: String, val snippet: String, val location: String? = null)

@Serializable
data class WeekStats(
    val weekStart: String, val xpEarned: Int = 0, val questsCompleted: Int = 0,
    val habitCheckins: Int = 0, val documentsProcessed: Int = 0, val milestones: Int = 0,
)

@Serializable
private data class QuestGen(val title: String = "", val description: String = "", val difficulty: String = "normal")

/** Full portable snapshot of everything on-device — "his data must outlive the app." */
@Serializable
data class ExportBundle(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val profile: ProfileEntity? = null,
    val xpEvents: List<XpEventEntity> = emptyList(),
    val quests: List<QuestEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val checkins: List<HabitCheckinEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val milestones: List<MilestoneEntity> = emptyList(),
    val achievements: List<AchievementEntity> = emptyList(),
    val skills: List<SkillEntity> = emptyList(),
    val collectibles: List<CollectibleEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val chunks: List<ChunkEntity> = emptyList(),
    val chat: List<ChatMessageEntity> = emptyList(),
    val reviews: List<WeeklyReviewEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    /**
     * Photo records, not the photos. The files live in the app's private storage and a JSON
     * export is text, so these paths resolve on this device and on no other — enough to
     * rebuild the trail after a reinstall-in-place, honestly useless after a phone change.
     */
    val attachments: List<AttachmentEntity> = emptyList(),
    /** Readings against target goals — the weigh-ins, the savings totals. */
    val goalProgress: List<GoalProgressEntity> = emptyList(),
    /** Mini goals inside target goals, planned and hand-set. */
    val goalCheckpoints: List<GoalCheckpointEntity> = emptyList(),
)

/**
 * The app's single offline data API. All screens go through this; nothing touches the network.
 * Ports the write-path semantics of backend/app/services/gamification.py onto Room.
 */
class MindQuestRepository(private val context: Context) {
    private val db = MindQuestDatabase.get(context)
    private val profileDao = db.profileDao()
    private val xpDao = db.xpEventDao()
    private val questDao = db.questDao()
    private val habitDao = db.habitDao()
    private val goalDao = db.goalDao()
    private val catalogDao = db.catalogDao()
    private val documentDao = db.documentDao()
    private val chatDao = db.chatDao()
    private val reviewDao = db.reviewDao()
    private val noteDao = db.noteDao()
    private val folderDao = db.folderDao()
    private val attachmentDao = db.attachmentDao()
    private val noteVectorDao = db.noteVectorDao()
    private val json = Json { ignoreUnknownKeys = true }

    // Resolved on first use: the MiniLM model if its assets shipped, else hashing vectors.
    private val embedder by lazy { Embedders.active(context) }

    val settings = SettingsStore(context)
    private val sarvam = SarvamClient(settings)

    /** True if the Sarvam key is set, so AI features generate rather than fall back offline. */
    fun aiConfigured(): Boolean = sarvam.isConfigured

    // ---------- bootstrap ----------

    suspend fun seedIfEmpty() {
        if (catalogDao.achievementCount() == 0) catalogDao.upsertAchievements(Catalogs.achievements)
        if (catalogDao.skillCount() == 0) catalogDao.upsertSkills(Catalogs.skills)
        if (catalogDao.collectibleCount() == 0) catalogDao.upsertCollectibles(Catalogs.collectibles)
    }

    suspend fun hasProfile(): Boolean = profileDao.get() != null

    suspend fun createProfile(heroName: String) {
        profileDao.upsert(ProfileEntity(heroName = heroName.ifBlank { "Wanderer" }))
    }

    fun observeProfile(): Flow<ProfileEntity?> = profileDao.observe()

    // ---------- award path (the single XP write point) ----------

    private suspend fun applyXp(amount: Int, kind: String, refId: String?, meta: String?): Pair<Boolean, Int> {
        val profile = profileDao.get() ?: return false to 1
        xpDao.insert(XpEventEntity(kind = kind, amount = amount, refId = refId, meta = meta))
        val oldLevel = profile.level
        val newXp = profile.xp + amount
        val newLevel = GameEngine.levelForXp(newXp)
        val gainedPoints = (newLevel - oldLevel).coerceAtLeast(0)
        profileDao.upsert(
            profile.copy(xp = newXp, level = newLevel, skillPoints = profile.skillPoints + gainedPoints),
        )
        return (newLevel > oldLevel) to newLevel
    }

    /** Insert an XP event and roll forward level, skill points, and achievements. */
    suspend fun award(kind: String, amount: Int, refId: String? = null, meta: String? = null): AwardResult =
        db.withTransaction {
            if (amount <= 0) return@withTransaction AwardResult()
            val (levelUp, newLevel) = applyXp(amount, kind, refId, meta)
            val unlocked = evaluateAchievements()
            AwardResult(amount, levelUp, newLevel, unlocked)
        }

    private suspend fun currentStats(): GameEngine.Stats {
        val profile = profileDao.get()
        return GameEngine.Stats(
            documentsReady = documentDao.readyCount(),
            documentsAny = documentDao.anyCount(),
            questsCompleted = questDao.completedCount(),
            epicCompleted = questDao.epicCompletedCount(),
            bestStreak = habitDao.maxBestStreak() ?: 0,
            consulted = xpDao.countKind("knowledge_consulted"),
            domains = documentDao.domainCount(),
            goalsCompleted = xpDao.countKind("goal_completed"),
            level = profile?.level ?: 1,
        )
    }

    /** Unlock any newly-earned achievements; grant their bonus XP and companion collectibles. */
    private suspend fun evaluateAchievements(): List<UnlockedAchievement> {
        val stats = currentStats()
        val unlocked = mutableListOf<UnlockedAchievement>()
        for (ach in catalogDao.achievements()) {
            if (ach.unlockedAt != null) continue
            val rule = GameEngine.rules[ach.code] ?: continue
            if (!rule(stats)) continue

            catalogDao.updateAchievement(ach.copy(unlockedAt = System.currentTimeMillis()))
            if (ach.xpBonus > 0) {
                // bonus XP does not itself re-trigger achievement evaluation (avoids recursion)
                applyXp(ach.xpBonus, "achievement_bonus", ach.code, null)
            }
            Catalogs.achievementCollectibles[ach.code]?.let { code ->
                catalogDao.collectible(code)?.let { c ->
                    if (c.acquiredAt == null) {
                        catalogDao.updateCollectible(
                            c.copy(acquiredAt = System.currentTimeMillis(), source = "achievement"),
                        )
                    }
                }
            }
            unlocked += UnlockedAchievement(ach.code, ach.name, ach.icon, ach.xpBonus)
        }
        return unlocked
    }

    // ---------- quests ----------

    fun observeActiveQuests(): Flow<List<QuestEntity>> = questDao.observeByStatus("active")
    fun observeAllQuests(): Flow<List<QuestEntity>> = questDao.observeAll()

    suspend fun createQuest(
        title: String,
        difficulty: String,
        description: String? = null,
        goalId: String? = null,
        category: String? = null,
        categoryChosen: Boolean = false,
    ) {
        val diff = if (difficulty in Catalogs.difficultyXp) difficulty else "normal"
        questDao.upsert(
            QuestEntity(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                description = description,
                difficulty = diff,
                xpReward = Catalogs.difficultyXp.getValue(diff),
                status = "active",
                goalId = goalId,
                category = category ?: Categories.classify(listOfNotNull(title, description).joinToString(" ")),
                categoryLocked = categoryChosen,
            ),
        )
    }

    suspend fun completeQuest(id: String): AwardResult {
        val quest = questDao.get(id) ?: return AwardResult()
        if (quest.status == "completed") return AwardResult()
        questDao.upsert(quest.copy(status = "completed", completedAt = System.currentTimeMillis()))
        return award("quest_completed", quest.xpReward, refId = quest.id, meta = "{\"difficulty\":\"${quest.difficulty}\"}")
    }

    suspend fun abandonQuest(id: String) {
        questDao.get(id)?.let { questDao.upsert(it.copy(status = "abandoned")) }
    }

    // ---------- habits ----------

    fun observeHabits(): Flow<List<HabitEntity>> = habitDao.observeAll()

    /**
     * Is this mission already done for the period it is currently in? A monthly mission
     * ticked off on the 3rd is done for that whole month, not just that Tuesday.
     */
    fun isCheckedInThisPeriod(habit: HabitEntity): Boolean {
        val last = habit.lastCheckinDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return false
        return Cadences.periodIndex(habit.cadence, last) ==
            Cadences.periodIndex(habit.cadence, LocalDate.now())
    }

    suspend fun createHabit(
        title: String,
        cadence: String,
        targetNote: String? = null,
        targetDate: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        habitDao.upsert(
            HabitEntity(
                id = id,
                title = title.trim(),
                cadence = cadence,
                targetNote = targetNote?.trim()?.takeIf { it.isNotBlank() },
                targetDate = targetDate,
            ),
        )
        return id
    }

    /**
     * Change a mission's wording, how often it comes round, or what it is working towards.
     * Changing the cadence re-aims the nudge, since "the 1st of the month" and "every
     * evening" are not the same appointment.
     */
    suspend fun editHabit(
        id: String,
        title: String,
        cadence: String,
        targetNote: String?,
        targetDate: String?,
    ) {
        val habit = habitDao.get(id) ?: return
        val updated = habit.copy(
            title = title.trim().ifBlank { habit.title },
            cadence = cadence,
            targetNote = targetNote?.trim()?.takeIf { it.isNotBlank() },
            targetDate = targetDate,
            updatedAt = System.currentTimeMillis(),
        )
        habitDao.upsert(updated)
        updated.remindMinuteOfDay?.let {
            Reminders.scheduleHabit(context, id, it, updated.cadence)
        }
        TodayWidget.refresh(context)
    }

    suspend fun deleteHabit(id: String) {
        Reminders.cancelHabitReminder(context, id) // no point nudging about a mission that's gone
        attachmentDao.of("habit", id).forEach { PhotoStore.delete(it.path) }
        attachmentDao.deleteAllOf("habit", id)
        habitDao.delete(id)
        TodayWidget.refresh(context)
    }

    /**
     * Set or clear the nudge for a mission — 21:00 daily for "walk 5000 steps", 09:00 on the
     * 1st for a monthly weigh-in. [minuteOfDay] is minutes past midnight; null turns it off.
     */
    suspend fun setHabitReminder(id: String, minuteOfDay: Int?) {
        val habit = habitDao.get(id) ?: return
        habitDao.upsert(habit.copy(remindMinuteOfDay = minuteOfDay))
        if (minuteOfDay == null) {
            Reminders.cancelHabitReminder(context, id)
        } else {
            Reminders.scheduleHabit(context, id, minuteOfDay, habit.cadence)
        }
    }

    /**
     * Re-arm every mission nudge. The schedule is a chain of one-shots that each arm the
     * next, and WorkManager keeps a pending one across reboots on its own — but this repairs
     * the case where the chain was broken (app data wiped, a restore from backup, a firing
     * that never happened) and costs nothing when everything is already scheduled.
     */
    /**
     * Re-arm every note reminder that is still ahead of us. Only future ones are touched:
     * a reminder already past its time may be partway through its repeat-until-done chain,
     * and cancelling that to reschedule would silence a nag that is currently working.
     */
    suspend fun rearmNoteReminders() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        noteDao.allNotes()
            .filter { !it.done && (it.remindAt ?: 0L) > now }
            .forEach { note ->
                Reminders.cancel(context, note.id)
                Reminders.schedule(context, note.id, note.text, note.remindAt!!)
            }
    }

    suspend fun rearmHabitReminders() = withContext(Dispatchers.IO) {
        habitDao.allHabits().forEach { habit ->
            habit.remindMinuteOfDay?.let {
                Reminders.scheduleHabit(context, habit.id, it, habit.cadence)
            }
        }
    }

    suspend fun checkin(id: String): CheckinResult = db.withTransaction {
        val habit = habitDao.get(id) ?: return@withTransaction CheckinResult()
        val today = LocalDate.now()
        val todayIso = today.toString()
        if (habitDao.checkinExists(id, todayIso) > 0) return@withTransaction CheckinResult(alreadyDone = true)
        // One check-in per period, not per day: a monthly mission ticked off on the 3rd
        // shouldn't pay out again on the 10th.
        if (isCheckedInThisPeriod(habit)) return@withTransaction CheckinResult(alreadyDone = true)

        val lastPeriod = habit.lastCheckinDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { Cadences.periodIndex(habit.cadence, it) }
        val newStreak = GameEngine.computeStreak(
            habit.cadence, habit.streak, lastPeriod, Cadences.periodIndex(habit.cadence, today),
        )
        val multiplier = GameEngine.streakMultiplier(newStreak)
        val xp = (habit.xpBase * multiplier).toInt()

        habitDao.insertCheckin(
            HabitCheckinEntity(UUID.randomUUID().toString(), id, todayIso, xp),
        )
        habitDao.upsert(
            habit.copy(
                streak = newStreak,
                bestStreak = maxOf(habit.bestStreak, newStreak),
                lastCheckinDate = todayIso,
            ),
        )
        val award = award("habit_checkin", xp, refId = id, meta = "{\"streak\":$newStreak}")
        CheckinResult(
            alreadyDone = false,
            xpAwarded = xp,
            multiplier = multiplier,
            newStreak = newStreak,
            levelUp = award.levelUp,
            achievementsUnlocked = award.achievementsUnlocked,
        )
    }

    // ---------- analytics ----------

    suspend fun xpLast7Days(): Long =
        xpDao.sumSince(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)

    suspend fun completedQuestCount(): Int = questDao.completedCount()
    suspend fun habitCount(): Int = habitDao.count()
    suspend fun maxStreak(): Int = habitDao.maxStreak() ?: 0

    // ---------- goals / story arcs ----------

    fun observeGoals(): Flow<List<GoalEntity>> = goalDao.observeGoals()
    fun observeAllMilestones(): Flow<List<MilestoneEntity>> = goalDao.observeAllMilestones()
    fun observeGoalProgress(): Flow<List<GoalProgressEntity>> = goalDao.observeAllProgress()

    suspend fun goal(id: String): GoalEntity? = goalDao.getGoal(id)
    suspend fun goalProgress(id: String): List<GoalProgressEntity> = goalDao.progressOf(id)

    /**
     * Create a target goal — "80 kg by March 2027" — with a check-in nudge on the 1st of each
     * month at 9:00 unless told otherwise. The nudge is on by default because a goal you are
     * never asked about is a goal you forget you set.
     */
    suspend fun createTargetGoal(
        goal: GoalParse.Goal,
        narrative: String?,
        checkinMinute: Int? = DEFAULT_CHECKIN_MINUTE,
    ): String {
        val id = UUID.randomUUID().toString()
        goalDao.upsertGoal(
            GoalEntity(
                id = id,
                title = goal.title,
                narrative = narrative?.trim()?.takeIf { it.isNotBlank() && it != goal.title },
                targetValue = goal.target,
                changeValue = goal.change,
                unit = goal.unit,
                deadline = goal.deadline.toString(),
                checkinMinuteOfDay = checkinMinute,
            ),
        )
        checkinMinute?.let { GoalCheckins.schedule(context, id, it, "monthly") }
        return id
    }

    data class GoalLogResult(
        val xpAwarded: Int = 0,
        val reached: Boolean = false,
        val levelUp: Boolean = false,
        val status: GoalMath.Status? = null,
        /** Checkpoints this reading got to for the first time. */
        val checkpointsHit: List<GoalCheckpointEntity> = emptyList(),
    )

    /**
     * Record a reading. The first reading of a "lose 10 kg" goal is what fixes its target —
     * until you weigh in, ten kilos from what isn't known. Crossing the target completes the
     * goal, pays the goal bonus, and stops the check-ins.
     */
    suspend fun logGoalProgress(goalId: String, value: Double, note: String? = null): GoalLogResult =
        db.withTransaction {
            val original = goalDao.getGoal(goalId) ?: return@withTransaction GoalLogResult()
            val change = original.changeValue
            val goal = if (original.targetValue == null && change != null) {
                original.copy(targetValue = value + change, changeValue = null)
            } else {
                original
            }
            if (goal != original) goalDao.upsertGoal(goal)
            goalDao.upsertProgress(
                GoalProgressEntity(
                    id = UUID.randomUUID().toString(), goalId = goalId, value = value,
                    note = note?.trim()?.takeIf { it.isNotBlank() },
                ),
            )
            val readings = goalDao.progressOf(goalId).map { it.value }
            val deadline = goal.deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now()
            val status = GoalMath.status(goal.targetValue, goal.unit ?: "", deadline, readings)

            var xp = 0
            var levelUp = false
            award("goal_checkin", GOAL_CHECKIN_XP, refId = goalId).let { xp += it.xpAwarded; levelUp = it.levelUp }
            val hit = scoreCheckpoints(goal, value)
            xp += hit.size * Catalogs.Xp.MILESTONE
            val reached = status.reached && goal.status == "active"
            if (reached) {
                goalDao.upsertGoal(goal.copy(status = "completed"))
                GoalCheckins.cancel(context, goalId)
                award("goal_completed", Catalogs.Xp.GOAL_BONUS, refId = goalId).let {
                    xp += it.xpAwarded; levelUp = levelUp || it.levelUp
                }
            }
            GoalLogResult(xp, reached, levelUp, status, hit)
        }

    suspend fun deleteGoalProgress(entryId: String) = goalDao.deleteProgress(entryId)

    // ---------- checkpoints: mini goals inside a target goal ----------

    fun observeCheckpoints(): Flow<List<GoalCheckpointEntity>> = goalDao.observeAllCheckpoints()
    suspend fun checkpointsOf(goalId: String): List<GoalCheckpointEntity> = goalDao.checkpointsOf(goalId)

    /** Direction and start of a goal, worked out from its readings so far. */
    private suspend fun directionOf(goal: GoalEntity, reference: Double? = null): Boolean {
        val start = if (GoalParse.accumulates(goal.unit ?: "")) 0.0 else goalDao.progressOf(goal.id).firstOrNull()?.value
        val target = goal.targetValue ?: return (goal.changeValue ?: 0.0) < 0
        return GoalMath.goesDown(goal.unit ?: "", start, target, reference)
    }

    /**
     * Add a mini goal by hand — "90 kg by 1 October". If the latest reading already gets
     * there it is marked reached straight away (without XP: there's nothing to reward in
     * setting a bar you've already cleared).
     */
    suspend fun addCheckpoint(goalId: String, value: Double, due: LocalDate): Boolean {
        val goal = goalDao.getGoal(goalId) ?: return false
        val latest = goalDao.progressOf(goalId).lastOrNull()?.value
        val already = latest != null && GoalMath.crosses(value, latest, directionOf(goal, reference = value))
        goalDao.upsertCheckpoint(
            GoalCheckpointEntity(
                id = UUID.randomUUID().toString(), goalId = goalId, value = value,
                dueDate = due.toString(), planned = false,
                reachedAt = if (already) System.currentTimeMillis() else null,
            ),
        )
        return already
    }

    suspend fun deleteCheckpoint(id: String) = goalDao.deleteCheckpoint(id)

    /**
     * Split a goal into steps: a checkpoint every week, half-month or month on a straight
     * line from today's reading to the target. Redoing it replaces the plan's open steps and
     * leaves your own checkpoints alone. The check-in moves to the same rhythm — weekly steps
     * want a weekly look — and is switched on if it was off. Returns the number of steps, or
     * null when there is no reading yet to start the line from.
     */
    suspend fun planCheckpoints(goalId: String, cadence: String): Int? = db.withTransaction {
        val goal = goalDao.getGoal(goalId) ?: return@withTransaction null
        val target = goal.targetValue ?: return@withTransaction null
        val deadline = goal.deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return@withTransaction null
        val unit = goal.unit ?: ""
        val start = goalDao.progressOf(goalId).lastOrNull()?.value
            ?: if (GoalParse.accumulates(unit)) 0.0 else return@withTransaction null
        goalDao.deleteOpenPlannedOf(goalId)
        val steps = GoalMath.plan(start, target, unit, LocalDate.now(), deadline, cadence)
        steps.forEach { (date, value) ->
            goalDao.upsertCheckpoint(
                GoalCheckpointEntity(
                    id = UUID.randomUUID().toString(), goalId = goalId, value = value,
                    dueDate = date.toString(), planned = true,
                ),
            )
        }
        setGoalCheckin(goalId, goal.checkinMinuteOfDay ?: DEFAULT_CHECKIN_MINUTE, cadence)
        steps.size
    }

    suspend fun clearPlan(goalId: String) = goalDao.deleteOpenPlannedOf(goalId)

    /**
     * After a reading: stamp every open checkpoint it gets to, and pay for each once. Only
     * checkpoints still in date count — reaching October's number in November is progress,
     * and the main goal will reward it, but it isn't hitting the checkpoint.
     */
    private suspend fun scoreCheckpoints(goal: GoalEntity, value: Double): List<GoalCheckpointEntity> {
        val today = LocalDate.now()
        val down = directionOf(goal)
        val hit = goalDao.checkpointsOf(goal.id).filter { cp ->
            cp.reachedAt == null &&
                !LocalDate.parse(cp.dueDate).isBefore(today) &&
                GoalMath.crosses(cp.value, value, down)
        }
        hit.forEach { cp ->
            goalDao.upsertCheckpoint(cp.copy(reachedAt = System.currentTimeMillis()))
            award("checkpoint_hit", Catalogs.Xp.MILESTONE, refId = cp.id)
        }
        return hit
    }

    /** Change a target goal's name, target, unit or deadline. */
    suspend fun editTargetGoal(id: String, title: String, target: Double?, unit: String, deadline: String?) {
        val goal = goalDao.getGoal(id) ?: return
        goalDao.upsertGoal(
            goal.copy(
                title = title.trim().ifBlank { goal.title },
                targetValue = target ?: goal.targetValue,
                // An explicit target replaces a pending "lose 10 kg" change.
                changeValue = if (target != null) null else goal.changeValue,
                unit = unit.trim().ifBlank { goal.unit },
                deadline = deadline ?: goal.deadline,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Set or clear the check-in nudge, and how often it comes round. */
    suspend fun setGoalCheckin(id: String, minuteOfDay: Int?, cadence: String? = null) {
        val goal = goalDao.getGoal(id) ?: return
        val updated = goal.copy(checkinMinuteOfDay = minuteOfDay, checkinCadence = cadence ?: goal.checkinCadence)
        goalDao.upsertGoal(updated)
        if (minuteOfDay == null || updated.status != "active") {
            GoalCheckins.cancel(context, id)
        } else {
            GoalCheckins.schedule(context, id, minuteOfDay, updated.cadence)
        }
    }

    suspend fun deleteGoal(id: String) = db.withTransaction {
        GoalCheckins.cancel(context, id)
        attachmentDao.of("goal", id).forEach { PhotoStore.delete(it.path) }
        attachmentDao.deleteAllOf("goal", id)
        goalDao.deleteProgressOf(id)
        goalDao.deleteCheckpointsOf(id)
        goalDao.deleteMilestonesOf(id)
        goalDao.deleteGoal(id)
    }

    suspend fun rearmGoalCheckins() = withContext(Dispatchers.IO) {
        goalDao.allGoals()
            .filter { it.status == "active" && it.isTarget }
            .forEach { g -> g.checkinMinuteOfDay?.let { GoalCheckins.schedule(context, g.id, it, g.cadence) } }
    }

    suspend fun createGoal(title: String, narrative: String?, milestones: List<String>) = db.withTransaction {
        val goalId = UUID.randomUUID().toString()
        goalDao.upsertGoal(GoalEntity(id = goalId, title = title.trim(), narrative = narrative?.ifBlank { null }))
        milestones.filter { it.isNotBlank() }.forEachIndexed { i, t ->
            goalDao.upsertMilestone(MilestoneEntity(id = UUID.randomUUID().toString(), goalId = goalId, seq = i, title = t.trim()))
        }
    }

    suspend fun completeMilestone(milestoneId: String): MilestoneResult = db.withTransaction {
        val m = goalDao.getMilestone(milestoneId) ?: return@withTransaction MilestoneResult()
        if (m.completed) return@withTransaction MilestoneResult(alreadyDone = true)
        goalDao.upsertMilestone(m.copy(completed = true, completedAt = System.currentTimeMillis()))

        var xp = 0
        var levelUp = false
        val achievements = mutableListOf<UnlockedAchievement>()
        val milestoneAward = award("milestone_completed", Catalogs.Xp.MILESTONE, refId = m.id)
        xp += milestoneAward.xpAwarded; levelUp = levelUp || milestoneAward.levelUp
        achievements += milestoneAward.achievementsUnlocked

        var goalCompleted = false
        if (goalDao.milestonesOf(m.goalId).all { it.completed }) {
            val goal = goalDao.getGoal(m.goalId)
            if (goal != null && goal.status == "active") {
                goalDao.upsertGoal(goal.copy(status = "completed"))
                val bonus = award("goal_completed", Catalogs.Xp.GOAL_BONUS, refId = goal.id)
                xp += bonus.xpAwarded; levelUp = levelUp || bonus.levelUp
                achievements += bonus.achievementsUnlocked
                goalCompleted = true
            }
        }
        MilestoneResult(false, xp, goalCompleted, levelUp, achievements)
    }

    // ---------- skills ----------

    fun observeSkills(): Flow<List<SkillEntity>> = catalogDao.observeSkills()

    suspend fun unlockSkill(code: String): SkillUnlockResult = db.withTransaction {
        val skill = catalogDao.skill(code) ?: return@withTransaction SkillUnlockResult(false, "Skill not found")
        if (skill.unlockedAt != null) return@withTransaction SkillUnlockResult(false, "Already unlocked")
        if (skill.parentCode != null) {
            val parent = catalogDao.skill(skill.parentCode)
            if (parent?.unlockedAt == null) {
                return@withTransaction SkillUnlockResult(false, "Unlock ${parent?.name ?: "the previous tier"} first")
            }
        }
        val profile = profileDao.get() ?: return@withTransaction SkillUnlockResult(false, "No profile")
        if (profile.skillPoints < skill.cost) {
            return@withTransaction SkillUnlockResult(false, "Need ${skill.cost} skill point(s)")
        }
        profileDao.upsert(profile.copy(skillPoints = profile.skillPoints - skill.cost))
        catalogDao.updateSkill(skill.copy(unlockedAt = System.currentTimeMillis()))
        SkillUnlockResult(true, "Unlocked ${skill.name}")
    }

    // ---------- achievements & collectibles ----------

    fun observeAchievements(): Flow<List<AchievementEntity>> = catalogDao.observeAchievements()
    fun observeOwnedCollectibles(): Flow<List<CollectibleEntity>> = catalogDao.observeOwnedCollectibles()

    // ---------- analytics ----------

    private fun isoDay(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    suspend fun xpDaily(days: Int): List<DayXp> {
        val since = System.currentTimeMillis() - days.toLong() * 86_400_000L
        val byDay = xpDao.since(since).groupBy { isoDay(it.createdAt) }.mapValues { e -> e.value.sumOf { it.amount } }
        return (days downTo 0).map { i ->
            val d = LocalDate.now().minusDays(i.toLong()).toString()
            DayXp(d, byDay[d] ?: 0)
        }
    }

    suspend fun activityHeatmap(weeks: Int): List<DayCount> {
        val days = weeks * 7
        val since = System.currentTimeMillis() - days.toLong() * 86_400_000L
        val byDay = xpDao.since(since).groupBy { isoDay(it.createdAt) }.mapValues { it.value.size }
        return (days - 1 downTo 0).map { i ->
            val d = LocalDate.now().minusDays(i.toLong()).toString()
            DayCount(d, byDay[d] ?: 0)
        }
    }

    suspend fun summary(): SummaryStats {
        val p = profileDao.get()
        return SummaryStats(
            xpTotal = p?.xp ?: 0,
            level = p?.level ?: 1,
            xp7d = xpLast7Days(),
            questsCompleted = questDao.completedCount(),
            habits = habitDao.count(),
            checkins = habitDao.totalCheckins(),
            bestStreak = habitDao.maxBestStreak() ?: 0,
        )
    }

    suspend fun personalBests(): PersonalBests {
        val p = profileDao.get()
        val mostXpDay = xpDao.since(0)
            .groupBy { isoDay(it.createdAt) }.mapValues { e -> e.value.sumOf { it.amount } }
            .values.maxOrNull() ?: 0
        return PersonalBests(
            highestLevel = p?.level ?: 1,
            totalXp = p?.xp ?: 0,
            bestStreakEver = habitDao.maxBestStreak() ?: 0,
            mostXpInADay = mostXpDay,
            questsCompleted = questDao.completedCount(),
            missionsCompleted = habitDao.totalCheckins(),
        )
    }

    // ---------- documents (second brain, on-device) ----------

    fun observeDocuments(): Flow<List<DocumentEntity>> = documentDao.observeDocuments()

    /** The display name of a content URI, e.g. "ticket.pdf". Public for the share handler. */
    fun displayName(uri: Uri): String = resolveName(uri)

    private fun resolveName(uri: Uri): String {
        var name = "document"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx)?.let { name = it }
            }
        }
        return name
    }

    /**
     * Import + process a picked file fully offline; returns the document id. [nameOverride]
     * and [mimeOverride] are for a file shared in from another app and copied locally first,
     * where the copy no longer knows what it was called or what it is.
     */
    suspend fun importDocument(uri: Uri, nameOverride: String? = null, mimeOverride: String? = null): String {
        val id = UUID.randomUUID().toString()
        val name = nameOverride ?: resolveName(uri)
        val mime = mimeOverride ?: context.contentResolver.getType(uri) ?: "application/octet-stream"
        documentDao.upsertDocument(DocumentEntity(id = id, title = name, filename = name, mimeType = mime, status = "processing"))
        award("document_uploaded", Catalogs.Xp.DOCUMENT_UPLOADED, refId = id)
        try {
            val extracted = Ingestion.extract(context, uri, name, mime)
            val fullText = extracted.pages.joinToString("\n\n") { it.second }
            if (fullText.isBlank()) {
                error("No text could be extracted (a scanned file may have produced no OCR text).")
            }
            ingest(id, name, mime, extracted.pages, extracted.ocrUsed)
        } catch (e: Exception) {
            markFailed(id, e)
        }
        return id
    }

    /** Import raw text (e.g. a transcribed voice note) through the same pipeline. */
    suspend fun importTextNote(title: String, text: String, category: String? = null): String {
        val id = UUID.randomUUID().toString()
        val name = title.ifBlank { "Voice note" }
        documentDao.upsertDocument(DocumentEntity(id = id, title = name, filename = name, mimeType = "text/plain", status = "processing"))
        award("document_uploaded", Catalogs.Xp.DOCUMENT_UPLOADED, refId = id)
        try {
            ingest(id, name, "text/plain", listOf(1 to text), ocrUsed = false, category = category)
        } catch (e: Exception) {
            markFailed(id, e)
        }
        return id
    }

    private suspend fun ingest(
        id: String,
        name: String,
        mime: String,
        pages: List<Pair<Int, String>>,
        ocrUsed: Boolean,
        category: String? = null,
    ) {
        val fullText = pages.joinToString("\n\n") { it.second }
        if (fullText.isBlank()) error("No text could be extracted (a scanned file may produce no OCR text).")
        val chunks = Ingestion.chunk(pages)
        documentDao.insertChunks(
            chunks.map { (seq, text, loc) ->
                ChunkEntity(
                    id = UUID.randomUUID().toString(), documentId = id, seq = seq, text = text,
                    location = loc, vectorCsv = Embeddings.toCsv(embedder.embed(text)),
                )
            },
        )
        documentDao.upsertDocument(
            DocumentEntity(
                id = id, title = name, filename = name, mimeType = mime, status = "ready",
                summary = Ingestion.summarize(fullText),
                // A category passed in came from the note this document was promoted from.
                // Trust it rather than classifying the same words a second time and risking
                // a different answer for the same content.
                domain = category ?: Categories.classify(fullText),
                // Inherited, not chosen here — deliberately left unlocked so that correcting
                // the source note later still flows through. Only a category set directly on
                // this document locks it.
                domainLocked = false,
                tagsCsv = Ingestion.tags(fullText).joinToString(","),
                ocrUsed = ocrUsed, charCount = fullText.length, chunkCount = chunks.size,
            ),
        )
        award("document_processed", Catalogs.Xp.DOCUMENT_PROCESSED, refId = id)
    }

    private suspend fun markFailed(id: String, e: Exception) {
        documentDao.getDocument(id)?.let {
            documentDao.upsertDocument(it.copy(status = "failed", error = e.message?.take(2000)))
        }
    }

    /** Transcribe a recorded WAV via Sarvam, then import the text as a note. Requires a key. */
    suspend fun transcribeAndImport(wav: File): String {
        val transcript = sarvam.transcribe(wav)
        return importTextNote("Voice note · ${LocalDate.now()}", transcript)
    }

    suspend fun deleteDocument(id: String) {
        documentDao.deleteChunksOf(id)
        documentDao.deleteDocument(id)
    }

    /** Hybrid BM25 + embedding search over every chunk of every ready document. */
    suspend fun search(query: String, limit: Int = 8): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val docs = documentDao.readyDocuments().associateBy { it.id }
        val chunks = documentDao.allChunks().filter { docs.containsKey(it.documentId) }
        return Retrieval.hybridRank(
            query = query,
            queryVector = embedder.embed(query),
            items = chunks,
            textOf = { it.text },
            vectorOf = { Embeddings.fromCsv(it.vectorCsv) },
            limit = limit,
            semanticVectors = embedder !== HashingEmbedder,
        ).map { (chunk, score) ->
            SearchHit(docs.getValue(chunk.documentId).title, chunk.text.take(300), chunk.location, score)
        }
    }

    /** Which embedder is live, for the Settings screen. */
    fun embedderLabel(): String = embedder.label

    /**
     * Chunks whose vectors came from a different embedder than the one now running —
     * i.e. everything archived before the MiniLM model arrived. They stay searchable
     * lexically in the meantime, so this is a quality gap, not an outage.
     */
    suspend fun staleChunkCount(): Int = withContext(Dispatchers.IO) {
        val dim = embedder.dim
        documentDao.allChunks().count { Embeddings.fromCsv(it.vectorCsv).size != dim }
    }

    /**
     * Re-embed every out-of-date chunk with the active model. Runs off the main thread and
     * reports progress, because with a real transformer this is seconds-to-minutes of work
     * rather than the instant rewrite the hashing embeddings used to be.
     */
    suspend fun reindexSearch(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int =
        withContext(Dispatchers.IO) {
            val dim = embedder.dim
            val stale = documentDao.allChunks().filter { Embeddings.fromCsv(it.vectorCsv).size != dim }
            stale.chunked(REINDEX_BATCH).forEachIndexed { batchIndex, batch ->
                documentDao.upsertChunks(
                    batch.map { it.copy(vectorCsv = Embeddings.toCsv(embedder.embed(it.text))) },
                )
                onProgress(minOf((batchIndex + 1) * REINDEX_BATCH, stale.size), stale.size)
            }
            stale.size
        }

    suspend fun buildGraph(): GraphData {
        val docs = documentDao.readyDocuments()
        val nodes = LinkedHashMap<String, GraphNode>()
        val edges = mutableListOf<GraphEdge>()
        for (doc in docs) {
            val domainKey = "domain:${doc.domain ?: "Uncharted Lands"}"
            val dNode = nodes[domainKey]
            nodes[domainKey] = GraphNode(domainKey, doc.domain ?: "Uncharted Lands", "domain", (dNode?.size ?: 0) + 1)
            val docKey = "doc:${doc.id}"
            nodes[docKey] = GraphNode(docKey, doc.title, "document", 1)
            edges.add(GraphEdge(domainKey, docKey, 2))
            for (tag in doc.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
                val tagKey = "tag:$tag"
                val tNode = nodes[tagKey]
                nodes[tagKey] = GraphNode(tagKey, tag, "tag", (tNode?.size ?: 0) + 1)
                edges.add(GraphEdge(docKey, tagKey, 1))
            }
        }
        return GraphData(nodes.values.toList(), edges)
    }

    // ---------- Narrator (RAG chat; Sarvam when configured, retrieval-only offline) ----------

    fun observeChatMessages(): Flow<List<ChatMessageEntity>> = chatDao.observeMessages()
    suspend fun clearChat() = chatDao.clear()

    fun citationsOf(msg: ChatMessageEntity): List<Citation> =
        try { json.decodeFromString(msg.citationsJson) } catch (e: Exception) { emptyList() }

    suspend fun sendNarratorMessage(text: String) {
        chatDao.insert(ChatMessageEntity(id = UUID.randomUUID().toString(), role = "user", content = text))
        val hits = search(text, 6)
        var answer: String
        var citations: List<Citation>

        if (hits.isEmpty()) {
            answer = "The archives hold no scrolls on this. Upload documents in the Archives, then ask me again."
            citations = emptyList()
        } else {
            val retrievalCitations = hits.mapIndexed { i, h -> Citation(i + 1, h.title, h.snippet, h.location) }
            if (sarvam.isConfigured) {
                val blocks = hits.mapIndexed { i, h ->
                    "[${i + 1}] (from \"${h.title}\"${h.location?.let { ", $it" } ?: ""})\n${h.snippet}"
                }.joinToString("\n\n")
                try {
                    val raw = sarvam.complete(Narrator.NARRATOR_SYSTEM, "Context passages:\n\n$blocks\n\nQuestion: $text")
                    val cleaned = Narrator.stripInvalidMarkers(raw, hits.size)
                    answer = cleaned
                    citations = Narrator.citedIndices(cleaned, hits.size).map { retrievalCitations[it - 1] }
                } catch (e: Exception) {
                    answer = "The Narrator rests (${e.message}). From your archives:\n\n" + retrievalAnswer(hits)
                    citations = retrievalCitations
                }
            } else {
                answer = "From your archives (add a Sarvam key in Settings for a spoken answer):\n\n" + retrievalAnswer(hits)
                citations = retrievalCitations
            }
        }
        award("knowledge_consulted", Catalogs.Xp.KNOWLEDGE_CONSULTED, refId = null)
        chatDao.insert(
            ChatMessageEntity(
                id = UUID.randomUUID().toString(), role = "assistant", content = answer,
                citationsJson = json.encodeToString(citations),
            ),
        )
    }

    private fun retrievalAnswer(hits: List<SearchHit>): String =
        hits.take(3).mapIndexed { i, h -> "[${i + 1}] ${h.snippet}…" }.joinToString("\n\n")

    // ---------- Weekly Review ----------

    fun observeReviews(): Flow<List<WeeklyReviewEntity>> = reviewDao.observeReviews()

    suspend fun generateWeeklyReview(): WeeklyReviewEntity {
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val ws = monday.toString()
        reviewDao.get(ws)?.let { return it }

        val startMillis = monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = xpDao.since(startMillis)
        val byKind = events.groupingBy { it.kind }.eachCount()
        val stats = WeekStats(
            weekStart = ws,
            xpEarned = events.sumOf { it.amount },
            questsCompleted = byKind["quest_completed"] ?: 0,
            habitCheckins = byKind["habit_checkin"] ?: 0,
            documentsProcessed = byKind["document_processed"] ?: 0,
            milestones = byKind["milestone_completed"] ?: 0,
        )
        val narrative = if (sarvam.isConfigured) {
            try {
                sarvam.complete(Narrator.REVIEW_SYSTEM, "This week's statistics: ${json.encodeToString(stats)}")
            } catch (e: Exception) {
                Narrator.offlineReviewNarrative(stats.xpEarned, stats.questsCompleted, stats.habitCheckins, stats.documentsProcessed)
            }
        } else {
            Narrator.offlineReviewNarrative(stats.xpEarned, stats.questsCompleted, stats.habitCheckins, stats.documentsProcessed)
        }
        val review = WeeklyReviewEntity(
            weekStart = ws,
            statsJson = json.encodeToString(stats),
            narrative = narrative,
            suggestionsJson = json.encodeToString(
                listOf("Pick one avoided milestone and schedule it first thing next week."),
            ),
        )
        reviewDao.upsert(review)
        award("weekly_review", Catalogs.Xp.WEEKLY_REVIEW, refId = ws)
        return review
    }

    fun weekStatsOf(review: WeeklyReviewEntity): WeekStats =
        try { json.decodeFromString(review.statsJson) } catch (e: Exception) { WeekStats(review.weekStart) }

    fun suggestionsOf(review: WeeklyReviewEntity): List<String> =
        try { json.decodeFromString(review.suggestionsJson) } catch (e: Exception) { emptyList() }

    // ---------- AI quest generation ----------

    fun observeDraftQuests(): Flow<List<QuestEntity>> = questDao.observeByStatus("draft")

    suspend fun acceptQuest(id: String) {
        questDao.get(id)?.let { if (it.status == "draft") questDao.upsert(it.copy(status = "active")) }
    }

    suspend fun generateQuests(count: Int = 3): Int {
        val drafts: List<QuestGen> = if (sarvam.isConfigured) {
            try {
                val recent = documentDao.readyDocuments().take(5).joinToString("; ") { it.title }
                val raw = sarvam.complete(
                    Narrator.QUESTMASTER_SYSTEM,
                    "Recent studies: $recent. Generate $count quests as a JSON array.",
                )
                Narrator.extractJsonArray(raw)?.let { json.decodeFromString<List<QuestGen>>(it) }
                    ?: offlineQuestPool(count)
            } catch (e: Exception) {
                offlineQuestPool(count)
            }
        } else {
            offlineQuestPool(count)
        }
        drafts.take(count).forEach { g ->
            val diff = if (g.difficulty.lowercase() in Catalogs.difficultyXp) g.difficulty.lowercase() else "normal"
            questDao.upsert(
                QuestEntity(
                    id = UUID.randomUUID().toString(),
                    title = g.title.ifBlank { "Unnamed quest" }.take(255),
                    description = g.description.take(2000),
                    difficulty = diff,
                    xpReward = Catalogs.difficultyXp.getValue(diff),
                    status = "draft",
                    source = "ai",
                ),
            )
        }
        return drafts.take(count).size
    }

    private fun offlineQuestPool(count: Int): List<QuestGen> =
        Narrator.questTemplates.shuffled().take(count).map { (t, d, diff) -> QuestGen(t, d, diff) }

    // ---------- data ownership: export / import ----------

    suspend fun exportBundle(): ExportBundle = ExportBundle(
        exportedAt = System.currentTimeMillis(),
        profile = profileDao.get(),
        xpEvents = xpDao.allEvents(),
        quests = questDao.allQuests(),
        habits = habitDao.allHabits(),
        checkins = habitDao.allCheckins(),
        goals = goalDao.allGoals(),
        milestones = goalDao.allMilestones(),
        achievements = catalogDao.achievements(),
        skills = catalogDao.allSkills(),
        collectibles = catalogDao.allCollectibles(),
        documents = documentDao.allDocuments(),
        chunks = documentDao.allChunks(),
        chat = chatDao.allMessages(),
        reviews = reviewDao.allReviews(),
        notes = noteDao.allNotes(),
        folders = folderDao.all(),
        attachments = attachmentDao.allAttachments(),
        goalProgress = goalDao.allProgress(),
        goalCheckpoints = goalDao.allCheckpoints(),
    )

    suspend fun exportJson(): String {
        val bundle = exportBundle()
        settings.recordBackup()
        return Json { prettyPrint = true }.encodeToString(bundle)
    }

    suspend fun exportMarkdown(): String {
        val b = exportBundle()
        return buildString {
            appendLine("# MindQuest — Export")
            appendLine("Exported ${LocalDate.now()}")
            b.profile?.let { appendLine("\n**Hero:** ${it.heroName} · Level ${it.level} · ${it.xp} XP") }
            appendLine("\n## Totals")
            appendLine("- Documents: ${b.documents.size}")
            appendLine("- Quests: ${b.quests.count { it.status == "completed" }} completed / ${b.quests.size} total")
            appendLine("- Habits: ${b.habits.size} · check-ins: ${b.checkins.size}")
            appendLine("- Goals: ${b.goals.size} · achievements: ${b.achievements.count { it.unlockedAt != null }}")
            if (b.goals.isNotEmpty()) {
                appendLine("\n## Story Arcs")
                b.goals.forEach { g -> appendLine("- ${g.title} (${g.status})") }
            }
            if (b.documents.isNotEmpty()) {
                appendLine("\n## Archives")
                b.documents.forEach { d -> appendLine("- ${d.title}${d.domain?.let { " — $it" } ?: ""}") }
            }
        }
    }

    /** Replace all on-device data with an imported bundle. Returns items restored. */
    suspend fun importJson(jsonStr: String): Int = importBundle(decodeBundle(jsonStr))

    fun decodeBundle(jsonStr: String): ExportBundle = json.decodeFromString<ExportBundle>(jsonStr)

    fun encodeBundle(bundle: ExportBundle): String = json.encodeToString(bundle)

    suspend fun importBundle(bundle: ExportBundle): Int {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        bundle.profile?.let { profileDao.upsert(it) }
        bundle.xpEvents.forEach { xpDao.insert(it) }
        bundle.quests.forEach { questDao.upsert(it) }
        bundle.habits.forEach { habitDao.upsert(it) }
        bundle.checkins.forEach { habitDao.insertCheckin(it) }
        bundle.goals.forEach { goalDao.upsertGoal(it) }
        bundle.milestones.forEach { goalDao.upsertMilestone(it) }
        if (bundle.achievements.isNotEmpty()) catalogDao.upsertAchievements(bundle.achievements)
        if (bundle.skills.isNotEmpty()) catalogDao.upsertSkills(bundle.skills)
        if (bundle.collectibles.isNotEmpty()) catalogDao.upsertCollectibles(bundle.collectibles)
        bundle.documents.forEach { documentDao.upsertDocument(it) }
        if (bundle.chunks.isNotEmpty()) documentDao.insertChunks(bundle.chunks)
        bundle.chat.forEach { chatDao.insert(it) }
        bundle.reviews.forEach { reviewDao.upsert(it) }
        bundle.notes.forEach { noteDao.upsert(it) }
        // Every table cleared above has to be refilled here. Folders and photo records were
        // once added to the export without being added to this list, which meant restoring
        // your own backup quietly deleted every folder you had made.
        bundle.folders.forEach { folderDao.upsert(it) }
        bundle.attachments.forEach { attachmentDao.upsert(it) }
        bundle.goalProgress.forEach { goalDao.upsertProgress(it) }
        bundle.goalCheckpoints.forEach { goalDao.upsertCheckpoint(it) }
        seedIfEmpty() // restore catalog rows if the bundle predates them
        // Restored rows carry their reminder times, but the alarms themselves lived in
        // WorkManager, which a restore onto a new phone starts without. Rebuild them.
        rearmNoteReminders()
        rearmHabitReminders()
        rearmGoalCheckins()
        return bundle.xpEvents.size + bundle.quests.size + bundle.habits.size +
            bundle.goals.size + bundle.documents.size
    }

    fun lastBackup(): Long = settings.lastBackup()

    // ---------- inbox: quick-capture notes ----------

    fun observeNotes(): Flow<List<NoteEntity>> = noteDao.observeNotes()

    /** Capture a line of text; optionally schedule a reminder notification. */
    suspend fun addNote(
        text: String,
        remindAt: Long? = null,
        category: String? = null,
        categoryChosen: Boolean = false,
        repeat: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val body = text.trim()
        noteDao.upsert(
            NoteEntity(
                id = id, text = body, remindAt = remindAt,
                category = category ?: Categories.classify(body),
                // Confirmed at capture, so nothing downstream may second-guess it.
                categoryLocked = categoryChosen,
                repeat = repeat?.takeIf { remindAt != null },
            ),
        )
        if (remindAt != null) Reminders.schedule(context, id, text.trim(), remindAt)
        TodayWidget.refresh(context)
        return id
    }

    /**
     * Tick a note off, or untick it. A repeating note is never left ticked: it rolls its
     * reminder forward to the next date and stays open, so the Inbox, the widget and the
     * notification's Done button — which all come through here — treat "rent on the 5th"
     * the same way. Returns the next reminder time when it rolled, so callers can say so.
     */
    suspend fun setNoteDone(id: String, done: Boolean): Long? {
        val note = noteDao.get(id) ?: return null
        val repeat = note.repeat
        val due = note.remindAt
        if (done && repeat != null && due != null) {
            val next = Cadences.nextOccurrence(repeat, due)
            noteDao.upsert(note.copy(done = false, remindAt = next))
            Reminders.cancel(context, id)
            Reminders.schedule(context, id, note.text, next)
            TodayWidget.refresh(context)
            return next
        }
        noteDao.upsert(note.copy(done = done))
        if (done) Reminders.cancel(context, id) // no point nagging about a finished errand
        TodayWidget.refresh(context)
        return null
    }

    /** Make a note's reminder repeat, or stop it repeating. Needs a reminder to repeat. */
    suspend fun setNoteRepeat(id: String, repeat: String?) {
        val note = noteDao.get(id) ?: return
        noteDao.upsert(note.copy(repeat = repeat, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Push a reminder back from the notification itself. The new time is written onto the
     * note, so the Inbox and the widget show when it will come back rather than the time
     * it was originally due. Not stamped as an edit — the note says the same thing.
     */
    suspend fun snoozeNote(id: String, minutes: Long) {
        val note = noteDao.get(id) ?: return
        val at = System.currentTimeMillis() + minutes * 60_000L
        noteDao.upsert(note.copy(remindAt = at))
        Reminders.cancel(context, id)
        Reminders.schedule(context, id, note.text, at)
        TodayWidget.refresh(context)
    }

    /** Set or clear a note's reminder, rescheduling the notification. */
    suspend fun setNoteReminder(id: String, remindAt: Long?) {
        val note = noteDao.get(id) ?: return
        noteDao.upsert(note.copy(remindAt = remindAt, updatedAt = System.currentTimeMillis()))
        Reminders.cancel(context, id)
        if (remindAt != null) Reminders.schedule(context, id, note.text, remindAt)
        TodayWidget.refresh(context)
    }

    /**
     * Change what a note says and when it should go off, in one move.
     *
     * The reminder is always torn down and rebuilt rather than left alone when the time
     * hasn't changed: the pending work carries the old wording as a fallback, so editing the
     * words without re-scheduling would leave a reminder that fires saying the wrong thing.
     */
    suspend fun editNote(id: String, text: String, remindAt: Long?, repeat: String?) {
        val note = noteDao.get(id) ?: return
        val body = text.trim().ifBlank { note.text }
        noteDao.upsert(
            note.copy(
                text = body,
                remindAt = remindAt,
                // Nothing to repeat without a time to repeat from.
                repeat = if (remindAt == null) null else repeat,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        Reminders.cancel(context, id)
        if (remindAt != null) Reminders.schedule(context, id, body, remindAt)
        TodayWidget.refresh(context)
    }

    suspend fun deleteNote(id: String) {
        Reminders.cancel(context, id)
        noteVectorDao.delete(id)
        attachmentDao.of("note", id).forEach { PhotoStore.delete(it.path) }
        attachmentDao.deleteAllOf("note", id)
        noteDao.delete(id)
        TodayWidget.refresh(context)
    }

    /** Promote a note into a real quest (so finishing it earns XP). */
    suspend fun noteToQuest(id: String, difficulty: String = "easy"): Boolean {
        val note = noteDao.get(id) ?: return false
        if (note.questId != null) return false
        val diff = if (difficulty in Catalogs.difficultyXp) difficulty else "easy"
        val questId = UUID.randomUUID().toString()
        questDao.upsert(
            QuestEntity(
                id = questId, title = note.text.take(255), difficulty = diff,
                xpReward = Catalogs.difficultyXp.getValue(diff), status = "active",
                source = "manual", dueAt = note.remindAt,
                // Inherited from the note and left unlocked, so that changing the note's
                // category later keeps the quest in step. See ingest() for the same reasoning.
                category = note.category ?: Categories.classify(note.text),
            ),
        )
        noteDao.upsert(note.copy(questId = questId))
        return true
    }

    /** Keep a note permanently: run it through the document pipeline so it becomes searchable. */
    suspend fun noteToArchive(id: String): Boolean {
        val note = noteDao.get(id) ?: return false
        if (note.docId != null) return false
        val docId = importTextNote(note.text.take(60), note.text, note.category)
        noteDao.upsert(note.copy(docId = docId))
        return true
    }

    suspend fun openNoteCount(): Int = noteDao.openCount()

    /** Override the auto-guessed category on a note. */
    suspend fun setNoteCategory(id: String, category: String) {
        val note = noteDao.get(id) ?: return
        noteDao.upsert(note.copy(category = category, categoryLocked = true))
    }

    suspend fun setQuestCategory(id: String, category: String) {
        val quest = questDao.get(id) ?: return
        questDao.upsert(quest.copy(category = category, categoryLocked = true))
    }

    /**
     * Change a quest's wording, difficulty or deadline. The XP follows the difficulty, so
     * downgrading an epic you over-estimated corrects the reward rather than leaving you
     * paid for work you didn't do.
     */
    suspend fun editQuest(id: String, title: String, difficulty: String, dueAt: Long?) {
        val quest = questDao.get(id) ?: return
        val diff = if (difficulty in Catalogs.difficultyXp) difficulty else quest.difficulty
        questDao.upsert(
            quest.copy(
                title = title.trim().ifBlank { quest.title },
                difficulty = diff,
                xpReward = Catalogs.difficultyXp[diff] ?: quest.xpReward,
                dueAt = dueAt,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        TodayWidget.refresh(context)
    }

    // ---------- photos ----------

    fun observeAttachments(kind: String): Flow<List<AttachmentEntity>> =
        attachmentDao.observeOfKind(kind)

    /**
     * Keep a photo against a quest or mission. [sourceUri] is copied into the app's own
     * storage by the caller via PhotoStore; this only records where it landed.
     */
    suspend fun addAttachment(kind: String, ownerId: String, path: String, caption: String? = null) {
        attachmentDao.upsert(
            AttachmentEntity(
                id = UUID.randomUUID().toString(),
                ownerKind = kind,
                ownerId = ownerId,
                path = path,
                caption = caption?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
    }

    /** Removing a photo deletes the file too — nothing else refers to it. */
    suspend fun deleteAttachment(id: String) {
        val attachment = attachmentDao.get(id) ?: return
        PhotoStore.delete(attachment.path)
        attachmentDao.delete(id)
    }

    suspend fun setDocumentCategory(id: String, category: String) {
        val doc = documentDao.get(id) ?: return
        documentDao.upsertDocument(doc.copy(domain = category, domainLocked = true))
    }

    /**
     * Categories are decided once, where the thing is written, and then travel with it.
     * This only fills gaps and repairs divergence; it never re-guesses something that
     * already has an answer.
     *
     * The gap case is legacy data captured before categories existed (and documents still
     * carrying the old "Something Realm" domain). The repair case is the bug this replaced:
     * a note promoted to the Archives used to be re-classified from scratch, so the same
     * words could land in two different categories depending on which screen you looked at.
     * The note is the source of truth; anything derived from it follows, unless the user has
     * set that item's category by hand.
     */
    suspend fun backfillCategories(): Int = withContext(Dispatchers.IO) {
        val known = Categories.all.map { it.id }.toSet()
        var changed = 0

        // --- gaps: legacy rows with no category at all ---
        noteDao.allNotes().filter { it.category == null }.forEach {
            noteDao.upsert(it.copy(category = Categories.classify(it.text)))
            changed++
        }
        questDao.allQuests().filter { it.category == null }.forEach {
            val text = listOfNotNull(it.title, it.description).joinToString(" ")
            questDao.upsert(it.copy(category = Categories.classify(text)))
            changed++
        }
        documentDao.allDocuments().filter { it.domain !in known }.forEach { doc ->
            val text = listOfNotNull(doc.title, doc.summary).joinToString(" ")
            documentDao.upsertDocument(doc.copy(domain = Categories.classify(text)))
            changed++
        }

        // --- repair: make derived items agree with the note they came from ---
        noteDao.allNotes().forEach { note ->
            val category = note.category ?: return@forEach
            note.questId?.let { questId ->
                val quest = questDao.get(questId)
                if (quest != null && !quest.categoryLocked && quest.category != category) {
                    questDao.upsert(quest.copy(category = category, categoryLocked = true))
                    changed++
                }
            }
            note.docId?.let { docId ->
                val doc = documentDao.get(docId)
                if (doc != null && !doc.domainLocked && doc.domain != category) {
                    documentDao.upsertDocument(doc.copy(domain = category, domainLocked = true))
                    changed++
                }
            }
        }
        changed
    }

    // ---------- user-made folders ----------

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    /**
     * The icon is guessed from the name rather than asked for: "Tuesday vegetable market"
     * should arrive already looking like what it is, and one more decision at the moment of
     * making a list is one reason not to make it.
     */
    suspend fun createFolder(name: String, icon: String? = null): String {
        val id = UUID.randomUUID().toString()
        val glyph = icon ?: Categories.of(Categories.classify(name)).icon
        folderDao.upsert(FolderEntity(id = id, name = name.trim(), icon = glyph))
        return id
    }

    suspend fun renameFolder(id: String, name: String) {
        val folder = folderDao.all().firstOrNull { it.id == id } ?: return
        folderDao.upsert(folder.copy(name = name.trim()))
    }

    /** Deleting a folder releases its notes rather than destroying them. */
    suspend fun deleteFolder(id: String) {
        folderDao.detachNotes(id)
        folderDao.delete(id)
    }

    suspend fun setNoteFolder(noteId: String, folderId: String?) {
        val note = noteDao.get(noteId) ?: return
        noteDao.upsert(note.copy(folderId = folderId))
    }

    /** Open note counts per user folder. */
    fun observeFolderCounts(): Flow<Map<String, Int>> =
        noteDao.observeNotes().map { notes ->
            notes.filter { !it.done && it.folderId != null }
                .groupingBy { it.folderId!! }
                .eachCount()
        }

    /**
     * The one capture path: take a sentence as spoken, typed or shared, and put it where it
     * belongs. Used by the Inbox mic, the home-screen mic widget and Share, so a line said
     * aloud lands exactly where the same line typed would.
     *
     *  - A goal — a number by a date, "80 kg by March 2027" — goes to Goals, with a monthly
     *    check-in, because a note can't track progress or ask how it's going.
     *  - Anything else is an Inbox note: its date becomes a reminder, and it is filed into
     *    one of your own folders when it shares a word with the folder's name ("vegetables"
     *    → "Tuesday vegetable market"), otherwise under its category.
     *
     * Inside a folder you opened yourself, the folder wins: you chose where it goes.
     */
    suspend fun captureNote(
        spoken: String,
        folderId: String? = null,
        category: String? = null,
    ): CaptureResult {
        if (folderId == null) {
            // A level by a date, in the unit of a goal already running, is a checkpoint on
            // the way to it — "90 kg by 1st October" inside "80 kg by March 2027" — not a
            // second goal and not an errand. Checked before goals for exactly that reason.
            GoalParse.checkpoint(spoken)?.let { cp ->
                goalForCheckpoint(cp, goalDao.allGoals())?.let { main ->
                    val met = addCheckpoint(main.id, cp.value, cp.date)
                    return CaptureResult(
                        main.id, GoalParse.format(cp.value, cp.unit), null, "general", null,
                        checkpoint = cp, checkpointOf = main.title, checkpointMet = met,
                    )
                }
            }
            GoalParse.detect(spoken)?.let { goal ->
                val id = createTargetGoal(goal, narrative = spoken)
                return CaptureResult(id, goal.title, null, "general", null, goal = goal)
            }
        }
        val parsed = DateParse.parse(spoken)
        val text = parsed.text.ifBlank { spoken.trim() }
        val chosen = category ?: Categories.classify(text)
        // Only when no folder or category was chosen for it: a choice always beats a guess.
        val folder = when {
            folderId != null -> folderDao.all().firstOrNull { it.id == folderId }
            category != null -> null
            else -> bestFolderFor(text)
        }
        // "Pay rent on the 5th every month" — the repeat only means something with a date.
        val repeat = parsed.repeat?.takeIf { parsed.dueAt != null }
        val id = addNote(
            text = text,
            remindAt = parsed.dueAt,
            category = chosen,
            categoryChosen = category != null || folderId != null,
            repeat = repeat,
        )
        if (folder != null) setNoteFolder(id, folder.id)
        return CaptureResult(id, text, parsed.dueAt, chosen, folder?.id, repeat, folderName = folder?.name)
    }

    /** The user folder a line belongs in, if it shares a word with the folder's name. */
    suspend fun bestFolderFor(text: String): FolderEntity? {
        val folders = folderDao.all()
        return FolderMatch.best(text, folders.map { it.name })?.let { folders[it] }
    }

    /** Open note counts per category, for the Inbox folders. */
    fun observeNoteFolders(): Flow<Map<String, Int>> =
        noteDao.observeNotes().map { notes ->
            notes.filter { !it.done }
                .groupingBy { it.category ?: "general" }
                .eachCount()
        }

    // ---------- today's agenda (home-screen widget) ----------

    /**
     * Everything due by the end of today: outstanding notes with a reminder, and active
     * quests with a deadline. Overdue items are included and marked, because something you
     * missed yesterday is more urgent than something due this evening, not less.
     */
    suspend fun todayAgenda(limit: Int = 6): List<AgendaItem> = withContext(Dispatchers.IO) {
        val endOfToday = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val fromNotes = noteDao.allNotes()
            .filter { !it.done && it.remindAt != null && it.remindAt <= endOfToday }
            .map {
                AgendaItem(
                    it.id, it.text, it.remindAt!!, it.remindAt < now,
                    Categories.of(it.category).icon, isQuest = false,
                )
            }

        val fromQuests = questDao.allQuests()
            .filter { it.status == "active" && it.dueAt != null && it.dueAt <= endOfToday }
            .map { AgendaItem(it.id, it.title, it.dueAt!!, it.dueAt < now, "⚔️", isQuest = true) }

        (fromNotes + fromQuests).sortedBy { it.dueAt }.take(limit)
    }

    /**
     * Tick an agenda row off from the home screen. Completing a quest here is the real
     * thing — the same XP, level-ups and achievements as completing it inside the app —
     * because a tick that quietly counted for less would make the widget a lie.
     * Returns XP awarded, or 0 for a plain note.
     */
    suspend fun completeAgendaItem(id: String, isQuest: Boolean): Int =
        if (isQuest) completeQuest(id).xpAwarded else { setNoteDone(id, true); 0 }

    // ---------- global search ----------

    /**
     * Search everything at once — notes, archives, quests, missions and arcs — so a half
     * remembered thing can be found without first knowing which screen it lives on.
     *
     * Archives and notes are matched on meaning as well as words — notes are where the
     * half-remembered things live. Quests, missions and arcs are short titles where a
     * substring match is both sufficient and predictable; running a transformer over a
     * dozen quest titles would cost more than it could possibly add.
     */
    suspend fun searchEverything(query: String, perKind: Int = 5): List<GlobalHit> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isBlank()) return@withContext emptyList()
            val needle = q.lowercase()
            val hits = mutableListOf<GlobalHit>()

            // Notes: every word you typed, or close enough in meaning. The words are matched
            // separately rather than as one phrase, so "dhaba colaba" finds "Gokul Dhaba,
            // Colaba" even though the comma breaks the phrase.
            val notes = noteDao.allNotes()
            indexNotes(notes)
            val semantic = embedder !== HashingEmbedder
            val queryVector = if (semantic) embedder.embed(q) else null
            val vectors = if (semantic) noteVectorDao.all().associateBy { it.noteId } else emptyMap()
            val words = needle.split(Regex("""\s+""")).filter { it.length >= 2 }
            val folderNames = folderDao.all().associate { it.id to it.name }

            notes
                .mapNotNull { note ->
                    val text = note.text.lowercase()
                    val allWords = words.isNotEmpty() && words.all { it in text }
                    val cosine = queryVector?.let { qv ->
                        vectors[note.id]?.let { Embeddings.cosine(qv, Embeddings.fromCsv(it.vectorCsv)) }
                    } ?: 0f
                    if (!allWords && cosine < NOTE_MEANING_FLOOR) return@mapNotNull null
                    // An exact hit outranks a merely similar one, but a strong match in meaning
                    // still beats a weak keyword hit buried in a long note.
                    val score = (if (allWords) 0.4f else 0f) + 0.6f * cosine
                    note to score
                }
                .sortedByDescending { it.second }
                .take(perKind)
                .forEach { (note, score) ->
                    val where = note.folderId?.let { folderNames[it] }?.let { "🗂 $it" }
                        ?: Categories.of(note.category).let { c -> "${c.icon} ${c.label}" }
                    hits += GlobalHit(
                        kind = GlobalKind.Note, title = note.text.take(120),
                        subtitle = where + if (note.done) " · done" else "",
                        refId = note.id, score = score,
                    )
                }

            // A folder is found by its name — "vegetable" should turn up the Tuesday market.
            folderDao.all()
                .filter { f -> words.isNotEmpty() && words.all { it in f.name.lowercase() } }
                .take(perKind)
                .forEach { hits += GlobalHit(GlobalKind.Folder, "${it.icon} ${it.name}", "folder", it.id) }

            search(q, perKind).forEach {
                hits += GlobalHit(
                    kind = GlobalKind.Document, title = it.title,
                    subtitle = it.snippet.take(120), refId = null, score = it.score,
                )
            }

            questDao.allQuests()
                .filter { needle in it.title.lowercase() }
                .take(perKind)
                .forEach { hits += GlobalHit(GlobalKind.Quest, it.title, it.status, it.id) }

            // A mission matches on what it is for as well as what it's called: searching
            // "80 kg" should find the monthly weigh-in.
            habitDao.allHabits()
                .filter { needle in it.title.lowercase() || needle in it.targetNote.orEmpty().lowercase() }
                .take(perKind)
                .forEach { h ->
                    val subtitle = h.targetNote?.let { "🎯 $it" } ?: "🔥 ${h.streak}"
                    hits += GlobalHit(GlobalKind.Habit, h.title, subtitle, h.id)
                }

            // A goal is found by its name or by the words it was first written in —
            // "crore" finds "Earn ₹1 crore", and so does "1crore inr earn".
            goalDao.allGoals()
                .filter { needle in it.title.lowercase() || needle in it.narrative.orEmpty().lowercase() }
                .take(perKind)
                .forEach { g ->
                    val subtitle = g.deadline?.let { "🎯 by ${Cadences.formatTarget(it)}" } ?: g.status
                    hits += GlobalHit(GlobalKind.Goal, g.title, subtitle, g.id)
                }

            hits
        }

    /**
     * Make sure every note has a vector for its current wording from the current embedder.
     * Cheap when nothing changed — a hash comparison per note — and it only embeds the ones
     * that are new, edited, or were computed by a different embedder, so it can run before
     * every search and at start-up without anyone noticing. Vectors for deleted notes go.
     */
    suspend fun indexNotes(notes: List<NoteEntity>? = null) = withContext(Dispatchers.IO) {
        if (embedder === HashingEmbedder) return@withContext // nothing meaningful to store
        val all = notes ?: noteDao.allNotes()
        val existing = noteVectorDao.all().associateBy { it.noteId }
        val label = embedder.label
        all.forEach { note ->
            val hash = note.text.hashCode()
            val current = existing[note.id]
            if (current == null || current.textHash != hash || current.embedder != label) {
                runCatching {
                    noteVectorDao.upsert(
                        NoteVectorEntity(
                            noteId = note.id,
                            vectorCsv = Embeddings.toCsv(embedder.embed(note.text)),
                            textHash = hash,
                            embedder = label,
                        ),
                    )
                }
            }
        }
        val live = all.map { it.id }.toSet()
        existing.keys.filter { it !in live }.forEach { noteVectorDao.delete(it) }
    }

    private companion object {
        /** Chunks re-embedded per database write during a reindex. */
        const val REINDEX_BATCH = 16

        /**
         * How close in meaning a note must be to turn up without sharing your words. MiniLM
         * puts related short sentences around 0.4–0.7 and unrelated ones under 0.2; 0.35
         * lets "that restaurant in Colaba" find the dhaba without every note matching.
         */
        const val NOTE_MEANING_FLOOR = 0.35f

        /** 09:00 — the check-in lands with the morning, not in the middle of the night. */
        const val DEFAULT_CHECKIN_MINUTE = 9 * 60

        /** A small reward for turning up to a check-in, whatever the number says. */
        const val GOAL_CHECKIN_XP = 10
    }
}

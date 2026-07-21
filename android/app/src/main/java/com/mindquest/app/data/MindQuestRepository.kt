package com.mindquest.app.data

import android.content.Context
import androidx.room.withTransaction
import com.mindquest.app.domain.Catalogs
import com.mindquest.app.domain.GameEngine
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

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

/**
 * The app's single offline data API. All screens go through this; nothing touches the network.
 * Ports the write-path semantics of backend/app/services/gamification.py onto Room.
 */
class MindQuestRepository(context: Context) {
    private val db = MindQuestDatabase.get(context)
    private val profileDao = db.profileDao()
    private val xpDao = db.xpEventDao()
    private val questDao = db.questDao()
    private val habitDao = db.habitDao()
    private val goalDao = db.goalDao()
    private val catalogDao = db.catalogDao()

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
            documentsReady = 0, // Phase 3
            documentsAny = 0, // Phase 3
            questsCompleted = questDao.completedCount(),
            epicCompleted = questDao.epicCompletedCount(),
            bestStreak = habitDao.maxBestStreak() ?: 0,
            consulted = xpDao.countKind("knowledge_consulted"),
            domains = 0, // Phase 3
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

    suspend fun createQuest(title: String, difficulty: String, description: String? = null, goalId: String? = null) {
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

    fun isCheckedInToday(habit: HabitEntity): Boolean = habit.lastCheckinDate == LocalDate.now().toString()

    suspend fun createHabit(title: String, cadence: String) {
        habitDao.upsert(HabitEntity(id = UUID.randomUUID().toString(), title = title.trim(), cadence = cadence))
    }

    suspend fun deleteHabit(id: String) = habitDao.delete(id)

    suspend fun checkin(id: String): CheckinResult = db.withTransaction {
        val habit = habitDao.get(id) ?: return@withTransaction CheckinResult()
        val today = LocalDate.now()
        val todayIso = today.toString()
        if (habitDao.checkinExists(id, todayIso) > 0) return@withTransaction CheckinResult(alreadyDone = true)

        val lastEpochDay = habit.lastCheckinDate?.let { LocalDate.parse(it).toEpochDay() }
        val newStreak = GameEngine.computeStreak(habit.cadence, habit.streak, lastEpochDay, today.toEpochDay())
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
}

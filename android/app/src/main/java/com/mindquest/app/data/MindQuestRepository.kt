package com.mindquest.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.mindquest.app.domain.Catalogs
import com.mindquest.app.domain.Embeddings
import com.mindquest.app.domain.GameEngine
import com.mindquest.app.domain.Ingestion
import com.mindquest.app.domain.Narrator
import com.mindquest.app.domain.SarvamClient
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    private val json = Json { ignoreUnknownKeys = true }

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

    // ---------- goals / story arcs ----------

    fun observeGoals(): Flow<List<GoalEntity>> = goalDao.observeGoals()
    fun observeAllMilestones(): Flow<List<MilestoneEntity>> = goalDao.observeAllMilestones()

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

    /** Import + process a picked file fully offline; returns the document id. */
    suspend fun importDocument(uri: Uri): String {
        val id = UUID.randomUUID().toString()
        val name = resolveName(uri)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        documentDao.upsertDocument(DocumentEntity(id = id, title = name, filename = name, mimeType = mime, status = "processing"))
        award("document_uploaded", Catalogs.Xp.DOCUMENT_UPLOADED, refId = id)
        try {
            val extracted = Ingestion.extract(context, uri, name, mime)
            val fullText = extracted.pages.joinToString("\n\n") { it.second }
            if (fullText.isBlank()) {
                error("No text could be extracted (a scanned file may have produced no OCR text).")
            }
            val chunks = Ingestion.chunk(extracted.pages)
            val summary = Ingestion.summarize(fullText)
            val tags = Ingestion.tags(fullText)
            val domain = Ingestion.domain(fullText)
            documentDao.insertChunks(
                chunks.map { (seq, text, loc) ->
                    ChunkEntity(
                        id = UUID.randomUUID().toString(), documentId = id, seq = seq, text = text,
                        location = loc, vectorCsv = Embeddings.toCsv(Embeddings.embed(text)),
                    )
                },
            )
            documentDao.upsertDocument(
                DocumentEntity(
                    id = id, title = name, filename = name, mimeType = mime, status = "ready",
                    summary = summary, domain = domain, tagsCsv = tags.joinToString(","),
                    ocrUsed = extracted.ocrUsed, charCount = fullText.length, chunkCount = chunks.size,
                ),
            )
            award("document_processed", Catalogs.Xp.DOCUMENT_PROCESSED, refId = id)
        } catch (e: Exception) {
            documentDao.getDocument(id)?.let {
                documentDao.upsertDocument(it.copy(status = "failed", error = e.message?.take(2000)))
            }
        }
        return id
    }

    suspend fun deleteDocument(id: String) {
        documentDao.deleteChunksOf(id)
        documentDao.deleteDocument(id)
    }

    suspend fun search(query: String, limit: Int = 8): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val qv = Embeddings.embed(query)
        val docs = documentDao.readyDocuments().associateBy { it.id }
        return documentDao.allChunks()
            .mapNotNull { c ->
                val doc = docs[c.documentId] ?: return@mapNotNull null
                SearchHit(doc.title, c.text.take(300), c.location, Embeddings.cosine(qv, Embeddings.fromCsv(c.vectorCsv)))
            }
            .sortedByDescending { it.score }
            .take(limit)
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
        val answer: String
        val citations: List<Citation>

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
    suspend fun importJson(jsonStr: String): Int {
        val bundle = json.decodeFromString<ExportBundle>(jsonStr)
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
        seedIfEmpty() // restore catalog rows if the bundle predates them
        return bundle.xpEvents.size + bundle.quests.size + bundle.habits.size +
            bundle.goals.size + bundle.documents.size
    }

    fun lastBackup(): Long = settings.lastBackup()
}

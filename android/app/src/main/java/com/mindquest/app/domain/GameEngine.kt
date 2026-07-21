package com.mindquest.app.domain

import kotlin.math.pow

/**
 * Pure progression math + achievement rules. Ported verbatim from
 * backend/app/services/gamification.py so on-device numbers match the web app.
 * No Android or DB types here — trivially unit-testable.
 */
object GameEngine {

    /** Total XP needed to reach [level]. Level 1 = 0, level 2 = 100, then 100*(n-1)^1.6. */
    fun xpRequiredForLevel(level: Int): Int =
        if (level <= 1) 0 else (100.0 * (level - 1).toDouble().pow(1.6)).toInt()

    fun levelForXp(xp: Long): Int {
        var level = 1
        while (xpRequiredForLevel(level + 1) <= xp) level++
        return level
    }

    /** +5% per consecutive day, capped at x2.5. */
    fun streakMultiplier(streak: Int): Double =
        minOf(1 + minOf(streak, 30) * 0.05, 2.5)

    /** New streak value given the previous check-in date, per cadence gap tolerance. */
    fun computeStreak(cadence: String, currentStreak: Int, lastDateEpochDay: Long?, todayEpochDay: Long): Int {
        if (lastDateEpochDay == null) return 1
        val gap = todayEpochDay - lastDateEpochDay
        val maxGap = when (cadence) {
            "weekly" -> 7L
            "weekdays" -> 3L
            else -> 1L
        }
        return if (gap in 1..maxGap) currentStreak + 1 else 1
    }

    /** Aggregates the achievement rules read (Phase 3/4 fields default to 0 until built). */
    data class Stats(
        val documentsReady: Int = 0,
        val documentsAny: Int = 0,
        val questsCompleted: Int = 0,
        val epicCompleted: Int = 0,
        val bestStreak: Int = 0,
        val consulted: Int = 0,
        val domains: Int = 0,
        val goalsCompleted: Int = 0,
        val level: Int = 1,
    )

    /** code -> predicate; mirrors backend RULES table. */
    val rules: Map<String, (Stats) -> Boolean> = mapOf(
        "first_light" to { s -> s.documentsAny >= 1 },
        "archivist" to { s -> s.documentsReady >= 10 },
        "lorekeeper" to { s -> s.documentsReady >= 50 },
        "first_quest" to { s -> s.questsCompleted >= 1 },
        "quest_veteran" to { s -> s.questsCompleted >= 25 },
        "epic_slayer" to { s -> s.epicCompleted >= 1 },
        "week_of_iron" to { s -> s.bestStreak >= 7 },
        "month_of_iron" to { s -> s.bestStreak >= 30 },
        "seeker" to { s -> s.consulted >= 1 },
        "cartographer" to { s -> s.domains >= 5 },
        "arc_closer" to { s -> s.goalsCompleted >= 1 },
        "level_5" to { s -> s.level >= 5 },
        "level_10" to { s -> s.level >= 10 },
    )
}

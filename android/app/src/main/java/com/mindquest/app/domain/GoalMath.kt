package com.mindquest.app.domain

import java.time.LocalDate
import kotlin.math.abs

/**
 * Where a target goal stands: how far along, how much is left, and what pace would get there.
 *
 * Shared by the Goals screen, the Dashboard and the check-in notification so all three give
 * the same answer. Pure arithmetic on numbers and dates — no Android — so it is tested.
 */
object GoalMath {

    data class Status(
        /** The most recent reading, or 0 for a from-zero goal nobody has logged yet. */
        val latest: Double?,
        /** Where progress is measured from: zero for money or books, the first weigh-in for weight. */
        val start: Double?,
        /** 0..1 along the way, or null until there is enough to say. */
        val fraction: Float?,
        /** Signed distance still to go (negative when the number must come down). */
        val remaining: Double?,
        /** Signed amount needed per month from here to make the deadline. */
        val perMonth: Double?,
        val reached: Boolean,
        val monthsLeft: Long,
    )

    fun status(
        target: Double?,
        unit: String,
        deadline: LocalDate,
        readings: List<Double>,
        today: LocalDate = LocalDate.now(),
    ): Status {
        val fromZero = GoalParse.accumulates(unit)
        val latest = readings.lastOrNull() ?: if (fromZero) 0.0 else null
        val start = if (fromZero) 0.0 else readings.firstOrNull()
        val monthsLeft = (Cadences.periodIndex("monthly", deadline) - Cadences.periodIndex("monthly", today))
            .coerceAtLeast(0)

        if (target == null || latest == null) {
            return Status(latest, start, null, null, null, reached = false, monthsLeft = monthsLeft)
        }
        val remaining = target - latest
        val reached = isReached(target, unit, start ?: latest, latest)
        val fraction = when {
            reached -> 1f
            start == null || start == target -> null
            else -> ((start - latest) / (start - target)).toFloat().coerceIn(0f, 1f)
        }
        // Count the current month as one still to use, so "this is the last month" asks for
        // everything that's left rather than dividing by zero.
        val perMonth = if (reached) null else remaining / (monthsLeft + 1).coerceAtLeast(1)
        return Status(latest, start, fraction, remaining, perMonth, reached, monthsLeft)
    }

    /**
     * Has the reading crossed the target, in the direction the goal runs? Money and counts
     * only go up. Weight goes whichever way the first reading says: from 90 towards 80 is
     * down, from 60 towards 65 is up.
     */
    fun isReached(target: Double, unit: String, start: Double, latest: Double): Boolean = when {
        GoalParse.accumulates(unit) -> latest >= target
        start > target -> latest <= target
        start < target -> latest >= target
        else -> abs(latest - target) < 1e-9
    }

    /** "lose 1 kg a month", "₹14.7 lakh a month", "2 books a month". */
    fun describePace(perMonth: Double, unit: String): String {
        val amount = GoalParse.format(abs(perMonth), unit)
        return when {
            GoalParse.accumulates(unit) -> "$amount a month"
            perMonth < 0 -> "lose $amount a month"
            else -> "gain $amount a month"
        }
    }

    /** "₹85 lakh to go", "4.5 kg to go". */
    fun describeRemaining(remaining: Double, unit: String): String =
        "${GoalParse.format(abs(remaining), unit)} to go"
}

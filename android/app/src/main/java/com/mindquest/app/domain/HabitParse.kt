package com.mindquest.app.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Recognises a habit — something you do every day or every week and want a streak for —
 * in the way you'd say it: "walk 5000 steps every day at 9pm", "take vitamins daily".
 *
 * The line it draws is about *when* the thing comes round. A habit's nudge lands at the
 * start of each period and stays quiet once it's done, so it suits "every day" and "every
 * week". Something tied to a particular day — "call mom every Sunday", "pay rent on the 5th
 * every month" — needs a reminder on that day instead, so it stays a repeating reminder.
 */
object HabitParse {

    data class Habit(
        val title: String,
        val cadence: String,
        /** Minutes past midnight for the nudge, when a time was said; null for no nudge. */
        val minuteOfDay: Int?,
    )

    private val WEEKDAY = Regex(
        """\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|tues|wed|thu|thurs|fri|sat|sun)\b""",
    )
    private val DAY_OF_MONTH = Regex("""\b\d{1,2}(st|nd|rd|th)\b""")

    /** "Daily" leading the line is describing a thing ("daily standup notes"), not a schedule. */
    private val LEADING_ADJECTIVE = Regex("""^(daily|weekly|weekdays)\b""")

    fun detect(raw: String, now: LocalDateTime = LocalDateTime.now()): Habit? {
        val lower = raw.trim().lowercase()
        if (lower.isEmpty() || LEADING_ADJECTIVE.containsMatchIn(lower)) return null
        val cadence = DateParse.REPEATS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(lower) }?.second
            ?: return null
        val habitLike = when (cadence) {
            "daily", "weekdays" -> true
            "weekly" -> !WEEKDAY.containsMatchIn(lower)
            else -> false
        }
        if (!habitLike || DAY_OF_MONTH.containsMatchIn(lower)) return null

        val parsed = DateParse.parse(raw, now)
        val minute = parsed.dueAt?.takeIf { parsed.timeGiven }?.let {
            val t = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
            t.hour * 60 + t.minute
        }
        val title = DateParse.stripSchedule(raw, now).trim().replaceFirstChar { it.uppercase() }
        if (title.isBlank()) return null
        return Habit(title, cadence, minute)
    }
}

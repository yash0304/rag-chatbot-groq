package com.mindquest.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Pulls a due date out of ordinary speech, so "buy sugar by 21st September" becomes a note
 * that says "buy sugar" and carries a reminder for the 21st.
 *
 * Dictation is the reason this exists: saying a sentence and then having to open a date
 * picker defeats the point of speaking in the first place. It handles the shapes people
 * actually say — "tomorrow", "by 21st September", "on Monday", "tonight at 9" — and
 * deliberately does no more than that. When nothing matches it returns the text untouched
 * with no date, which is always a safe outcome; a wrong date would be worse than none.
 */
object DateParse {

    /** [repeat] is a Cadences id when the sentence said how often — "every month". */
    data class Parsed(val text: String, val dueAt: Long?, val repeat: String? = null)

    /**
     * Phrases that say how often, most specific first so "every weekday" isn't read as a
     * bare "every week". "Every Sunday" is weekly; the weekday itself is picked up by the
     * weekday rule below, which is what supplies the first date.
     */
    private val REPEATS = listOf(
        Regex("""\b(every\s+weekday|on\s+weekdays|weekdays)\b""") to "weekdays",
        Regex("""\b(every\s+(single\s+)?day|everyday|daily)\b""") to "daily",
        Regex("""\bevery\s+(?=(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b)""") to "weekly",
        Regex("""\b(every\s+week|weekly)\b""") to "weekly",
        Regex("""\b(every\s+(6|six)\s+months|half[\s-]?yearly|twice\s+a\s+year)\b""") to "halfyearly",
        Regex("""\b(every\s+(3|three)\s+months|every\s+quarter|quarterly)\b""") to "quarterly",
        // Before "monthly": "half monthly" contains it.
        Regex("""\b(half[\s-]?monthly|twice\s+a\s+month|every\s+(15|fifteen)\s+days)\b""") to "halfmonthly",
        Regex("""\b(every\s+month|monthly)\b""") to "monthly",
        Regex("""\b(every\s+year|yearly|annually)\b""") to "yearly",
    )

    internal val MONTHS = mapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8, "september" to 9, "sept" to 9, "sep" to 9,
        "october" to 10, "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12,
    )

    private val WEEKDAYS = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )

    /** Default time for a date given without one — late enough to still be actionable. */
    private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    fun parse(raw: String, now: LocalDateTime = LocalDateTime.now()): Parsed {
        var text = raw.trim()
        if (text.isEmpty()) return Parsed(raw.trim(), null)

        val lower = text.lowercase()
        var date: LocalDate? = null
        var time: LocalTime? = null
        val cut = mutableListOf<IntRange>()

        // --- how often: "every month", "every Sunday", "daily" ---
        // Only kept if a date turns up as well — see the early return below, which hands
        // the sentence back untouched, so "daily standup notes" stays exactly as written.
        var repeat: String? = null
        for ((pattern, cadence) in REPEATS) {
            val m = pattern.find(lower) ?: continue
            repeat = cadence
            cut += m.range
            break
        }

        // --- explicit clock time: "at 5pm", "at 17:30", "by 9 pm" ---
        Regex("""\b(?:at|by|around)?\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""")
            .find(lower)?.let { m ->
                var hour = m.groupValues[1].toInt()
                val minute = m.groupValues[2].toIntOrNull() ?: 0
                val pm = m.groupValues[3] == "pm"
                if (pm && hour < 12) hour += 12
                if (!pm && hour == 12) hour = 0
                if (hour in 0..23 && minute in 0..59) {
                    time = LocalTime.of(hour, minute)
                    cut += m.range
                }
            }
        if (time == null) {
            Regex("""\b(?:at|by)\s+(\d{1,2}):(\d{2})\b""").find(lower)?.let { m ->
                val hour = m.groupValues[1].toInt()
                val minute = m.groupValues[2].toInt()
                if (hour in 0..23 && minute in 0..59) {
                    time = LocalTime.of(hour, minute)
                    cut += m.range
                }
            }
        }

        // --- "21st September" / "September 21" ---
        // Every match is tried, not just the first: "buy 2 kg sugar by 21st September" starts
        // with a number that isn't a date, and stopping there would lose the date entirely.
        // An explicit year ("21st March 2028") is taken as given rather than guessed.
        Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?([a-z]+)\b(?:\s*,?\s*(\d{4})\b)?""").findAll(lower)
            .firstOrNull { MONTHS.containsKey(it.groupValues[2]) }?.let { m ->
                val month = MONTHS.getValue(m.groupValues[2])
                val day = m.groupValues[1].toInt()
                date = m.groupValues[3].toIntOrNull()
                    ?.let { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
                    ?: safeDate(now.toLocalDate(), month, day)
                if (date != null) cut += m.range
            }
        if (date == null) {
            Regex("""\b([a-z]+)\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:\s*,?\s*(\d{4})\b)?""").findAll(lower)
                .firstOrNull { MONTHS.containsKey(it.groupValues[1]) }?.let { m ->
                    val month = MONTHS.getValue(m.groupValues[1])
                    val day = m.groupValues[2].toInt()
                    date = m.groupValues[3].toIntOrNull()
                        ?.let { year -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }
                        ?: safeDate(now.toLocalDate(), month, day)
                    if (date != null) cut += m.range
                }
        }

        // --- a bare day of the month: "on the 5th", "5th of every month" ---
        // The ordinal suffix is required, so "buy 2 kg" is never read as the 2nd. Lands on
        // the next 5th still ahead — this month's if there's time left in it, else next.
        if (date == null) {
            Regex("""\b(?:on\s+)?(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)\b""").find(lower)?.let { m ->
                val day = m.groupValues[1].toInt()
                if (day in 1..31) {
                    val today = now.toLocalDate()
                    val at = time ?: DEFAULT_TIME
                    var month = YearMonth.from(today)
                    for (k in 0..12) {
                        if (day <= month.lengthOfMonth()) {
                            val candidate = month.atDay(day)
                            val ahead = candidate.isAfter(today) ||
                                (candidate == today && at.isAfter(now.toLocalTime()))
                            if (ahead) {
                                date = candidate
                                break
                            }
                        }
                        month = month.plusMonths(1)
                    }
                    if (date != null) cut += m.range
                }
            }
        }

        // --- relative days ---
        if (date == null) {
            val today = now.toLocalDate()
            when {
                Regex("""\bday after tomorrow\b""").find(lower)?.also { cut += it.range } != null ->
                    date = today.plusDays(2)
                Regex("""\btomorrow\b""").find(lower)?.also { cut += it.range } != null ->
                    date = today.plusDays(1)
                Regex("""\b(tonight|this evening)\b""").find(lower)?.also { cut += it.range } != null -> {
                    date = today
                    if (time == null) time = LocalTime.of(20, 0)
                }
                Regex("""\btoday\b""").find(lower)?.also { cut += it.range } != null -> date = today
                else -> {
                    // Again every match, so "holiday on friday" isn't stopped by "holiday".
                    Regex("""\b(?:next|on|this)?\s*([a-z]+day|mon|tue|tues|wed|thu|thurs|fri|sat|sun)\b""")
                        .findAll(lower)
                        .firstOrNull { WEEKDAYS.containsKey(it.groupValues[1]) }?.let { m ->
                            val target = WEEKDAYS.getValue(m.groupValues[1])
                            // "on Tuesday" means the next one — never today, because a day
                            // that is already half gone is not what anyone means by it.
                            var d = today
                            do { d = d.plusDays(1) } while (d.dayOfWeek != target)
                            date = d
                            cut += m.range
                        }
                }
            }
        }

        // --- "in 2 hours" / "in 30 minutes" ---
        if (date == null && time == null) {
            Regex("""\bin\s+(\d{1,3})\s*(minute|minutes|min|mins|hour|hours|hr|hrs|day|days)\b""")
                .find(lower)?.let { m ->
                    val n = m.groupValues[1].toLong()
                    val moment = when (m.groupValues[2].take(3)) {
                        "min" -> now.plusMinutes(n)
                        "hou", "hrs", "hr" -> now.plusHours(n)
                        else -> now.plusDays(n)
                    }
                    cut += m.range
                    return Parsed(
                        clean(text, cut),
                        moment.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        repeat,
                    )
                }
        }

        if (date == null && time == null) return Parsed(text, null)

        val day = date ?: run {
            // A bare time that has already passed today plainly means tomorrow.
            val t = time ?: DEFAULT_TIME
            if (t.isAfter(now.toLocalTime())) now.toLocalDate() else now.toLocalDate().plusDays(1)
        }
        val moment = LocalDateTime.of(day, time ?: DEFAULT_TIME)
        text = clean(text, cut)
        return Parsed(text, moment.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), repeat)
    }

    /** A date in the past almost always means the same day next year. */
    private fun safeDate(today: LocalDate, month: Int, day: Int): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        return try {
            val d = LocalDate.of(today.year, month, day)
            if (d.isBefore(today)) d.plusYears(1) else d
        } catch (e: Exception) {
            null // 31 February and friends
        }
    }

    /**
     * Remove the phrases that became the date, then tidy the seams: the leftover connecting
     * word ("by", "on"), doubled spaces, and trailing punctuation.
     */
    private fun clean(text: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return text
        val keep = StringBuilder()
        text.forEachIndexed { i, ch -> if (ranges.none { i in it }) keep.append(ch) }
        return keep.toString()
            // One or more, because removing "every month" from "on the 5th of every month"
            // can leave connecting words stacked at the end ("… of the").
            .replace(Regex("""(\s+(by|on|at|before|around|of|the))+\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trim(',', '-', '.', ';')
            .trim()
            .ifEmpty { text.trim() }
    }
}

package com.mindquest.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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

    data class Parsed(val text: String, val dueAt: Long?)

    private val MONTHS = mapOf(
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
        Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?([a-z]+)\b""").findAll(lower)
            .firstOrNull { MONTHS.containsKey(it.groupValues[2]) }?.let { m ->
                val month = MONTHS.getValue(m.groupValues[2])
                date = safeDate(now.toLocalDate(), month, m.groupValues[1].toInt())
                if (date != null) cut += m.range
            }
        if (date == null) {
            Regex("""\b([a-z]+)\s+(\d{1,2})(?:st|nd|rd|th)?\b""").findAll(lower)
                .firstOrNull { MONTHS.containsKey(it.groupValues[1]) }?.let { m ->
                    val month = MONTHS.getValue(m.groupValues[1])
                    date = safeDate(now.toLocalDate(), month, m.groupValues[2].toInt())
                    if (date != null) cut += m.range
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
                    return Parsed(clean(text, cut), moment.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
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
        return Parsed(text, moment.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
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
            .replace(Regex("""\s+(by|on|at|before|around)\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trim(',', '-', '.', ';')
            .trim()
            .ifEmpty { text.trim() }
    }
}

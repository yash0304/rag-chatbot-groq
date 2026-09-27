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

    /**
     * [repeat] is a Cadences id when the sentence said how often — "every month".
     * [timeGiven] says a time of day was actually named ("at 9pm", "tonight"), as opposed to
     * the 09:00 a date gets when none was — a habit should only nudge at a time you chose.
     */
    data class Parsed(
        val text: String,
        val dueAt: Long?,
        val repeat: String? = null,
        val timeGiven: Boolean = false,
    )

    /**
     * Phrases that say how often, most specific first so "every weekday" isn't read as a
     * bare "every week". "Every Sunday" is weekly; the weekday itself is picked up by the
     * weekday rule below, which is what supplies the first date.
     */
    internal val REPEATS = listOf(
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
        val anchors = mutableListOf<Anchor>()
        DAY_MONTH.findAll(lower).filter { MONTHS.containsKey(it.groupValues[2]) }.forEach { m ->
            anchors += Anchor(m.range, MONTHS.getValue(m.groupValues[2]), m.groupValues[1].toInt(), m.groupValues[3].toIntOrNull())
        }
        MONTH_DAY.findAll(lower).filter { MONTHS.containsKey(it.groupValues[1]) }.forEach { m ->
            if (anchors.none { it.range.first <= m.range.last && m.range.first <= it.range.last }) {
                anchors += Anchor(m.range, MONTHS.getValue(m.groupValues[1]), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull())
            }
        }
        val stepMonths = STEP_MONTHS[repeat]
        val today = now.toLocalDate()
        val at = time ?: DEFAULT_TIME
        // "Starting 5th January" names the first one, so it is taken as said; without that the
        // date is read as a point on the cycle.
        val startsThen = Regex("""\b(starting|beginning)\b""").containsMatchIn(lower)
        if (stepMonths != null && !startsThen && anchors.any { it.year == null }) {
            // "Every 6 months, July 5 then January 5": each date is a point on the cycle, and
            // the reminder goes on the nearest one still ahead — January, not next July.
            val valid = anchors.filter { it.year == null && it.month in 1..12 && it.day in 1..31 }
            date = valid.mapNotNull { nextAligned(it.month, it.day, stepMonths, today, at, now.toLocalTime()) }.minOrNull()
            if (date != null) valid.forEachIndexed { i, anchor ->
                cut += if (i == 0) anchor.range else withConnector(lower, anchor.range)
            }
        } else {
            for (anchor in anchors) {
                date = anchor.year
                    ?.let { year -> runCatching { LocalDate.of(year, anchor.month, anchor.day) }.getOrNull() }
                    ?: safeDate(today, anchor.month, anchor.day)
                if (date != null) {
                    cut += anchor.range
                    break
                }
            }
        }

        // --- a bare day of the month: "on the 5th", "5th of every month" ---
        // The ordinal suffix is required, so "buy 2 kg" is never read as the 2nd. Lands on
        // the next 5th still ahead — this month's if there's time left in it, else next.
        if (date == null) {
            // Something that comes round monthly also takes a plain number: "haircut every
            // month at 15" is the 15th. Clock times were read above, so "at 9pm" is gone by now.
            val bare = if (repeat in MONTHLY_ISH) BARE_DAY_LOOSE else BARE_DAY
            bare.find(lower)?.let { m ->
                val day = m.groupValues[1].ifEmpty { m.groupValues[2] }.toInt()
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
                        timeGiven = true,
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
        return Parsed(text, moment.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), repeat, time != null)
    }

    /**
     * The sentence without its "how often" and "when" words: "walk 5000 steps every day at
     * 9pm" → "walk 5000 steps". Works even when there is no date to read, which [parse]
     * deliberately leaves untouched.
     */
    fun stripSchedule(raw: String, now: LocalDateTime = LocalDateTime.now()): String {
        val parsed = parse(raw, now)
        if (parsed.dueAt != null) return parsed.text
        val lower = raw.trim().lowercase()
        val cut = REPEATS.mapNotNull { (pattern, _) -> pattern.find(lower)?.range }.take(1)
        return clean(raw.trim(), cut)
    }

    private data class Anchor(val range: IntRange, val month: Int, val day: Int, val year: Int?)

    private val DAY_MONTH = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?([a-z]+)\b(?:\s*,?\s*(\d{4})\b)?""")
    private val MONTH_DAY = Regex("""\b([a-z]+)\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:\s*,?\s*(\d{4})\b)?""")
    private val BARE_DAY = Regex("""\b(?:on\s+)?(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)\b""")
    private val BARE_DAY_LOOSE = Regex(
        """\b(?:(?:on|at)\s+(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?|(?:the\s+)?(\d{1,2})(?:st|nd|rd|th))\b""" +
            """(?!\s*(?:am|pm|:|%|kg|km|rs|inr|min|mins|minutes?|hours?|hrs?|days?|weeks?|months?|years?)\b)""",
    )
    private val MONTHLY_ISH = setOf("monthly", "halfmonthly", "quarterly", "halfyearly")

    /** Months between occurrences, for the cadences that are counted in months. */
    private val STEP_MONTHS = mapOf("monthly" to 1, "quarterly" to 3, "halfyearly" to 6, "yearly" to 12)

    /**
     * The first date on the cycle through [month]/[day], every [stepMonths] months, that is
     * still ahead. Starts a year back so a cycle whose named date has passed this year still
     * finds its next point — July 5 every six months, asked in September, is January 5.
     */
    private fun nextAligned(
        month: Int, day: Int, stepMonths: Int,
        today: LocalDate, at: LocalTime, nowTime: LocalTime,
    ): LocalDate? {
        var ym = YearMonth.of(today.year - 1, month)
        repeat(40) {
            val d = ym.atDay(minOf(day, ym.lengthOfMonth()))
            if (d.isAfter(today) || (d == today && at.isAfter(nowTime))) return d
            ym = ym.plusMonths(stepMonths.toLong())
        }
        return null
    }

    /** A second date's range, widened to take the "then" / "and" joining it to the first. */
    private fun withConnector(lower: String, range: IntRange): IntRange {
        val before = Regex("""(?:,|&|\bthen\b|\band\b)\s*$""").find(lower.substring(0, range.first).trimEnd())
            ?: return range
        return before.range.first..range.last
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
            .replace(Regex("""(\s+(by|on|at|before|around|of|the|from|starting|then|and))+\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trim(',', '-', '.', ';')
            .trim()
            .ifEmpty { text.trim() }
    }
}

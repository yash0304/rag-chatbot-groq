package com.mindquest.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Recognises a goal in a sentence: a number you want to reach, and a date to reach it by.
 *
 * "80 kgs by March 2027" and "1crore inr earn by March 2027" are goals — somewhere to get
 * to, months away, measured by a number you'll check in on. "Buy vegetables by Thursday" and
 * "buy 2 kg sugar by 21st September" are errands, even though the second has a number and a
 * date too. The difference the rules below draw is the one a person would: an errand is a
 * thing you do (buy, pay, call) by a particular day soon; a goal is a level you reach by a
 * month or a year, usually far off.
 *
 * Like DateParse this is deliberately conservative. When unsure it says "not a goal", and the
 * line becomes an ordinary note — which the user can still promote with one tap.
 */
object GoalParse {

    data class Goal(
        /** Short canonical name: "Reach 80 kg", "Earn ₹1 crore", "Lose 10 kg". */
        val title: String,
        /** Where you want to end up, or null for a change ("lose 10 kg") awaiting a first reading. */
        val target: Double?,
        /** Signed change for "lose 10 kg" (−10) or "gain 5 kg" (+5); null for an absolute target. */
        val change: Double?,
        /** "kg", "₹", "km", "books"… */
        val unit: String,
        val deadline: LocalDate,
    )

    private const val NUM = """(\d{1,3}(?:,\d{2,3})+|\d+(?:\.\d+)?)"""
    private const val MULT = """(crores?|cr|lakhs?|lacs?|k|thousand|million|mn|billion|bn)"""

    private val MULTIPLIERS = mapOf(
        "crore" to 1e7, "crores" to 1e7, "cr" to 1e7,
        "lakh" to 1e5, "lakhs" to 1e5, "lac" to 1e5, "lacs" to 1e5,
        "k" to 1e3, "thousand" to 1e3,
        "million" to 1e6, "mn" to 1e6,
        "billion" to 1e9, "bn" to 1e9,
    )

    // Money. Crore and lakh are money on their own in Indian usage; "k" or "million" only
    // count when a currency is named, so "10k steps" is never read as rupees.
    private val MONEY_BEFORE = Regex("""(?:₹|\brs\.?|\binr)\s*$NUM\s*$MULT?\b""")
    private val MONEY_INDIAN = Regex("""\b$NUM\s*(crores?|cr|lakhs?|lacs?)\b""")
    private val MONEY_AFTER = Regex("""\b$NUM\s*$MULT?\s*(?:inr|rupees|rs\b|₹)""")

    private val WEIGHT_KG = Regex("""\b$NUM\s*(?:kgs?|kilos?|kilograms?)\b""")
    private val WEIGHT_LB = Regex("""\b$NUM\s*(?:lbs?|pounds)\b""")
    private val COUNT = Regex(
        """\b$NUM\s*(k)?\s*(books?|kms?|kilomet(?:er|re)s?|steps|followers|subscribers|clients|""" +
            """customers|students|pages|users|downloads|sales|marathons?|push-?ups)\b""",
    )

    /** Things you do, not levels you reach. A sentence starting with one is an errand. */
    private val ERRAND = Regex(
        """^(?:please\s+)?(?:remind\s+me\s+to\s+)?(?:buy|pick|order|pay|call|book|renew|collect|send|""" +
            """bring|fetch|return|submit|file|transfer|deposit|withdraw|get(?!\s+(?:to|down|up)\b))\b""",
    )

    private val LOSE = Regex("""\b(lose|drop|cut|shed|reduce)\b""")
    private val GAIN = Regex("""\b(gain|put\s+on|bulk\s+up)\b""")

    /** A deadline given to the day ("by 21st September") must be this far off to be a goal. */
    private const val PRECISE_MIN_DAYS = 45L

    fun detect(raw: String, today: LocalDate = LocalDate.now()): Goal? {
        val lower = raw.trim().lowercase(Locale.ROOT)
        if (lower.isEmpty() || ERRAND.containsMatchIn(lower)) return null

        val (deadline, precise) = deadline(lower, today) ?: return null
        if (!deadline.isAfter(today)) return null
        if (precise && deadline.isBefore(today.plusDays(PRECISE_MIN_DAYS))) return null

        val (value, unit) = target(lower) ?: return null
        if (value <= 0) return null

        val change = when {
            unit == "₹" -> null
            LOSE.containsMatchIn(lower) -> -value
            GAIN.containsMatchIn(lower) -> value
            else -> null
        }
        val verb = when {
            change != null -> if (change < 0) "Lose" else "Gain"
            Regex("""\b(earn|earning|make|making|income)\b""").containsMatchIn(lower) -> "Earn"
            Regex("""\b(save|saving|savings)\b""").containsMatchIn(lower) -> "Save"
            Regex("""\b(invest|investing)\b""").containsMatchIn(lower) -> "Invest"
            Regex("""\b(read|reading)\b""").containsMatchIn(lower) -> "Read"
            Regex("""\b(run|running)\b""").containsMatchIn(lower) -> "Run"
            Regex("""\b(walk|walking)\b""").containsMatchIn(lower) -> "Walk"
            else -> "Reach"
        }
        return Goal(
            title = "$verb ${format(value, unit)}",
            target = if (change != null) null else value,
            change = change,
            unit = unit,
            deadline = deadline,
        )
    }

    /** The amount and unit a sentence is aiming at, e.g. 1.0E7 to "₹", or 80.0 to "kg". */
    private fun target(lower: String): Pair<Double, String>? {
        MONEY_BEFORE.find(lower)?.let { m -> return amount(m.groupValues[1], m.groupValues[2]) to "₹" }
        MONEY_INDIAN.find(lower)?.let { m -> return amount(m.groupValues[1], m.groupValues[2]) to "₹" }
        MONEY_AFTER.find(lower)?.let { m -> return amount(m.groupValues[1], m.groupValues[2]) to "₹" }
        WEIGHT_KG.find(lower)?.let { m -> return number(m.groupValues[1]) to "kg" }
        WEIGHT_LB.find(lower)?.let { m -> return number(m.groupValues[1]) to "lb" }
        COUNT.find(lower)?.let { m ->
            val n = number(m.groupValues[1]) * if (m.groupValues[2] == "k") 1e3 else 1.0
            val unit = when (val u = m.groupValues[3]) {
                "km", "kms" -> "km"
                "book" -> "books"
                "marathon" -> "marathons"
                else -> if (u.startsWith("kilomet")) "km" else u.replace("-", "")
            }
            return n to unit
        }
        return null
    }

    /**
     * The date a sentence sets, and whether it was given to the day. "By March 2027" means by
     * the end of March, so it lands on the 31st — a month's goal shouldn't expire on the 1st.
     */
    private fun deadline(lower: String, today: LocalDate): Pair<LocalDate, Boolean>? {
        val months = DateParse.MONTHS

        // 21st March 2027 / 21 March, 2027
        Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?([a-z]+)\s*,?\s*(\d{4})\b""").findAll(lower)
            .firstOrNull { months.containsKey(it.groupValues[2]) }?.let { m ->
                dateOrNull(m.groupValues[3].toInt(), months.getValue(m.groupValues[2]), m.groupValues[1].toInt())
                    ?.let { return it to true }
            }
        // March 21st, 2027
        Regex("""\b([a-z]+)\s+(\d{1,2})(?:st|nd|rd|th)?\s*,?\s*(\d{4})\b""").findAll(lower)
            .firstOrNull { months.containsKey(it.groupValues[1]) }?.let { m ->
                dateOrNull(m.groupValues[3].toInt(), months.getValue(m.groupValues[1]), m.groupValues[2].toInt())
                    ?.let { return it to true }
            }
        // by March 2027 / by end of March / till Dec '27
        Regex("""\b(?:by|before|till|until|in|end\s+of)\s+(?:the\s+)?(?:end\s+of\s+)?([a-z]+)\s*,?\s*['’]?(\d{4}|\d{2})?\b""")
            .findAll(lower)
            .firstOrNull {
                // "By March 20th" names a day, not a month; that is DateParse's business.
                months.containsKey(it.groupValues[1]) &&
                    !Regex("""^\s*\d{1,2}(?:st|nd|rd|th)\b""").containsMatchIn(lower.substring(it.range.last + 1))
            }?.let { m ->
                val month = months.getValue(m.groupValues[1])
                val yearText = m.groupValues[2]
                val year = when {
                    yearText.length == 4 -> yearText.toInt()
                    yearText.length == 2 -> 2000 + yearText.toInt()
                    // No year: the next time that month ends.
                    YearMonth.of(today.year, month).atEndOfMonth().isBefore(today) -> today.year + 1
                    else -> today.year
                }
                return YearMonth.of(year, month).atEndOfMonth() to false
            }
        // by 2027 / by end of 2027
        Regex("""\b(?:by|before|till|until|in|end\s+of)\s+(?:the\s+)?(?:end\s+of\s+)?(\d{4})\b""").find(lower)?.let { m ->
            val year = m.groupValues[1].toInt()
            if (year in today.year..today.year + 50) return LocalDate.of(year, 12, 31) to false
        }
        // by the end of the year / by year-end
        if (Regex("""\b(?:end\s+of\s+(?:the\s+|this\s+)?year|year[\s-]?end)\b""").containsMatchIn(lower)) {
            return LocalDate.of(today.year, 12, 31) to false
        }
        // in 6 months / within a year
        Regex("""\b(?:in|within)\s+(\d{1,2}|a|an|one|two|three|four|five|six|nine|twelve|eighteen)\s+(months?|years?)\b""")
            .find(lower)?.let { m ->
                val n = WORD_NUMBERS[m.groupValues[1]] ?: m.groupValues[1].toIntOrNull() ?: return@let
                val date = if (m.groupValues[2].startsWith("month")) today.plusMonths(n.toLong()) else today.plusYears(n.toLong())
                return date to false
            }
        return null
    }

    private val WORD_NUMBERS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "nine" to 9, "twelve" to 12, "eighteen" to 18,
    )

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun number(text: String): Double = text.replace(",", "").toDouble()

    private fun amount(num: String, mult: String): Double =
        number(num) * (MULTIPLIERS[mult] ?: 1.0)

    /**
     * Read a check-in value — "84.5", "12 lakh", "1.2 cr", "₹15,00,000". Rupee goals accept the
     * Indian multipliers; everything else takes the first number. Null if there's no number.
     */
    fun parseValue(text: String, unit: String): Double? {
        val lower = text.trim().lowercase(Locale.ROOT)
        if (unit == "₹") {
            Regex("""$NUM\s*$MULT?\b""").find(lower)?.let { m ->
                return amount(m.groupValues[1], m.groupValues[2])
            }
            return null
        }
        return Regex(NUM).find(lower)?.let { number(it.groupValues[1]) }
    }

    /**
     * Goals where the number only grows — money earned, books read — are measured from zero.
     * Weight moves either way, so it is measured from wherever you started.
     */
    fun accumulates(unit: String): Boolean = unit != "kg" && unit != "lb"

    /** "₹1 crore", "₹12.5 lakh", "₹45,000", "80 kg", "20 books". */
    fun format(value: Double, unit: String): String {
        if (unit == "₹") {
            val v = abs(value)
            val sign = if (value < 0) "−" else ""
            return sign + when {
                v >= 1e7 -> "₹${trim(v / 1e7)} crore"
                v >= 1e5 -> "₹${trim(v / 1e5)} lakh"
                else -> "₹" + indianGrouping(v.roundToLong())
            }
        }
        return "${trim(value)} $unit"
    }

    /** Up to two decimals, and none at all for whole numbers: 80, 84.5, 1.25. */
    private fun trim(v: Double): String {
        val rounded = Math.round(v * 100) / 100.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString()
        else rounded.toString().trimEnd('0').trimEnd('.')
    }

    /** 1500000 → "15,00,000", the way amounts are written in India. */
    private fun indianGrouping(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val groups = rest.reversed().chunked(2).joinToString(",").reversed()
        return "$groups,$last3"
    }
}

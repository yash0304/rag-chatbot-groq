package com.mindquest.app.domain

/**
 * Finds phone numbers in a line people wrote for themselves — "puncture wala, 98 sector,
 * 98100 12345" — so the number can be tapped to call. Indian numbers are what it is tuned
 * for: 10-digit mobiles, landlines with their STD code, with or without +91, and written
 * with spaces or dashes wherever the writer liked.
 *
 * Fewer than ten digits is never a number: "sector 104", "5000 steps" and "₹2500" stay text.
 */
object PhoneFind {

    data class Phone(val range: IntRange, val dial: String)

    private val CANDIDATE = Regex("""(?<![\d])\+?\d[\d \-]{7,16}\d(?![\d])""")

    fun find(text: String): List<Phone> = CANDIDATE.findAll(text).mapNotNull { m ->
        val digits = m.value.filter { it.isDigit() }
        if (digits.length !in 10..13) return@mapNotNull null
        val dial = if (m.value.trimStart().startsWith("+")) "+$digits" else digits
        Phone(m.range, dial)
    }.toList()

    /** The line without its numbers — what to search a map for. */
    fun withoutPhones(text: String): String {
        val phones = find(text)
        if (phones.isEmpty()) return text
        val keep = StringBuilder()
        text.forEachIndexed { i, ch -> if (phones.none { i in it.range }) keep.append(ch) }
        return keep.toString().replace(Regex("""\s{2,}"""), " ").trim().trim(',', '-', ':').trim()
    }
}

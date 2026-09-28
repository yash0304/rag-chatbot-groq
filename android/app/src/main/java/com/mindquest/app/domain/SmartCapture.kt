package com.mindquest.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Asks the on-phone model where a line belongs — a to-do, something to remember, a daily
 * goal or a long-term goal — and reads its answer back defensively. The model is good at
 * the reading and poor at the rules, so everything it says is checked here: an unknown kind,
 * a date that has already passed or a made-up cadence is dropped, and anything that doesn't
 * parse falls back to the ordinary rules.
 */
object SmartCapture {

    data class Result(
        val kind: String, // task | thought | habit | goal
        val text: String,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val repeat: String? = null,
        /** Goals: "80 kg", "₹1 crore". */
        val target: String? = null,
        val deadline: LocalDate? = null,
    )

    private val KINDS = setOf("task", "thought", "habit", "goal")
    private val CADENCES = setOf("daily", "weekdays", "weekly", "halfmonthly", "monthly", "quarterly", "halfyearly", "yearly")

    fun system(today: LocalDate): String {
        val day = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH))
        return """
            You sort one line from a person's notes app. Today is $day.
            Decide what it is:
            - "task": something to do (buy, pay, call, book, fix, renew), once or on a schedule.
            - "thought": something to remember, not do — a place, a person, a phone number, an idea.
            - "habit": something done every day or every week to keep a streak (walk, read, vitamins).
            - "goal": a number to reach by a date (80 kg by March 2027, save 5 lakh by December).
            Reply with only one JSON object, no other words:
            {"kind":"task|thought|habit|goal","text":"the line, cleaned, without the date words",
             "date":"YYYY-MM-DD or null","time":"HH:MM 24-hour or null",
             "repeat":"daily|weekdays|weekly|halfmonthly|monthly|quarterly|halfyearly|yearly or null",
             "target":"for goals only, e.g. 80 kg, else null","deadline":"for goals only, YYYY-MM-DD, else null"}
        """.trimIndent()
    }

    /** The first complete {...} in [text], ignoring anything the model wrote around it. */
    fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(reply: String, today: LocalDate): Result? {
        val obj: JsonObject = try {
            json.parseToJsonElement(extractJson(reply) ?: return null).jsonObject
        } catch (e: Exception) {
            return null
        }
        fun str(key: String): String? =
            (obj[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() != "null" }

        val kind = str("kind")?.lowercase()?.takeIf { it in KINDS } ?: return null
        val text = str("text") ?: return null
        val date = str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { !it.isBefore(today) }
        val time = str("time")?.let { runCatching { LocalTime.parse(if (it.length == 4) "0$it" else it) }.getOrNull() }
        val repeat = str("repeat")?.lowercase()?.takeIf { it in CADENCES }
        val target = str("target")
        val deadline = str("deadline")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it.isAfter(today) }
        if (kind == "goal" && (target == null || deadline == null)) return null
        return Result(kind, text.replaceFirstChar { it.uppercase() }, date, time, repeat, target, deadline)
    }
}

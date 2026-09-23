package com.mindquest.app.domain

/**
 * Which of your own folders a line belongs in, by the words it shares with the folder's name.
 *
 * "Buy vegetables by Thursday" belongs in "Tuesday vegetable market" because of *vegetable*
 * — not because of the day, which is why days, months and other scheduling words never count.
 * Words match when one is the other with a short ending added (vegetable / vegetables,
 * gift / gifts), which is all the stemming a folder name needs.
 */
object FolderMatch {

    private val IGNORE = setOf(
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "january", "february", "march", "april", "june", "july", "august", "september",
        "october", "november", "december",
        "today", "tomorrow", "tonight", "morning", "evening", "week", "month", "year",
        "daily", "weekly", "monthly", "every", "before", "after", "with", "from", "this",
        "that", "some", "need", "want", "have", "list", "things", "stuff", "items", "folder",
        "remind", "remember",
    )

    fun words(text: String): List<String> =
        Regex("[a-z]+").findAll(text.lowercase())
            .map { it.value }
            .filter { it.length >= 4 && it !in IGNORE }
            .toList()

    private fun same(a: String, b: String): Boolean {
        val (short, long) = if (a.length <= b.length) a to b else b to a
        return long.startsWith(short) && long.length - short.length <= 3
    }

    /** Index into [folderNames] of the best match for [text], or null if nothing is shared. */
    fun best(text: String, folderNames: List<String>): Int? {
        val lineWords = words(text)
        if (lineWords.isEmpty()) return null
        return folderNames.withIndex()
            .map { (i, name) -> i to words(name).count { fw -> lineWords.any { same(it, fw) } } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }
}

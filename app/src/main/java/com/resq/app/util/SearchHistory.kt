package com.resq.app.util

import java.util.Locale

/** Keeps history compact without changing the user's capitalization. */
internal object SearchHistory {
    const val MAX_ENTRIES = 10
    private val whitespace = Regex("\\s+")

    fun record(entries: List<String>, query: String): List<String> {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return entries
        return sanitize(listOf(normalized) + entries)
    }

    fun sanitize(entries: List<String>): List<String> = entries
        .asSequence()
        .map(::normalize)
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .take(MAX_ENTRIES)
        .toList()

    private fun normalize(query: String): String = query.trim().replace(whitespace, " ")
}

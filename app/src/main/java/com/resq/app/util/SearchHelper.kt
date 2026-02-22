package com.resq.app.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for search functionality in RESQ.
 */
@Singleton
class SearchHelper @Inject constructor() {

    fun normalizeQuery(query: String): String {
        return query.lowercase().trim().replace(Regex("\\s+"), " ")
    }

    fun matchesQuery(text: String, query: String): Boolean {
        val normalizedText = normalizeQuery(text)
        val normalizedQuery = normalizeQuery(query)

        if (normalizedText.contains(normalizedQuery)) return true

        val queryWords = normalizedQuery.split(" ")
        return queryWords.all { word -> normalizedText.contains(word) }
    }

    fun calculateRelevanceScore(text: String, query: String): Int {
        val normalizedText = normalizeQuery(text)
        val normalizedQuery = normalizeQuery(query)

        var score = 0

        if (normalizedText == normalizedQuery) score += 100
        if (normalizedText.startsWith(normalizedQuery)) score += 50
        if (normalizedText.contains(normalizedQuery)) score += 25

        val queryWords = normalizedQuery.split(" ")
        val matchedWords = queryWords.count { normalizedText.contains(it) }
        score += matchedWords * 10

        return score
    }

    fun getSearchSuggestions(
        input: String,
        allTerms: List<String>,
        maxSuggestions: Int = 5
    ): List<String> {
        if (input.isBlank()) return emptyList()

        val normalizedInput = normalizeQuery(input)

        return allTerms
            .filter { matchesQuery(it, normalizedInput) }
            .sortedByDescending { calculateRelevanceScore(it, normalizedInput) }
            .take(maxSuggestions)
    }

    fun highlightMatches(text: String, query: String): String {
        if (query.isBlank()) return text

        val normalizedQuery = normalizeQuery(query)
        val pattern = Regex("(?i)($normalizedQuery)")

        return text.replace(pattern) { match -> "**${match.value}**" }
    }

    fun getDisasterSearchTerms(): List<String> {
        return listOf(
            "Earthquake", "Lindol", "Typhoon", "Bagyo",
            "Flood", "Baha", "Fire", "Sunog",
            "Volcano", "Bulkan", "Tsunami", "Daluyong",
            "Landslide", "Guho", "Storm Surge"
        )
    }

    fun getFirstAidSearchTerms(): List<String> {
        return listOf(
            "CPR", "Choking", "Heimlich", "Wound", "Bleeding", "Cut",
            "Burn", "Scald", "Fracture", "Broken bone", "Sprain",
            "Heat stroke", "Hypothermia", "Drowning", "Water rescue"
        )
    }
}
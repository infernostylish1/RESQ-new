package com.resq.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SearchHistoryTest {
    @Test
    fun recordingSearchTrimsAndCollapsesWhitespace() {
        assertEquals(
            listOf("Storm surge"),
            SearchHistory.record(emptyList(), " \tStorm  \n surge  ")
        )
    }

    @Test
    fun blankQueriesDoNotChangeHistory() {
        val history = listOf("Earthquake", "CPR")
        assertEquals(history, SearchHistory.record(history, " \t\n "))
    }

    @Test
    fun repeatedSearchMovesToFrontAndUsesLatestCapitalization() {
        assertEquals(
            listOf("CPR", "Earthquake", "Flood"),
            SearchHistory.record(listOf("Earthquake", "cpr", "Flood"), "CPR")
        )
    }

    @Test
    fun historyDropsOldestEntryAtLimit() {
        val history = (1..10).map { "Search $it" }
        assertEquals(
            listOf("Newest") + history.take(9),
            SearchHistory.record(history, "Newest")
        )
    }

    @Test
    fun duplicateDoesNotEvictUnrelatedHistoryAtLimit() {
        val history = (1..10).map { "Search $it" }
        assertEquals(
            listOf("search 10") + history.take(9),
            SearchHistory.record(history, "search 10")
        )
    }

    @Test
    fun restoredHistoryDiscardsBlankAndDuplicateEntriesInOrder() {
        assertEquals(
            listOf("Storm surge", "CPR", "Flood"),
            SearchHistory.sanitize(listOf("Storm  surge", "", "CPR", "storm surge", "  ", "Flood"))
        )
    }

    @Test
    fun caseDeduplicationIsIndependentOfDeviceLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals(listOf("FIRE"), SearchHistory.record(listOf("fire"), "FIRE"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}

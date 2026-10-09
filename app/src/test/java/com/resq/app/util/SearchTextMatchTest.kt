package com.resq.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextMatchTest {
    @Test
    fun `highlights every case insensitive occurrence using original text offsets`() {
        assertEquals(
            listOf(0..3, 6..9, 11..14),
            SearchTextMatch.ranges("Fire, FIRE fireproof", "fIrE")
        )
    }

    @Test
    fun `punctuation is treated as literal text rather than a regular expression`() {
        assertEquals(listOf(8..11), SearchTextMatch.ranges("Call at (02) 123", "(02)"))
        assertEquals(emptyList<IntRange>(), SearchTextMatch.ranges("Check all supplies", ".*"))
        assertEquals(listOf(5..5), SearchTextMatch.ranges("Step [1]", "["))
    }

    @Test
    fun `empty and missing queries have no highlights`() {
        assertTrue(SearchTextMatch.ranges("Earthquake", " \t ").isEmpty())
        assertTrue(SearchTextMatch.ranges("Earthquake", "flood").isEmpty())
        assertTrue(SearchTextMatch.ranges("", "flood").isEmpty())
    }

    @Test
    fun `query whitespace matches search normalization`() {
        assertEquals(listOf(7..17), SearchTextMatch.ranges("During storm surge warnings", "  storm   surge  "))
    }

    @Test
    fun `Unicode prefix does not shift span offsets`() {
        assertEquals(listOf(3..6, 8..11), SearchTextMatch.ranges("🚨 FIRE fire", "fire"))
    }

    @Test
    fun `snippet retains a match beyond the old truncation point`() {
        val content = "Prepare emergency supplies and review your household evacuation plan. ".repeat(4) +
            "Remember to practice CPR with a trained instructor before emergencies happen."
        val snippet = SearchTextMatch.snippet(content, "cpr", maxChars = 80)

        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.contains("CPR"))
        assertTrue(SearchTextMatch.ranges(snippet, "cpr").isNotEmpty())
        assertTrue(snippet.length <= 82)
    }

    @Test
    fun `snippet near the end includes the whole match and has no trailing ellipsis`() {
        val snippet = SearchTextMatch.snippet("Keep checking supplies. ".repeat(8) + "Fire", "fire", 40)

        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("Fire"))
        assertFalse(snippet.endsWith("…"))
    }

    @Test
    fun `long query is not clipped to the usual snippet size`() {
        val query = "earthquake preparedness and evacuation procedures"
        val snippet = SearchTextMatch.snippet("Read about $query today.", query, maxChars = 12)

        assertTrue(snippet.contains(query))
        assertEquals(listOf(1..query.length), SearchTextMatch.ranges(snippet, query))
    }

    @Test
    fun `short descriptions stay intact and absent queries use a leading preview`() {
        assertEquals("Call for help", SearchTextMatch.snippet("Call for help", "help"))
        assertEquals("Keep the…", SearchTextMatch.snippet("Keep the exit clear", "missing", 8))
        assertEquals("Keep the…", SearchTextMatch.snippet("Keep the exit clear", "", 8))
        assertEquals("", SearchTextMatch.snippet("", "fire"))
    }

    @Test
    fun `snippet normalizes multiline descriptions and preserves emoji`() {
        assertEquals("CPR helps", SearchTextMatch.snippet("  CPR\n\t helps  ", "cpr"))
        assertEquals("Use 🚨…", SearchTextMatch.snippet("Use 🚨 for emergencies", "", 5))
    }
}

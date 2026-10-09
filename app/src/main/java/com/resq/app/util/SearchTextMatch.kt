package com.resq.app.util

/** Literal search matching shared by result highlights and contextual snippets. */
object SearchTextMatch {
    private val whitespace = Regex("\\s+")

    fun normalizeQuery(query: String): String = query.trim().replace(whitespace, " ")

    /** UTF-16 ranges can be applied directly to Android text spans. */
    fun ranges(text: String, query: String): List<IntRange> {
        val needle = normalizeQuery(query)
        if (needle.isEmpty()) return emptyList()

        val matches = mutableListOf<IntRange>()
        var start = text.indexOf(needle, ignoreCase = true)
        while (start >= 0) {
            matches += start until start + needle.length
            start = text.indexOf(needle, start + needle.length, ignoreCase = true)
        }
        return matches
    }

    /** Keep the first match visible, even when it occurs deep in a description. */
    fun snippet(text: String, query: String, maxChars: Int = 120): String {
        require(maxChars > 0)
        val content = text.trim().replace(whitespace, " ")
        val needle = normalizeQuery(query)
        val matchStart = if (needle.isEmpty()) -1 else content.indexOf(needle, ignoreCase = true)
        val width = maxOf(maxChars, if (matchStart >= 0) needle.length else 0)
        if (content.length <= width) return content

        var start = if (matchStart < 0) 0 else {
            (matchStart - (width - needle.length) / 2)
                .coerceIn(0, content.length - width)
        }
        var end = start + width

        // Avoid partial words around the match without ever clipping the match itself.
        if (matchStart >= 0) {
            while (start > 0 && start < matchStart && !content[start - 1].isWhitespace()) start++
            val matchEnd = matchStart + needle.length
            while (end < content.length && end > matchEnd && !content[end].isWhitespace()) end--
        }

        // A window edge must not separate the halves of a surrogate pair.
        if (start > 0 && content[start].isLowSurrogate() && content[start - 1].isHighSurrogate()) start--
        if (end < content.length && content[end - 1].isHighSurrogate() && content[end].isLowSurrogate()) end++

        return buildString {
            if (start > 0) append('\u2026')
            append(content.substring(start, end).trim())
            if (end < content.length) append('\u2026')
        }
    }
}

package com.resq.app.data.repository

import android.content.Context
import androidx.core.content.edit
import com.resq.app.util.SearchHistory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Locally stored searches in most-recent-first order. */
@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _history = MutableStateFlow(
        SearchHistory.sanitize(
            (0 until SearchHistory.MAX_ENTRIES).mapNotNull { index ->
                preferences.getString("$ENTRY_PREFIX$index", null)
            }
        )
    )
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun record(query: String) {
        val updated = SearchHistory.record(_history.value, query)
        if (updated == _history.value) return
        save(updated)
    }

    fun clear() {
        save(emptyList())
    }

    private fun save(entries: List<String>) {
        // One edit preserves ordering and atomically replaces the previous stored list.
        preferences.edit {
            clear()
            entries.forEachIndexed { index, entry ->
                putString("$ENTRY_PREFIX$index", entry)
            }
        }
        _history.value = entries
    }

    private companion object {
        const val PREFERENCES_NAME = "global_search_history"
        const val ENTRY_PREFIX = "query_"
    }
}

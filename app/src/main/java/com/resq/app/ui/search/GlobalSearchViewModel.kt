// com/resq/app/ui/search/GlobalSearchViewModel.kt
package com.resq.app.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.repository.GlobalSearchRepository
import com.resq.app.data.repository.SearchHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GlobalSearchUiState(
    val query: String = "",
    val results: List<GlobalSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val isEmpty: Boolean = false,
    val isIdle: Boolean = true          // true when no query has been entered yet
)

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val repository: GlobalSearchRepository,
    private val historyRepository: SearchHistoryRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
    val history: StateFlow<List<String>> = historyRepository.history

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<GlobalSearchUiState> = query
        .flatMapLatest { q ->
            flow {
                if (q.isBlank()) {
                    emit(GlobalSearchUiState(query = q))
                    return@flow
                }

                // Cancel the previous search as soon as the text changes, including clear.
                // Only the new lookup is delayed, so stale results cannot replace idle state.
                emit(GlobalSearchUiState(query = q, isLoading = true, isIdle = false))
                delay(300L)
                emitAll(
                    repository.search(q).map { results ->
                        GlobalSearchUiState(
                            query     = q,
                            results   = results,
                            isLoading = false,
                            isEmpty   = results.isEmpty(),
                            isIdle    = false
                        )
                    }
                )
            }
        }
        .stateIn(
            scope            = viewModelScope,
            started          = SharingStarted.WhileSubscribed(5_000),
            initialValue     = GlobalSearchUiState(
                query = query.value,
                isLoading = query.value.isNotBlank(),
                isIdle = query.value.isBlank()
            )
        )

    fun onQueryChanged(query: String) {
        savedStateHandle[KEY_QUERY] = query
    }

    /** Navigation arguments seed a new search only once, even if it is later cleared. */
    fun initializeQuery(initialQuery: String?) {
        if (savedStateHandle.get<Boolean>(KEY_INITIALIZED) == true) return
        savedStateHandle[KEY_INITIALIZED] = true
        if (!initialQuery.isNullOrBlank() && query.value.isBlank()) {
            onQueryChanged(initialQuery)
        }
    }

    /** Record deliberate searches, rather than every partially typed query. */
    fun recordSearch() {
        historyRepository.record(query.value)
    }

    fun clearHistory() {
        historyRepository.clear()
    }

    private companion object {
        const val KEY_QUERY = "global_search_query"
        const val KEY_INITIALIZED = "global_search_initialized"
    }
}

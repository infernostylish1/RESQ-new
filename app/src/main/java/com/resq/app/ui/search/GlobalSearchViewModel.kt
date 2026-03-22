// com/resq/app/ui/search/GlobalSearchViewModel.kt
package com.resq.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.repository.GlobalSearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    private val repository: GlobalSearchRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<GlobalSearchUiState> = _query
        .debounce(300L)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(GlobalSearchUiState(query = q, isIdle = true))
            } else {
                repository.search(q)
                    .map { results ->
                        GlobalSearchUiState(
                            query     = q,
                            results   = results,
                            isLoading = false,
                            isEmpty   = results.isEmpty(),
                            isIdle    = false
                        )
                    }
                    .onStart {
                        emit(
                            GlobalSearchUiState(
                                query     = q,
                                isLoading = true,
                                isIdle    = false
                            )
                        )
                    }
            }
        }
        .stateIn(
            scope            = viewModelScope,
            started          = SharingStarted.WhileSubscribed(5_000),
            initialValue     = GlobalSearchUiState()
        )

    fun onQueryChanged(query: String) {
        _query.value = query
    }
}
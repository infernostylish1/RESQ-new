package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.entity.DisasterTipEntity
import com.resq.app.data.repository.DisasterRepository
import com.resq.app.data.repository.DisasterTipRepository
import com.resq.app.data.repository.UserSettingsRepository
import com.resq.app.ui.adapter.TipAdapter.TipWithCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisasterTipsViewModel @Inject constructor(
    private val disasterTipRepository: DisasterTipRepository,
    private val disasterRepository: DisasterRepository,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisasterTipsUiState())
    val uiState: StateFlow<DisasterTipsUiState> = _uiState.asStateFlow()

    private val _currentTip = MutableStateFlow<DisasterTipEntity?>(null)
    val currentTip: StateFlow<DisasterTipEntity?> = _currentTip.asStateFlow()

    /** Flow expected by TipsFragment — the list currently displayed. */
    private val _tipsWithCategory = MutableStateFlow<List<TipWithCategory>>(emptyList())
    val tipsWithCategory: StateFlow<List<TipWithCategory>> = _tipsWithCategory.asStateFlow()

    private var currentTipIndex = 0
    private var allTipsList: List<DisasterTipEntity> = emptyList()
    private var showingFavorites = false

    init {
        loadTips()
        loadRandomTip()
    }

    /**
     * Maps a list of DisasterTipEntity to TipWithCategory by looking up the
     * disaster name from the DisasterRepository.
     */
    private suspend fun mapToTipWithCategory(tips: List<DisasterTipEntity>): List<TipWithCategory> {
        val disasters = disasterRepository.getAllDisasters().first()
        val disasterMap = disasters.associateBy { it.id }
        return tips.map { tip ->
            TipWithCategory(
                tip = tip,
                categoryName = disasterMap[tip.disasterId]?.name ?: "General"
            )
        }
    }

    private fun loadTips() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val settings = userSettingsRepository.getSettings().first()
                val languageCode = settings?.preferredLanguage ?: "en"

                disasterTipRepository.getAllTips().collect { tips ->
                    allTipsList = tips
                    _tipsWithCategory.value = mapToTipWithCategory(tips)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allTips = tips,
                        filteredTips = tips,
                        languageCode = languageCode
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load tips: ${e.message}"
                )
            }
        }
    }

    private fun loadRandomTip() {
        viewModelScope.launch {
            try {
                val tips = disasterTipRepository.getRandomTips(1)
                if (tips.isNotEmpty()) {
                    _currentTip.value = tips.first()
                    currentTipIndex = allTipsList.indexOfFirst { it.id == tips.first().id }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load tip: ${e.message}"
                )
            }
        }
    }

    /** Called by TipsFragment "All Tips" tab. */
    fun loadAllTips() {
        showingFavorites = false
        viewModelScope.launch {
            _tipsWithCategory.value = mapToTipWithCategory(_uiState.value.allTips)
            _uiState.value = _uiState.value.copy(
                filteredTips = _uiState.value.allTips
            )
        }
    }

    /** Called by TipsFragment "Favorites" tab. */
    fun loadFavoriteTips() {
        showingFavorites = true
        viewModelScope.launch {
            val favorites = _uiState.value.allTips.filter { it.isFavorite }
            _tipsWithCategory.value = mapToTipWithCategory(favorites)
            _uiState.value = _uiState.value.copy(filteredTips = favorites)
        }
    }

    /** Called by TipsFragment when the favorite icon on a tip is tapped. */
    fun toggleFavorite(tip: DisasterTipEntity) {
        viewModelScope.launch {
            val updated = tip.copy(isFavorite = !tip.isFavorite)
            disasterTipRepository.updateTip(updated)

            // Update in-memory lists
            allTipsList = allTipsList.map { if (it.id == tip.id) updated else it }
            val allUpdated = _uiState.value.allTips.map { if (it.id == tip.id) updated else it }
            _uiState.value = _uiState.value.copy(allTips = allUpdated)

            // Refresh displayed list
            if (showingFavorites) {
                loadFavoriteTips()
            } else {
                _tipsWithCategory.value = mapToTipWithCategory(allUpdated)
                _uiState.value = _uiState.value.copy(filteredTips = allUpdated)
            }
        }
    }

    /** Called by TipsFragment when search is cleared. */
    fun clearSearch() {
        _uiState.value = _uiState.value.copy(searchQuery = "")
        if (showingFavorites) {
            loadFavoriteTips()
        } else {
            loadAllTips()
        }
    }

    fun getNextTip() {
        if (allTipsList.isEmpty()) return
        currentTipIndex = (currentTipIndex + 1) % allTipsList.size
        _currentTip.value = allTipsList[currentTipIndex]
    }

    fun getPreviousTip() {
        if (allTipsList.isEmpty()) return
        currentTipIndex = if (currentTipIndex > 0) currentTipIndex - 1 else allTipsList.size - 1
        _currentTip.value = allTipsList[currentTipIndex]
    }

    fun getRandomTip() {
        loadRandomTip()
    }

    fun filterByDisaster(disasterId: Long?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedDisasterId = disasterId)

            if (disasterId == null) {
                _tipsWithCategory.value = mapToTipWithCategory(_uiState.value.allTips)
                _uiState.value = _uiState.value.copy(
                    filteredTips = _uiState.value.allTips
                )
            } else {
                disasterTipRepository.getTipsByDisasterId(disasterId).collect { tips ->
                    _tipsWithCategory.value = mapToTipWithCategory(tips)
                    _uiState.value = _uiState.value.copy(filteredTips = tips)
                }
            }
        }
    }

    fun searchTips(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searchQuery = query)

            val source = if (showingFavorites) {
                _uiState.value.allTips.filter { it.isFavorite }
            } else {
                _uiState.value.allTips
            }

            if (query.isBlank()) {
                _tipsWithCategory.value = mapToTipWithCategory(source)
                _uiState.value = _uiState.value.copy(filteredTips = source)
            } else {
                val filtered = source.filter { tip ->
                    tip.tipText.contains(query, ignoreCase = true) ||
                            tip.tipTextTagalog.contains(query, ignoreCase = true)
                }
                _tipsWithCategory.value = mapToTipWithCategory(filtered)
                _uiState.value = _uiState.value.copy(filteredTips = filtered)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getTipForTTS(): String {
        val tip = _currentTip.value ?: return ""
        val isTagalog = _uiState.value.languageCode == "tl"
        return if (isTagalog) tip.tipTextTagalog else tip.tipText
    }
}

data class DisasterTipsUiState(
    val isLoading: Boolean = true,
    val allTips: List<DisasterTipEntity> = emptyList(),
    val filteredTips: List<DisasterTipEntity> = emptyList(),
    val selectedDisasterId: Long? = null,
    val searchQuery: String = "",
    val languageCode: String = "en",
    val error: String? = null
)
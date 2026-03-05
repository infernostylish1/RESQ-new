package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.entity.DisasterTipEntity
import com.resq.app.data.entity.UserProfileEntity          // CHANGED: new import
import com.resq.app.data.repository.DisasterRepository
import com.resq.app.data.repository.DisasterTipRepository
import com.resq.app.data.repository.UserProfileRepository  // CHANGED: new import
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
    private val userSettingsRepository: UserSettingsRepository,
    private val userProfileRepository: UserProfileRepository  // CHANGED: injected
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisasterTipsUiState())
    val uiState: StateFlow<DisasterTipsUiState> = _uiState.asStateFlow()

    private val _currentTip = MutableStateFlow<DisasterTipEntity?>(null)
    val currentTip: StateFlow<DisasterTipEntity?> = _currentTip.asStateFlow()

    private val _tipsWithCategory = MutableStateFlow<List<TipWithCategory>>(emptyList())
    val tipsWithCategory: StateFlow<List<TipWithCategory>> = _tipsWithCategory.asStateFlow()

    private var currentTipIndex = 0
    private var allTipsList: List<DisasterTipEntity> = emptyList()

    // CHANGED: track which tab is active using a clear enum instead of a boolean.
    private var activeTab = Tab.RECOMMENDED

    // CHANGED: cache the profile so loadRecommendedTips() can be called without
    // hitting the DB every time the tab is re-selected or search is cleared.
    private var cachedProfile: UserProfileEntity? = null

    private enum class Tab { RECOMMENDED, ALL }

    init {
        loadInitialData()
    }

    // CHANGED: load profile and settings together before splitting tip loading.
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val settings     = userSettingsRepository.getSettings().first()
                val languageCode = settings?.preferredLanguage ?: "en"
                val profile      = userProfileRepository.getUserProfile().first()
                cachedProfile    = profile

                _uiState.value = _uiState.value.copy(languageCode = languageCode)

                // Start on the Recommended tab.
                loadRecommendedTips()

                // Pre-load all tips into allTipsList for getNextTip / getPreviousTip.
                val all = disasterTipRepository.getAllTipsOnce()
                allTipsList = all
                _uiState.value = _uiState.value.copy(allTips = all)

                loadRandomTip()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error     = "Failed to load tips: ${e.message}"
                )
            }
        }
    }

    /**
     * Maps tips to TipWithCategory by joining against the disaster list.
     * Identical logic to the original — extracted here for reuse.
     */
    private suspend fun mapToTipWithCategory(tips: List<DisasterTipEntity>): List<TipWithCategory> {
        val disasters   = disasterRepository.getAllDisasters().first()
        val disasterMap = disasters.associateBy { it.id }
        return tips.map { tip ->
            TipWithCategory(
                tip          = tip,
                categoryName = disasterMap[tip.disasterId]?.name ?: "General"
            )
        }
    }

    // CHANGED: new primary loader — fetches tips recommended for the user profile.
    // Falls back to random tips when no profile exists (pre-onboarding).
    private suspend fun loadRecommendedTips() {
        val profile = cachedProfile
        val tips = if (profile != null) {
            disasterTipRepository.getRecommendedTips(profile, limit = 50)
        } else {
            disasterTipRepository.getRandomTips(50)
        }
        _tipsWithCategory.value = mapToTipWithCategory(tips)
        _uiState.value = _uiState.value.copy(
            isLoading     = false,
            filteredTips  = tips
        )
    }

    private fun loadRandomTip() {
        viewModelScope.launch {
            try {
                val tips = disasterTipRepository.getRandomTips(1)
                if (tips.isNotEmpty()) {
                    _currentTip.value  = tips.first()
                    currentTipIndex    = allTipsList.indexOfFirst { it.id == tips.first().id }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load tip: ${e.message}"
                )
            }
        }
    }

    // CHANGED: "All Tips" tab — tab 1 in the layout.
    fun loadAllTips() {
        activeTab = Tab.ALL
        viewModelScope.launch {
            disasterTipRepository.getAllTips().collect { tips ->
                allTipsList = tips
                _tipsWithCategory.value = mapToTipWithCategory(tips)
                _uiState.value = _uiState.value.copy(
                    allTips      = tips,
                    filteredTips = tips
                )
            }
        }
    }

    // CHANGED: "Recommended" tab — tab 0 in the layout.
    fun loadRecommendedTipsForTab() {
        activeTab = Tab.RECOMMENDED
        viewModelScope.launch {
            loadRecommendedTips()
        }
    }

    fun toggleFavorite(tip: DisasterTipEntity) {
        viewModelScope.launch {
            val updated   = tip.copy(isFavorite = !tip.isFavorite)
            disasterTipRepository.updateTip(updated)

            allTipsList = allTipsList.map { if (it.id == tip.id) updated else it }
            val allUpdated = _uiState.value.allTips.map { if (it.id == tip.id) updated else it }
            _uiState.value = _uiState.value.copy(allTips = allUpdated)

            // Refresh displayed list in whichever tab is active.
            if (activeTab == Tab.RECOMMENDED) {
                loadRecommendedTips()
            } else {
                _tipsWithCategory.value = mapToTipWithCategory(allUpdated)
                _uiState.value = _uiState.value.copy(filteredTips = allUpdated)
            }
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(searchQuery = "")
        viewModelScope.launch {
            if (activeTab == Tab.RECOMMENDED) {
                loadRecommendedTips()
            } else {
                _tipsWithCategory.value = mapToTipWithCategory(_uiState.value.allTips)
                _uiState.value = _uiState.value.copy(filteredTips = _uiState.value.allTips)
            }
        }
    }

    fun searchTips(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searchQuery = query)

            // Search within whichever tab is currently active.
            val source = _uiState.value.filteredTips

            val filtered = if (query.isBlank()) source else {
                source.filter { tip ->
                    tip.tipText.contains(query, ignoreCase = true) ||
                            tip.tipTextTagalog.contains(query, ignoreCase = true)
                }
            }
            _tipsWithCategory.value = mapToTipWithCategory(filtered)
            _uiState.value = _uiState.value.copy(filteredTips = filtered)
        }
    }

    fun getNextTip() {
        if (allTipsList.isEmpty()) return
        currentTipIndex   = (currentTipIndex + 1) % allTipsList.size
        _currentTip.value = allTipsList[currentTipIndex]
    }

    fun getPreviousTip() {
        if (allTipsList.isEmpty()) return
        currentTipIndex   = if (currentTipIndex > 0) currentTipIndex - 1 else allTipsList.size - 1
        _currentTip.value = allTipsList[currentTipIndex]
    }

    fun getRandomTip() { loadRandomTip() }

    fun filterByDisaster(disasterId: Long?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedDisasterId = disasterId)
            if (disasterId == null) {
                _tipsWithCategory.value = mapToTipWithCategory(_uiState.value.allTips)
                _uiState.value = _uiState.value.copy(filteredTips = _uiState.value.allTips)
            } else {
                disasterTipRepository.getTipsByDisasterId(disasterId).collect { tips ->
                    _tipsWithCategory.value = mapToTipWithCategory(tips)
                    _uiState.value          = _uiState.value.copy(filteredTips = tips)
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getTipForTTS(): String {
        val tip       = _currentTip.value ?: return ""
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
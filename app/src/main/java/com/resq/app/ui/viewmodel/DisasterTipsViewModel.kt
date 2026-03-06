package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.entity.DisasterEntity
import com.resq.app.data.entity.DisasterTipEntity
import com.resq.app.data.entity.UserProfileEntity
import com.resq.app.data.repository.DisasterRepository
import com.resq.app.data.repository.DisasterTipRepository
import com.resq.app.data.repository.UserProfileRepository
import com.resq.app.data.repository.UserSettingsRepository
import com.resq.app.ui.adapter.TipAdapter.TipWithCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DisasterTipGroup(
    val disasterId: Long,
    val disasterName: String,
    val disasterNameTagalog: String,
    val disasterDescription: String,
    val disasterDescriptionTagalog: String,
    val iconResName: String,
    val tipCount: Int
)

@HiltViewModel
class DisasterTipsViewModel @Inject constructor(
    private val disasterTipRepository: DisasterTipRepository,
    private val disasterRepository: DisasterRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisasterTipsUiState())
    val uiState: StateFlow<DisasterTipsUiState> = _uiState.asStateFlow()

    private val _currentTip = MutableStateFlow<DisasterTipEntity?>(null)
    val currentTip: StateFlow<DisasterTipEntity?> = _currentTip.asStateFlow()

    private val _tipsWithCategory = MutableStateFlow<List<TipWithCategory>>(emptyList())
    val tipsWithCategory: StateFlow<List<TipWithCategory>> = _tipsWithCategory.asStateFlow()

    // Disaster group cards for TipsFragment list screen
    private val _recommendedDisasterGroups = MutableStateFlow<List<DisasterTipGroup>>(emptyList())
    val recommendedDisasterGroups: StateFlow<List<DisasterTipGroup>> =
        _recommendedDisasterGroups.asStateFlow()

    private val _allDisasterGroups = MutableStateFlow<List<DisasterTipGroup>>(emptyList())
    val allDisasterGroups: StateFlow<List<DisasterTipGroup>> =
        _allDisasterGroups.asStateFlow()

    // Tips for one specific disaster — consumed by TipDetailFragment
    private val _tipsForDisaster = MutableStateFlow<List<DisasterTipEntity>>(emptyList())
    val tipsForDisaster: StateFlow<List<DisasterTipEntity>> = _tipsForDisaster.asStateFlow()

    // ── Disaster detail for TipDetailFragment header ──────────────────────────
    private val _disasterDetail = MutableStateFlow<DisasterEntity?>(null)
    val disasterDetail: StateFlow<DisasterEntity?> = _disasterDetail.asStateFlow()

    private var currentTipIndex = 0
    private var allTipsList: List<DisasterTipEntity> = emptyList()

    private var activeTab = Tab.RECOMMENDED
    private var cachedProfile: UserProfileEntity? = null

    private enum class Tab { RECOMMENDED, ALL }

    init {
        loadInitialData()
    }

    // ── Initial load ──────────────────────────────────────────────────────────

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val settings     = userSettingsRepository.getSettings().first()
                val languageCode = settings?.preferredLanguage ?: "en"
                val profile      = userProfileRepository.getUserProfile().first()
                cachedProfile    = profile

                _uiState.value = _uiState.value.copy(languageCode = languageCode)

                buildDisasterGroups(profile)

                loadRecommendedTips()

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

    // ── Disaster group building ───────────────────────────────────────────────

    private suspend fun buildDisasterGroups(profile: UserProfileEntity?) {
        val disasters = disasterRepository.getAllDisasters().first()
        val allTips   = disasterTipRepository.getAllTipsOnce()

        val tipCountByDisaster = allTips.groupBy { it.disasterId }
            .mapValues { it.value.size }

        val disastersWithTips = disasters.filter { (tipCountByDisaster[it.id] ?: 0) > 0 }

        fun toGroup(d: DisasterEntity) = DisasterTipGroup(
            disasterId                 = d.id,
            disasterName               = d.name,
            disasterNameTagalog        = d.nameTagalog,
            disasterDescription        = d.description,
            disasterDescriptionTagalog = d.descriptionTagalog,
            iconResName                = d.iconResName,
            tipCount                   = tipCountByDisaster[d.id] ?: 0
        )

        _allDisasterGroups.value = disastersWithTips
            .sortedBy { it.displayOrder }
            .map { toGroup(it) }

        val recommended = if (profile == null) {
            disastersWithTips
        } else {
            disastersWithTips.filter { d ->
                d.isUniversal ||
                        (profile.isNearWater        && d.isRecommendedNearWater)   ||
                        (profile.isInMountainousArea && d.isRecommendedMountainous) ||
                        (profile.isInLandslideProne  && d.isRecommendedLandslide)  ||
                        (profile.isInFloodProne      && d.isRecommendedFlood)       ||
                        (profile.isNearVolcano       && d.isRecommendedVolcano)     ||
                        (profile.isInUrbanArea       && d.isRecommendedUrban)       ||
                        (profile.isInCoastalArea     && d.isRecommendedCoastal)
            }
        }

        _recommendedDisasterGroups.value = recommended
            .sortedBy { it.displayOrder }
            .map { toGroup(it) }
    }

    // ── Tip loading helpers ───────────────────────────────────────────────────

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

    private suspend fun loadRecommendedTips() {
        val profile = cachedProfile
        val tips = if (profile != null) {
            disasterTipRepository.getRecommendedTips(profile, limit = 50)
        } else {
            disasterTipRepository.getRandomTips(50)
        }
        _tipsWithCategory.value = mapToTipWithCategory(tips)
        _uiState.value = _uiState.value.copy(
            isLoading    = false,
            filteredTips = tips
        )
    }

    private fun loadRandomTip() {
        viewModelScope.launch {
            try {
                val tips = disasterTipRepository.getRandomTips(1)
                if (tips.isNotEmpty()) {
                    _currentTip.value = tips.first()
                    currentTipIndex   = allTipsList.indexOfFirst { it.id == tips.first().id }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to load tip: ${e.message}")
            }
        }
    }

    // ── Public tab switchers ──────────────────────────────────────────────────

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

    fun loadRecommendedTipsForTab() {
        activeTab = Tab.RECOMMENDED
        viewModelScope.launch { loadRecommendedTips() }
    }

    // ── Tips for a specific disaster (TipDetailFragment) ─────────────────────

    fun loadTipsForDisaster(disasterId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            disasterTipRepository.getTipsByDisasterId(disasterId).collect { tips ->
                _tipsForDisaster.value = tips
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun loadDisasterDetail(disasterId: Long) {
        viewModelScope.launch {
            try {
                disasterRepository.getDisasterById(disasterId).collect { disaster ->
                    _disasterDetail.value = disaster
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load disaster detail: ${e.message}"
                )
            }
        }
    }

    // ── Favorite / share / TTS ────────────────────────────────────────────────

    fun toggleFavorite(tip: DisasterTipEntity) {
        viewModelScope.launch {
            val updated   = tip.copy(isFavorite = !tip.isFavorite)
            disasterTipRepository.updateTip(updated)

            allTipsList = allTipsList.map { if (it.id == tip.id) updated else it }
            val allUpdated = _uiState.value.allTips.map { if (it.id == tip.id) updated else it }
            _uiState.value = _uiState.value.copy(allTips = allUpdated)

            _tipsForDisaster.value = _tipsForDisaster.value.map {
                if (it.id == tip.id) updated else it
            }

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
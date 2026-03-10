package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.entity.DisasterEntity
import com.resq.app.data.entity.DisasterTipEntity
import com.resq.app.data.entity.UserProfileEntity
import com.resq.app.data.entity.UserSettingsEntity
import com.resq.app.data.repository.DisasterRepository
import com.resq.app.data.repository.DisasterTipRepository
import com.resq.app.data.repository.UserProfileRepository
import com.resq.app.data.repository.UserSettingsRepository
import com.resq.app.util.DateTimeHelper
import com.resq.app.util.ProfileSituationMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val disasterRepository: DisasterRepository,
    private val disasterTipRepository: DisasterTipRepository,
    private val dateTimeHelper: DateTimeHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _greeting = MutableStateFlow("")
    val greeting: StateFlow<String> = _greeting.asStateFlow()

    private val _safetyMessage = MutableStateFlow("")
    val safetyMessage: StateFlow<String> = _safetyMessage.asStateFlow()

    private val _disasters = MutableStateFlow<List<DisasterEntity>>(emptyList())
    val disasters: StateFlow<List<DisasterEntity>> = _disasters.asStateFlow()

    private val _currentTip = MutableStateFlow<DisasterTipEntity?>(null)
    val currentTip: StateFlow<DisasterTipEntity?> = _currentTip.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfileEntity?>(null)
    val userProfile: StateFlow<UserProfileEntity?> = _userProfile.asStateFlow()

    private var allTipsList: List<DisasterTipEntity> = emptyList()
    private var allDisastersList: List<DisasterEntity> = emptyList()
    private var recommendedDisastersList: List<DisasterEntity> = emptyList()

    private var currentTipIndex = 0

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val profile      = userProfileRepository.getUserProfile().first()
                val settings     = userSettingsRepository.getSettings().first()
                val disasterList = disasterRepository.getAllDisasters().first()
                val tips         = disasterTipRepository.getRandomTips(10)

                allTipsList      = tips
                allDisastersList = disasterList
                val tip          = tips.firstOrNull()

                recommendedDisastersList = if (profile != null) {
                    ProfileSituationMapper.filterRecommended(disasterList, profile)
                        .ifEmpty { disasterList }
                } else {
                    disasterList
                }

                val languageCode  = settings?.preferredLanguage ?: "en"
                val greetingText  = dateTimeHelper.getGreeting(languageCode)
                val safetyText    = dateTimeHelper.getTimeBasedSafetyMessage(languageCode)

                _greeting.value      = greetingText
                _safetyMessage.value = safetyText
                _currentTip.value    = tip
                _userProfile.value   = profile
                _disasters.value     = recommendedDisastersList

                _uiState.value = _uiState.value.copy(
                    isLoading     = false,
                    userProfile   = profile,
                    userSettings  = settings,
                    disasters     = recommendedDisastersList,
                    currentTip    = tip,
                    greeting      = greetingText,
                    currentDate   = dateTimeHelper.getCurrentDateFormatted(languageCode),
                    safetyMessage = safetyText
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error     = "Failed to load data: ${e.message}"
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        viewModelScope.launch {
            _disasters.value = if (query.isBlank()) {
                recommendedDisastersList
            } else {
                recommendedDisastersList.filter { disaster ->
                    disaster.name.contains(query, ignoreCase = true) ||
                            disaster.nameTagalog.contains(query, ignoreCase = true)
                }
            }
        }
    }

    fun refreshTip() {
        viewModelScope.launch {
            try {
                val tips = disasterTipRepository.getRandomTips(1)
                if (tips.isNotEmpty()) {
                    _currentTip.value = tips.first()
                    _uiState.value    = _uiState.value.copy(currentTip = tips.first())
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to refresh tip: ${e.message}"
                )
            }
        }
    }

    fun getNextTip() {
        if (allTipsList.isEmpty()) return
        currentTipIndex   = (currentTipIndex + 1) % allTipsList.size
        val tip           = allTipsList[currentTipIndex]
        _currentTip.value = tip
        _uiState.value    = _uiState.value.copy(currentTip = tip)
    }

    fun getLanguageCode(): String =
        _uiState.value.userSettings?.preferredLanguage ?: "en"

    fun refreshData() { loadHomeData() }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val userProfile: UserProfileEntity? = null,
    val userSettings: UserSettingsEntity? = null,
    val disasters: List<DisasterEntity> = emptyList(),
    val currentTip: DisasterTipEntity? = null,
    val greeting: String = "",
    val currentDate: String = "",
    val safetyMessage: String = "",
    val error: String? = null
)
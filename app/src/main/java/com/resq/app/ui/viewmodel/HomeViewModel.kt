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

    private var allTipsList: List<DisasterTipEntity> = emptyList()
    private var currentTipIndex = 0

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val profile = userProfileRepository.getUserProfile().first()
                val settings = userSettingsRepository.getSettings().first()
                val disasters = disasterRepository.getAllDisasters().first()
                val tips = disasterTipRepository.getRandomTips(10)

                allTipsList = tips
                val currentTip = tips.firstOrNull()

                val languageCode = settings?.preferredLanguage ?: "en"

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    userProfile = profile,
                    userSettings = settings,
                    disasters = disasters,
                    currentTip = currentTip,
                    greeting = dateTimeHelper.getGreeting(languageCode),
                    currentDate = dateTimeHelper.getCurrentDateFormatted(languageCode),
                    safetyMessage = dateTimeHelper.getTimeBasedSafetyMessage(languageCode)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load data: ${e.message}"
                )
            }
        }
    }

    fun refreshTip() {
        viewModelScope.launch {
            try {
                val tips = disasterTipRepository.getRandomTips(1)
                if (tips.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(currentTip = tips.first())
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
        currentTipIndex = (currentTipIndex + 1) % allTipsList.size
        _uiState.value = _uiState.value.copy(currentTip = allTipsList[currentTipIndex])
    }

    fun getLanguageCode(): String {
        return _uiState.value.userSettings?.preferredLanguage ?: "en"
    }

    fun refreshData() {
        loadHomeData()
    }
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
package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.entity.UserProfileEntity
import com.resq.app.data.entity.UserSettingsEntity
import com.resq.app.data.model.FontSize
import com.resq.app.data.model.TipsPreference
import com.resq.app.data.repository.UserProfileRepository
import com.resq.app.data.repository.UserSettingsRepository
import com.resq.app.util.LanguageManager
import com.resq.app.util.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val themeManager: ThemeManager,
    private val languageManager: LanguageManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            combine(
                userSettingsRepository.getSettings(),
                userProfileRepository.getUserProfile()
            ) { settings, profile ->
                Pair(settings, profile)
            }.collect { (settings, profile) ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    settings = settings,
                    profile = profile
                )
            }
        }
    }

    fun updateDarkTheme(isDark: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.updateDarkTheme(isDark)
            themeManager.applyTheme(isDark)
        }
    }

    fun updateLanguage(languageCode: String) {
        viewModelScope.launch {
            userSettingsRepository.updateLanguage(languageCode)
            _uiState.value = _uiState.value.copy(languageChanged = true)
        }
    }

    fun updateFontSize(fontSize: FontSize) {
        viewModelScope.launch {
            userSettingsRepository.updateFontSize(fontSize)
        }
    }

    fun updateTipsPreference(preference: TipsPreference) {
        viewModelScope.launch {
            userSettingsRepository.updateTipsPreference(preference)
        }
    }

    fun updateVoiceCommandEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.updateVoiceCommandEnabled(enabled)
        }
    }

    fun updateTTSEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.updateTtsEnabled(enabled)
        }
    }

    fun updateTTSSettings(speed: Float, pitch: Float) {
        viewModelScope.launch {
            userSettingsRepository.updateTtsSettings(speed, pitch)
        }
    }

    fun updateProfile(
        isNearWater: Boolean,
        isMountainous: Boolean,
        isLandslideProne: Boolean,
        isFloodProne: Boolean,
        isNearVolcano: Boolean,
        isUrban: Boolean,
        isCoastal: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            try {
                userProfileRepository.updateOnboardingAnswers(
                    isNearWater = isNearWater,
                    isMountainous = isMountainous,
                    isLandslide = isLandslideProne,
                    isFlood = isFloodProne,
                    isVolcano = isNearVolcano,
                    isUrban = isUrban,
                    isCoastal = isCoastal
                )

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Failed to save profile: ${e.message}"
                )
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            val defaultSettings = UserSettingsEntity()
            userSettingsRepository.insertSettings(defaultSettings)
            themeManager.applyTheme(defaultSettings.isDarkTheme)
        }
    }

    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    fun clearLanguageChanged() {
        _uiState.value = _uiState.value.copy(languageChanged = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getSupportedLanguages() = LanguageManager.SUPPORTED_LANGUAGES

    fun getFontSizeOptions() = FontSize.entries

    fun getTipsPreferenceOptions() = TipsPreference.entries
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val settings: UserSettingsEntity? = null,
    val profile: UserProfileEntity? = null,
    val saveSuccess: Boolean = false,
    val languageChanged: Boolean = false,
    val error: String? = null
)
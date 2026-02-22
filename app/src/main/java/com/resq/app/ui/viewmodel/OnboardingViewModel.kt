package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.entity.UserProfileEntity
import com.resq.app.data.repository.UserProfileRepository
import com.resq.app.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    init {
        checkOnboardingStatus()
    }

    private fun checkOnboardingStatus() {
        viewModelScope.launch {
            val isCompleted = userProfileRepository.isOnboardingCompleted()
            _uiState.value = _uiState.value.copy(
                hasCompletedOnboarding = isCompleted
            )
        }
    }

    // Location-based risk profile updates
    fun updateNearWater(isNearWater: Boolean) {
        _uiState.value = _uiState.value.copy(isNearWater = isNearWater)
    }

    fun updateMountainous(isMountainous: Boolean) {
        _uiState.value = _uiState.value.copy(isMountainous = isMountainous)
    }

    fun updateLandslideProne(isLandslideProne: Boolean) {
        _uiState.value = _uiState.value.copy(isLandslideProne = isLandslideProne)
    }

    fun updateFloodProne(isFloodProne: Boolean) {
        _uiState.value = _uiState.value.copy(isFloodProne = isFloodProne)
    }

    fun updateNearVolcano(isNearVolcano: Boolean) {
        _uiState.value = _uiState.value.copy(isNearVolcano = isNearVolcano)
    }

    fun updateUrban(isUrban: Boolean) {
        _uiState.value = _uiState.value.copy(isUrban = isUrban)
    }

    fun updateCoastal(isCoastal: Boolean) {
        _uiState.value = _uiState.value.copy(isCoastal = isCoastal)
    }

    fun nextPage() {
        if (_currentPage.value < TOTAL_PAGES - 1) {
            _currentPage.value = _currentPage.value + 1
        }
    }

    fun previousPage() {
        if (_currentPage.value > 0) {
            _currentPage.value = _currentPage.value - 1
        }
    }

    fun goToPage(page: Int) {
        if (page in 0 until TOTAL_PAGES) {
            _currentPage.value = page
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val state = _uiState.value

                val profile = UserProfileEntity(
                    id = 1,
                    isNearWater = state.isNearWater,
                    isInMountainousArea = state.isMountainous,
                    isInLandslideProne = state.isLandslideProne,
                    isInFloodProne = state.isFloodProne,
                    isNearVolcano = state.isNearVolcano,
                    isInUrbanArea = state.isUrban,
                    isInCoastalArea = state.isCoastal,
                    onboardingCompleted = true
                )

                userProfileRepository.insertProfile(profile)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasCompletedOnboarding = true,
                    onboardingComplete = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to save profile: ${e.message}"
                )
            }
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            val profile = UserProfileEntity(
                id = 1,
                onboardingCompleted = true
            )
            userProfileRepository.insertProfile(profile)

            _uiState.value = _uiState.value.copy(
                hasCompletedOnboarding = true,
                onboardingComplete = true
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        const val TOTAL_PAGES = 4
    }
}

data class OnboardingUiState(
    val isNearWater: Boolean = false,
    val isMountainous: Boolean = false,
    val isLandslideProne: Boolean = false,
    val isFloodProne: Boolean = false,
    val isNearVolcano: Boolean = false,
    val isUrban: Boolean = false,
    val isCoastal: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasCompletedOnboarding: Boolean = false,
    val onboardingComplete: Boolean = false
)
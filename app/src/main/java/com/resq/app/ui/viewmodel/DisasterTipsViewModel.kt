package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.entity.DisasterTipEntity
import com.resq.app.data.repository.DisasterTipRepository
import com.resq.app.data.repository.UserSettingsRepository
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
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisasterTipsUiState())
    val uiState: StateFlow<DisasterTipsUiState> = _uiState.asStateFlow()

    private val _currentTip = MutableStateFlow<DisasterTipEntity?>(null)
    val currentTip: StateFlow<DisasterTipEntity?> = _currentTip.asStateFlow()

    private var currentTipIndex = 0
    private var allTipsList: List<DisasterTipEntity> = emptyList()

    init {
        loadTips()
        loadRandomTip()
    }

    private fun loadTips() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val settings = userSettingsRepository.getSettings().first()
                val languageCode = settings?.preferredLanguage ?: "en"

                disasterTipRepository.getAllTips().collect { tips ->
                    allTipsList = tips
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
                _uiState.value = _uiState.value.copy(
                    filteredTips = _uiState.value.allTips
                )
            } else {
                disasterTipRepository.getTipsByDisasterId(disasterId).collect { tips ->
                    _uiState.value = _uiState.value.copy(filteredTips = tips)
                }
            }
        }
    }

    fun searchTips(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                filteredTips = _uiState.value.allTips
            )
        } else {
            val filtered = _uiState.value.allTips.filter { tip ->
                tip.tipText.contains(query, ignoreCase = true) ||
                        tip.tipTextTagalog.contains(query, ignoreCase = true)
            }
            _uiState.value = _uiState.value.copy(filteredTips = filtered)
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
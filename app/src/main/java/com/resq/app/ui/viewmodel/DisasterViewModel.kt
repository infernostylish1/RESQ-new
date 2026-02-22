package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.entity.DisasterEntity
import com.resq.app.data.entity.DisasterInfoEntity
import com.resq.app.data.entity.DisasterPreparationEntity
import com.resq.app.data.model.SituationType
import com.resq.app.data.repository.DisasterRepository
import com.resq.app.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisasterViewModel @Inject constructor(
    private val disasterRepository: DisasterRepository,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisasterUiState())
    val uiState: StateFlow<DisasterUiState> = _uiState.asStateFlow()

    private val _selectedDisaster = MutableStateFlow<DisasterEntity?>(null)
    val selectedDisaster: StateFlow<DisasterEntity?> = _selectedDisaster.asStateFlow()

    private val _disasterInfo = MutableStateFlow<List<DisasterInfoEntity>>(emptyList())
    val disasterInfo: StateFlow<List<DisasterInfoEntity>> = _disasterInfo.asStateFlow()

    private val _preparations = MutableStateFlow<List<DisasterPreparationEntity>>(emptyList())
    val preparations: StateFlow<List<DisasterPreparationEntity>> = _preparations.asStateFlow()

    init {
        loadDisasters()
    }

    private fun loadDisasters() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val settings = userSettingsRepository.getSettings().first()
                val languageCode = settings?.preferredLanguage ?: "en"

                disasterRepository.getAllDisasters().collect { disasters ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        disasters = disasters,
                        filteredDisasters = disasters,
                        languageCode = languageCode
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load disasters: ${e.message}"
                )
            }
        }
    }

    fun selectDisaster(disasterId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDetails = true)

            try {
                disasterRepository.getDisasterById(disasterId).collect { disaster ->
                    _selectedDisaster.value = disaster
                }

                disasterRepository.getInfoByDisasterId(disasterId).collect { info ->
                    _disasterInfo.value = info
                }

                disasterRepository.getPreparationByDisasterId(disasterId).collect { preps ->
                    _preparations.value = preps
                }

                _uiState.value = _uiState.value.copy(isLoadingDetails = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingDetails = false,
                    error = "Failed to load disaster details: ${e.message}"
                )
            }
        }
    }

    fun filterBySituationType(situationType: SituationType?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedSituationType = situationType)

            if (situationType == null) {
                _uiState.value = _uiState.value.copy(
                    filteredDisasters = _uiState.value.disasters
                )
            } else {
                // Filter preparations by situation type and get related disasters
                val allDisasters = _uiState.value.disasters
                // For now, show all disasters - filtering by situation applies to preparations
                _uiState.value = _uiState.value.copy(filteredDisasters = allDisasters)
            }
        }
    }

    fun getPreparationsBySituation(situationType: SituationType): List<DisasterPreparationEntity> {
        return _preparations.value.filter { it.situationType == situationType }
    }

    fun searchDisasters(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    filteredDisasters = _uiState.value.disasters,
                    searchQuery = ""
                )
            } else {
                disasterRepository.searchDisasters(query).collect { results ->
                    _uiState.value = _uiState.value.copy(
                        filteredDisasters = results,
                        searchQuery = query
                    )
                }
            }
        }
    }

    fun clearSelection() {
        _selectedDisaster.value = null
        _disasterInfo.value = emptyList()
        _preparations.value = emptyList()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getContentForTTS(): String {
        val disaster = _selectedDisaster.value ?: return ""
        val info = _disasterInfo.value
        val isTagalog = _uiState.value.languageCode == "tl"

        val builder = StringBuilder()
        if (isTagalog) {
            builder.append("${disaster.nameTagalog}. ")
            builder.append("${disaster.descriptionTagalog} ")
            info.forEach { item ->
                builder.append("${item.titleTagalog}: ${item.contentTagalog} ")
            }
        } else {
            builder.append("${disaster.name}. ")
            builder.append("${disaster.description} ")
            info.forEach { item ->
                builder.append("${item.title}: ${item.content} ")
            }
        }

        return builder.toString()
    }
}

data class DisasterUiState(
    val isLoading: Boolean = true,
    val isLoadingDetails: Boolean = false,
    val disasters: List<DisasterEntity> = emptyList(),
    val filteredDisasters: List<DisasterEntity> = emptyList(),
    val selectedSituationType: SituationType? = null,
    val searchQuery: String = "",
    val languageCode: String = "en",
    val error: String? = null
)
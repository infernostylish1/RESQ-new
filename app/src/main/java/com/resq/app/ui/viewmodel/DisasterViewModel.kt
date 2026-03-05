package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.entity.DisasterEntity
import com.resq.app.data.entity.DisasterInfoEntity
import com.resq.app.data.entity.DisasterPreparationEntity
import com.resq.app.data.entity.UserProfileEntity          // CHANGED: new import
import com.resq.app.data.model.SituationType
import com.resq.app.data.repository.DisasterRepository
import com.resq.app.data.repository.UserProfileRepository  // CHANGED: new import
import com.resq.app.data.repository.UserSettingsRepository
import com.resq.app.util.ProfileSituationMapper             // CHANGED: new import
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
    private val userSettingsRepository: UserSettingsRepository,
    private val userProfileRepository: UserProfileRepository  // CHANGED: injected
) : ViewModel() {

    private val _uiState = MutableStateFlow(DisasterUiState())
    val uiState: StateFlow<DisasterUiState> = _uiState.asStateFlow()

    // Flat list used by search — contains ALL disasters regardless of section.
    private val _disasters = MutableStateFlow<List<DisasterEntity>>(emptyList())
    val disasters: StateFlow<List<DisasterEntity>> = _disasters.asStateFlow()

    // CHANGED: two sectioned flows consumed by DisastersFragment via ConcatAdapter.
    private val _recommendedDisasters = MutableStateFlow<List<DisasterEntity>>(emptyList())
    val recommendedDisasters: StateFlow<List<DisasterEntity>> = _recommendedDisasters.asStateFlow()

    private val _otherDisasters = MutableStateFlow<List<DisasterEntity>>(emptyList())
    val otherDisasters: StateFlow<List<DisasterEntity>> = _otherDisasters.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedDisaster = MutableStateFlow<DisasterEntity?>(null)
    val selectedDisaster: StateFlow<DisasterEntity?> = _selectedDisaster.asStateFlow()

    private val _disasterInfo = MutableStateFlow<List<DisasterInfoEntity>>(emptyList())
    val disasterInfo: StateFlow<List<DisasterInfoEntity>> = _disasterInfo.asStateFlow()

    private val _preparations = MutableStateFlow<List<DisasterPreparationEntity>>(emptyList())
    val preparations: StateFlow<List<DisasterPreparationEntity>> = _preparations.asStateFlow()

    // CHANGED: hold the profile so search can re-split correctly.
    private var cachedProfile: UserProfileEntity? = null

    init {
        loadDisasters()
    }

    private fun loadDisasters() {
        viewModelScope.launch {
            _isLoading.value  = true
            _uiState.value    = _uiState.value.copy(isLoading = true)

            try {
                val settings     = userSettingsRepository.getSettings().first()
                val languageCode = settings?.preferredLanguage ?: "en"

                // CHANGED: load profile once alongside disasters.
                val profile = userProfileRepository.getUserProfile().first()
                cachedProfile   = profile

                disasterRepository.getAllDisasters().collect { disasterList ->
                    _disasters.value  = disasterList
                    _isLoading.value  = false

                    // CHANGED: split into recommended / other using the mapper.
                    val recommended = if (profile != null) {
                        ProfileSituationMapper.filterRecommended(disasterList, profile)
                    } else {
                        disasterList   // no profile yet — show everything as recommended
                    }
                    val other = if (profile != null) {
                        ProfileSituationMapper.filterOther(disasterList, profile)
                    } else {
                        emptyList()
                    }

                    _recommendedDisasters.value = recommended
                    _otherDisasters.value       = other

                    _uiState.value = _uiState.value.copy(
                        isLoading         = false,
                        disasters         = disasterList,
                        filteredDisasters = disasterList,
                        languageCode      = languageCode
                    )
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _uiState.value   = _uiState.value.copy(
                    isLoading = false,
                    error     = "Failed to load disasters: ${e.message}"
                )
            }
        }
    }

    // CHANGED: restore both sections when search is cleared.
    fun loadAllDisasters() {
        viewModelScope.launch {
            val all     = _uiState.value.disasters
            val profile = cachedProfile

            _disasters.value = all
            _recommendedDisasters.value = if (profile != null) {
                ProfileSituationMapper.filterRecommended(all, profile)
            } else { all }
            _otherDisasters.value = if (profile != null) {
                ProfileSituationMapper.filterOther(all, profile)
            } else { emptyList() }

            _uiState.value = _uiState.value.copy(
                filteredDisasters = all,
                searchQuery       = ""
            )
        }
    }

    fun loadDisaster(disasterId: Long) {
        viewModelScope.launch {
            try {
                disasterRepository.getDisasterById(disasterId).collect { disaster ->
                    _selectedDisaster.value = disaster
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load disaster: ${e.message}"
                )
            }
        }
    }

    fun loadPreparationsByPhase(disasterId: Long, phase: String) {
        viewModelScope.launch {
            try {
                disasterRepository.getPreparationByDisasterId(disasterId).collect { preps ->
                    _preparations.value = preps.filter {
                        it.phase.equals(phase, ignoreCase = true)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load preparations: ${e.message}"
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
                    error            = "Failed to load disaster details: ${e.message}"
                )
            }
        }
    }

    fun filterBySituationType(situationType: SituationType?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedSituationType = situationType)
            val all = _uiState.value.disasters
            _uiState.value = _uiState.value.copy(filteredDisasters = all)
        }
    }

    fun getPreparationsBySituation(situationType: SituationType): List<DisasterPreparationEntity> =
        _preparations.value.filter { it.situationType == situationType }

    // CHANGED: search filters the flat _disasters list and re-splits the result
    // so both section adapters update correctly during search.
    fun searchDisasters(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                loadAllDisasters()
                return@launch
            }
            disasterRepository.searchDisasters(query).collect { results ->
                val profile = cachedProfile
                _disasters.value = results

                _recommendedDisasters.value = if (profile != null) {
                    ProfileSituationMapper.filterRecommended(results, profile)
                } else { results }
                _otherDisasters.value = if (profile != null) {
                    ProfileSituationMapper.filterOther(results, profile)
                } else { emptyList() }

                _uiState.value = _uiState.value.copy(
                    filteredDisasters = results,
                    searchQuery       = query
                )
            }
        }
    }

    fun clearSelection() {
        _selectedDisaster.value = null
        _disasterInfo.value     = emptyList()
        _preparations.value     = emptyList()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getContentForTTS(): String {
        val disaster   = _selectedDisaster.value ?: return ""
        val info       = _disasterInfo.value
        val isTagalog  = _uiState.value.languageCode == "tl"
        val builder    = StringBuilder()
        if (isTagalog) {
            builder.append("${disaster.nameTagalog}. ")
            builder.append("${disaster.descriptionTagalog} ")
            info.forEach { builder.append("${it.titleTagalog}: ${it.contentTagalog} ") }
        } else {
            builder.append("${disaster.name}. ")
            builder.append("${disaster.description} ")
            info.forEach { builder.append("${it.title}: ${it.content} ") }
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
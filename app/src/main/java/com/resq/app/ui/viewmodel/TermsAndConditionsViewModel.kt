package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Move data class BEFORE the ViewModel
data class TermsUiState(
    val content: String = ""
)

@HiltViewModel
class TermsAndConditionsViewModel @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TermsUiState())
    val uiState: StateFlow<TermsUiState> = _uiState.asStateFlow()

    // The content will be provided by the Fragment directly from strings.xml
    // We only need the ViewModel for any future logic
}
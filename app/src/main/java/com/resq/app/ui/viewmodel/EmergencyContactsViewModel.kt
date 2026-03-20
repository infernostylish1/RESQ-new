package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.entity.EmergencyContactEntity
import com.resq.app.data.model.ContactCategory
import com.resq.app.data.repository.EmergencyContactRepository
import com.resq.app.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmergencyContactsViewModel @Inject constructor(
    private val emergencyContactRepository: EmergencyContactRepository,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencyContactsUiState())
    val uiState: StateFlow<EmergencyContactsUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
    }

    private fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val settings = userSettingsRepository.getSettings().first()
                val languageCode = settings?.preferredLanguage ?: "en"

                emergencyContactRepository.getAllContacts().collect { contacts ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allContacts = contacts,
                        languageCode = languageCode
                    )
                    applyFilters()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load contacts: ${e.message}"
                )
            }
        }
    }

    private fun applyFilters() {
        val state = _uiState.value
        var filtered = state.allContacts

        // Filter by tab: 0 = Primary (preloaded), 1 = Custom (user-added)
        filtered = when (state.selectedTab) {
            0 -> filtered.filter { !it.isCustom }
            1 -> filtered.filter { it.isCustom }
            else -> filtered
        }

        // Filter by search query
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            filtered = filtered.filter {
                it.name.lowercase().contains(query) ||
                        it.phoneNumber.lowercase().contains(query) ||
                        (it.description?.lowercase()?.contains(query) == true)
            }
        }

        _uiState.value = state.copy(filteredContacts = filtered)
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
        applyFilters()
    }

    fun searchContacts(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun addCustomContact(name: String, phoneNumber: String) {
        viewModelScope.launch {
            try {
                emergencyContactRepository.addCustomContact(
                    name = name,
                    phoneNumber = phoneNumber,
                    category = ContactCategory.PERSONAL
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to add contact: ${e.message}"
                )
            }
        }
    }

    fun updateContact(contactId: Long, name: String, phoneNumber: String) {
        viewModelScope.launch {
            try {
                val existing = emergencyContactRepository.getContactById(contactId)
                if (existing != null) {
                    emergencyContactRepository.updateContact(
                        existing.copy(name = name, phoneNumber = phoneNumber)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update contact: ${e.message}"
                )
            }
        }
    }

    fun deleteContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            try {
                emergencyContactRepository.deleteContact(contact)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete contact: ${e.message}"
                )
            }
        }
    }

    fun getNationalEmergencyContact(): EmergencyContactEntity? {
        return _uiState.value.allContacts.find {
            it.category == ContactCategory.NATIONAL
        }
    }

    fun getContactByName(name: String): EmergencyContactEntity? {
        val normalizedName = name.lowercase().trim()
        return _uiState.value.allContacts.find {
            it.name.lowercase().contains(normalizedName)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class EmergencyContactsUiState(
    val isLoading: Boolean = true,
    val allContacts: List<EmergencyContactEntity> = emptyList(),
    val filteredContacts: List<EmergencyContactEntity> = emptyList(),
    val selectedTab: Int = 0,
    val searchQuery: String = "",
    val languageCode: String = "en",
    val error: String? = null
)
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
                        filteredContacts = contacts,
                        languageCode = languageCode
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load contacts: ${e.message}"
                )
            }
        }
    }

    fun filterByCategory(category: ContactCategory?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedCategory = category)

            if (category == null) {
                _uiState.value = _uiState.value.copy(
                    filteredContacts = _uiState.value.allContacts
                )
            } else {
                emergencyContactRepository.getContactsByCategory(category).collect { contacts ->
                    _uiState.value = _uiState.value.copy(filteredContacts = contacts)
                }
            }
        }
    }

    fun searchContacts(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searchQuery = query)

            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    filteredContacts = _uiState.value.allContacts
                )
            } else {
                emergencyContactRepository.searchContacts(query).collect { results ->
                    _uiState.value = _uiState.value.copy(filteredContacts = results)
                }
            }
        }
    }

    fun addCustomContact(name: String, phoneNumber: String, category: ContactCategory = ContactCategory.PERSONAL) {
        viewModelScope.launch {
            try {
                emergencyContactRepository.addCustomContact(
                    name = name,
                    phoneNumber = phoneNumber,
                    category = category
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to add contact: ${e.message}"
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

    fun getCategoryOptions() = ContactCategory.entries
}

data class EmergencyContactsUiState(
    val isLoading: Boolean = true,
    val allContacts: List<EmergencyContactEntity> = emptyList(),
    val filteredContacts: List<EmergencyContactEntity> = emptyList(),
    val selectedCategory: ContactCategory? = null,
    val searchQuery: String = "",
    val languageCode: String = "en",
    val error: String? = null
)
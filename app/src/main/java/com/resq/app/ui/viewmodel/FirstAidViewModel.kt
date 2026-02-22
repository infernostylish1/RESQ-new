package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.entity.FirstAidCategoryEntity
import com.resq.app.data.entity.FirstAidContentEntity
import com.resq.app.data.repository.FirstAidRepository
import com.resq.app.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FirstAidViewModel @Inject constructor(
    private val firstAidRepository: FirstAidRepository,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirstAidUiState())
    val uiState: StateFlow<FirstAidUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow<FirstAidCategoryEntity?>(null)
    val selectedCategory: StateFlow<FirstAidCategoryEntity?> = _selectedCategory.asStateFlow()

    private val _categoryContent = MutableStateFlow<List<FirstAidContentEntity>>(emptyList())
    val categoryContent: StateFlow<List<FirstAidContentEntity>> = _categoryContent.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val settings = userSettingsRepository.getSettings().first()
                val languageCode = settings?.preferredLanguage ?: "en"

                firstAidRepository.getAllCategories().collect { categories ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        categories = categories,
                        filteredCategories = categories,
                        languageCode = languageCode
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load first aid categories: ${e.message}"
                )
            }
        }
    }

    fun selectCategory(categoryId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingContent = true)
            _currentStepIndex.value = 0

            try {
                firstAidRepository.getCategoryById(categoryId).collect { category ->
                    _selectedCategory.value = category
                }

                firstAidRepository.getContentByCategoryId(categoryId).collect { content ->
                    _categoryContent.value = content.sortedBy { it.displayOrder }
                }

                _uiState.value = _uiState.value.copy(isLoadingContent = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingContent = false,
                    error = "Failed to load content: ${e.message}"
                )
            }
        }
    }

    fun nextStep() {
        val content = _categoryContent.value
        if (_currentStepIndex.value < content.size - 1) {
            _currentStepIndex.value = _currentStepIndex.value + 1
        }
    }

    fun previousStep() {
        if (_currentStepIndex.value > 0) {
            _currentStepIndex.value = _currentStepIndex.value - 1
        }
    }

    fun goToStep(index: Int) {
        val content = _categoryContent.value
        if (index in content.indices) {
            _currentStepIndex.value = index
        }
    }

    fun getCurrentStep(): FirstAidContentEntity? {
        val content = _categoryContent.value
        val index = _currentStepIndex.value
        return if (index in content.indices) content[index] else null
    }

    fun getTotalSteps(): Int {
        return _categoryContent.value.size
    }

    fun isFirstStep(): Boolean {
        return _currentStepIndex.value == 0
    }

    fun isLastStep(): Boolean {
        return _currentStepIndex.value >= _categoryContent.value.size - 1
    }

    fun searchCategories(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    filteredCategories = _uiState.value.categories,
                    searchQuery = ""
                )
            } else {
                firstAidRepository.searchCategories(query).collect { results ->
                    _uiState.value = _uiState.value.copy(
                        filteredCategories = results,
                        searchQuery = query
                    )
                }
            }
        }
    }

    fun clearSelection() {
        _selectedCategory.value = null
        _categoryContent.value = emptyList()
        _currentStepIndex.value = 0
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getContentForTTS(): String {
        val category = _selectedCategory.value ?: return ""
        val content = _categoryContent.value
        val isTagalog = _uiState.value.languageCode == "tl"

        val builder = StringBuilder()
        if (isTagalog) {
            builder.append("${category.nameTagalog}. ")
            builder.append("${category.descriptionTagalog} ")
            content.forEachIndexed { index, step ->
                builder.append("Hakbang ${index + 1}: ${step.contentTagalog} ")
            }
        } else {
            builder.append("${category.name}. ")
            builder.append("${category.description} ")
            content.forEachIndexed { index, step ->
                builder.append("Step ${index + 1}: ${step.content} ")
            }
        }

        return builder.toString()
    }

    fun getCurrentStepForTTS(): String {
        val step = getCurrentStep() ?: return ""
        val stepNumber = _currentStepIndex.value + 1
        val isTagalog = _uiState.value.languageCode == "tl"
        return if (isTagalog) {
            "Hakbang $stepNumber: ${step.contentTagalog}"
        } else {
            "Step $stepNumber: ${step.content}"
        }
    }
}

data class FirstAidUiState(
    val isLoading: Boolean = true,
    val isLoadingContent: Boolean = false,
    val categories: List<FirstAidCategoryEntity> = emptyList(),
    val filteredCategories: List<FirstAidCategoryEntity> = emptyList(),
    val searchQuery: String = "",
    val languageCode: String = "en",
    val error: String? = null
)
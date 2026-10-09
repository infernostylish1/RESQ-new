package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.resq.app.R
import com.resq.app.data.entity.UserSettingsEntity
import com.resq.app.data.model.FontSize
import com.resq.app.databinding.FragmentSettingsBinding
import com.resq.app.ui.viewmodel.SettingsViewModel
import com.resq.app.util.DialogHelper
import com.resq.app.util.LanguageManager
import com.resq.app.util.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setupWithNavController(this)
        setupClickListeners()
        setupSliders()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateDarkTheme(isChecked)
        }

        binding.settingFontSize.setOnClickListener {
            showFontSizeDialog()
        }

        binding.settingLanguage.setOnClickListener {
            showLanguageDialog()
        }

        binding.switchVoiceCommand.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateVoiceCommandEnabled(isChecked)
        }

        binding.switchTts.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateTTSEnabled(isChecked)
            updateTtsCustomizationVisibility(isChecked)
        }

        binding.btnTestTts.setOnClickListener {
            viewModel.testTTS()
        }

        binding.settingEmergencyContacts.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_emergency_contacts)
        }

        binding.settingAbout.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_about)
        }

        binding.settingTerms.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_terms)
        }

        binding.settingPrivacy.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_privacy)
        }

        binding.settingDataSources.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_data_sources)
        }
    }

    private fun setupSliders() {
        binding.sliderTtsSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.updateTTSSettings(value, binding.sliderTtsPitch.value)
            }
        }

        binding.sliderTtsPitch.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.updateTTSSettings(binding.sliderTtsSpeed.value, value)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.settings?.let { settings ->
                        updateUI(settings)
                    }

                    if (state.languageChanged) {
                        requireActivity().recreate()
                        viewModel.clearLanguageChanged()
                    }

                    if (state.fontSizeChanged) {
                        requireActivity().recreate()
                        viewModel.clearFontSizeChanged()
                    }

                    state.error?.let { error ->
                        showError(error)
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun updateUI(settings: UserSettingsEntity) {
        // Detach listeners to avoid triggering updates during UI sync
        binding.switchDarkTheme.setOnCheckedChangeListener(null)
        binding.switchVoiceCommand.setOnCheckedChangeListener(null)
        binding.switchTts.setOnCheckedChangeListener(null)

        binding.switchDarkTheme.isChecked = settings.isDarkTheme
        binding.switchVoiceCommand.isChecked = settings.isVoiceCommandEnabled
        binding.switchTts.isChecked = settings.isTtsEnabled
        
        updateTtsCustomizationVisibility(settings.isTtsEnabled)
        
        // Update sliders if not touching them
        if (!binding.sliderTtsSpeed.isFocused) {
            binding.sliderTtsSpeed.value = settings.ttsSpeed
        }
        if (!binding.sliderTtsPitch.isFocused) {
            binding.sliderTtsPitch.value = settings.ttsPitch
        }

        // Re-attach listeners
        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateDarkTheme(isChecked)
        }
        binding.switchVoiceCommand.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateVoiceCommandEnabled(isChecked)
        }
        binding.switchTts.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateTTSEnabled(isChecked)
            updateTtsCustomizationVisibility(isChecked)
        }

        binding.tvFontSizeValue.text = getFontSizeDisplayName(settings.fontSize)
        binding.tvLanguageValue.text = getLanguageDisplayName(settings.preferredLanguage)
    }

    private fun updateTtsCustomizationVisibility(enabled: Boolean) {
        binding.layoutTtsCustomization.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun showFontSizeDialog() {
        val fontSizes = viewModel.getFontSizeOptions()
        val displayNames = fontSizes.map { getFontSizeDisplayName(it) }.toTypedArray()

        val currentSettings = viewModel.uiState.value.settings
        val currentIndex = fontSizes.indexOf(currentSettings?.fontSize ?: FontSize.MEDIUM)

        DialogHelper.showSelectionDialog(
            context = requireContext(),
            title = getString(R.string.settings_font_size),
            items = displayNames,
            selectedIndex = currentIndex,
            onItemSelected = { which ->
                viewModel.updateFontSize(fontSizes[which])
            }
        )
    }

    private fun showLanguageDialog() {
        val languages = viewModel.getSupportedLanguages()
        val displayNames = languages.map { it.displayName }.toTypedArray()

        val currentSettings = viewModel.uiState.value.settings
        val currentIndex = languages.indexOfFirst {
            it.code == (currentSettings?.preferredLanguage ?: LanguageManager.LANGUAGE_ENGLISH)
        }.coerceAtLeast(0)

        DialogHelper.showSelectionDialog(
            context = requireContext(),
            title = getString(R.string.settings_language),
            items = displayNames,
            selectedIndex = currentIndex,
            onItemSelected = { which ->
                viewModel.updateLanguage(languages[which].code)
            }
        )
    }

    private fun getFontSizeDisplayName(fontSize: FontSize): String {
        return when (fontSize) {
            FontSize.SMALL -> getString(R.string.font_size_small)
            FontSize.MEDIUM -> getString(R.string.font_size_medium)
            FontSize.LARGE -> getString(R.string.font_size_large)
            FontSize.EXTRA_LARGE -> getString(R.string.font_size_extra_large)
        }
    }

    private fun getLanguageDisplayName(languageCode: String): String {
        return viewModel.getSupportedLanguages().find { it.code == languageCode }?.displayName
            ?: getString(R.string.language_english)
    }

    private fun showError(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.resq.app.R
import com.resq.app.data.model.FontSize
import com.resq.app.databinding.FragmentSettingsBinding
import com.resq.app.ui.viewmodel.SettingsViewModel
import com.resq.app.util.LanguageManager
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

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        // Dark theme toggle
        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateDarkTheme(isChecked)
        }

        // Font size selection
        binding.settingFontSize.setOnClickListener {
            showFontSizeDialog()
        }

        // Language selection
        binding.settingLanguage.setOnClickListener {
            showLanguageDialog()
        }

        // Voice command toggle
        binding.switchVoiceCommand.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateVoiceCommandEnabled(isChecked)
        }

        // TTS toggle
        binding.switchTts.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateTTSEnabled(isChecked)
        }

        // Emergency contacts navigation
        binding.settingEmergencyContacts.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_emergency_contacts)
        }

        // About navigation
        binding.settingAbout.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_about)
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

                    state.error?.let { error ->
                        showError(error)
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun updateUI(settings: com.resq.app.data.entity.UserSettingsEntity) {
        // Update switches without triggering listeners
        binding.switchDarkTheme.setOnCheckedChangeListener(null)
        binding.switchVoiceCommand.setOnCheckedChangeListener(null)
        binding.switchTts.setOnCheckedChangeListener(null)

        binding.switchDarkTheme.isChecked = settings.isDarkTheme
        binding.switchVoiceCommand.isChecked = settings.isVoiceCommandEnabled
        binding.switchTts.isChecked = settings.isTtsEnabled

        // Re-attach listeners
        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateDarkTheme(isChecked)
        }
        binding.switchVoiceCommand.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateVoiceCommandEnabled(isChecked)
        }
        binding.switchTts.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateTTSEnabled(isChecked)
        }

        // Update text values
        binding.tvFontSizeValue.text = getFontSizeDisplayName(settings.fontSize)
        binding.tvLanguageValue.text = getLanguageDisplayName(settings.preferredLanguage)
    }

    private fun showFontSizeDialog() {
        val fontSizes = viewModel.getFontSizeOptions()
        val displayNames = fontSizes.map { getFontSizeDisplayName(it) }.toTypedArray()

        val currentSettings = viewModel.uiState.value.settings
        val currentIndex = fontSizes.indexOf(currentSettings?.fontSize ?: FontSize.MEDIUM)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_font_size)
            .setSingleChoiceItems(displayNames, currentIndex) { dialog, which ->
                viewModel.updateFontSize(fontSizes[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = viewModel.getSupportedLanguages()
        val displayNames = languages.map { it.displayName }.toTypedArray()

        val currentSettings = viewModel.uiState.value.settings
        val currentIndex = languages.indexOfFirst {
            it.code == (currentSettings?.preferredLanguage ?: LanguageManager.LANGUAGE_ENGLISH)
        }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(displayNames, currentIndex) { dialog, which ->
                viewModel.updateLanguage(languages[which].code)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
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
            ?: "English"
    }

    private fun showError(message: String) {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            message,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.resq.app.R
import com.resq.app.databinding.FragmentDataPrivacyBinding
import com.resq.app.ui.viewmodel.DataPrivacyViewModel
import com.resq.app.util.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DataPrivacyFragment : Fragment() {

    private var _binding: FragmentDataPrivacyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DataPrivacyViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDataPrivacyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupContent()
    }

    private fun setupToolbar() {
        binding.toolbar.setupWithNavController(this)
    }

    private fun setupContent() {
        // Get content directly from strings.xml
        val rawContent = getString(R.string. privacy_full_content)
        val formattedContent = formatContentForDisplay(rawContent)
        binding.tvContent.text = android.text.Html.fromHtml(formattedContent, android.text.Html.FROM_HTML_MODE_LEGACY)
    }

    private fun formatContentForDisplay(rawContent: String): String {
        val sections = rawContent.split("\n\n")
        val builder = StringBuilder()

        for (section in sections) {
            val lines = section.split("\n")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    if (trimmed.matches(Regex("^\\d+\\. .*"))) {
                        builder.append("<b>${trimmed}</b><br>")
                    } else if (trimmed.startsWith("•")) {
                        builder.append("$trimmed<br>")
                    } else {
                        builder.append("$trimmed<br>")
                    }
                }
            }
            builder.append("<br>")
        }

        return builder.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
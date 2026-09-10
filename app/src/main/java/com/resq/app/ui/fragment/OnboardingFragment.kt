package com.resq.app.ui.fragment

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.resq.app.R
import com.resq.app.databinding.FragmentOnboardingBinding
import com.resq.app.ui.adapter.OnboardingPagerAdapter
import com.resq.app.ui.adapter.OnboardingPagerAdapter.AgreementField
import com.resq.app.ui.adapter.OnboardingPagerAdapter.LocationField
import com.resq.app.ui.adapter.OnboardingPagerAdapter.OnboardingPage
import com.resq.app.ui.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by viewModels()
    private lateinit var pagerAdapter: OnboardingPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupIndicators()
        setupClickListeners()
        observeViewModel()

        // Set content after adapter is set
        binding.viewPager.post {
            updateAgreementContent()
        }
    }

    // ── ViewPager setup ───────────────────────────────────────────────────────

    private fun setupViewPager() {
        val pages = buildPageList()

        pagerAdapter = OnboardingPagerAdapter(
            pages = pages,
            onLocationChanged = ::onLocationFieldChanged,
            onAgreementChanged = ::onAgreementFieldChanged
        )
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.goToPage(position)
                updateIndicators(position)
                updateButtonsVisibility(position)
                // Update agreement content when page changes to agreement page
                if (position == 3) {
                    binding.viewPager.post {
                        updateAgreementContent()
                    }
                }
            }
        })
    }

    private fun buildPageList(): List<OnboardingPage> = listOf(
        // Part 1: Info Pages (0-2)
        OnboardingPage.InfoPage(
            title = getString(R.string.onboarding_welcome_title),
            description = getString(R.string.onboarding_welcome_desc),
            imageRes = R.drawable.onboarding_1
        ),
        OnboardingPage.InfoPage(
            title = getString(R.string.onboarding_disasters_title),
            description = getString(R.string.onboarding_disasters_desc),
            imageRes = R.drawable.onboarding_2
        ),
        OnboardingPage.InfoPage(
            title = getString(R.string.onboarding_first_aid_title),
            description = getString(R.string.onboarding_first_aid_desc),
            imageRes = R.drawable.onboarding_3
        ),
        // Part 2: Agreement (3), Location (4), Ready (5)
        OnboardingPage.AgreementPage(),
        OnboardingPage.LocationPage(),
        OnboardingPage.InfoPage(
            title = getString(R.string.onboarding_ready_title),
            description = getString(R.string.onboarding_ready_desc),
            imageRes = R.drawable.onboarding_4
        )
    )

    // ── Location checkbox callback ────────────────────────────────────────────

    private fun onLocationFieldChanged(field: LocationField, checked: Boolean) {
        when (field) {
            LocationField.NEAR_WATER   -> viewModel.updateNearWater(checked)
            LocationField.MOUNTAINOUS  -> viewModel.updateMountainous(checked)
            LocationField.LANDSLIDE    -> viewModel.updateLandslideProne(checked)
            LocationField.FLOOD        -> viewModel.updateFloodProne(checked)
            LocationField.VOLCANO      -> viewModel.updateNearVolcano(checked)
            LocationField.URBAN        -> viewModel.updateUrban(checked)
            LocationField.COASTAL      -> viewModel.updateCoastal(checked)
        }
    }

    // ── Agreement checkbox callback ───────────────────────────────────────────

    private fun onAgreementFieldChanged(field: AgreementField, checked: Boolean) {
        when (field) {
            AgreementField.TERMS -> {
                viewModel.updateTermsAccepted(checked)
                val currentPage = binding.viewPager.currentItem
                if (currentPage == 3) {
                    updateNextButtonState()
                }
            }
            AgreementField.PRIVACY -> {
                viewModel.updatePrivacyAccepted(checked)
                val currentPage = binding.viewPager.currentItem
                if (currentPage == 3) {
                    updateNextButtonState()
                }
            }
        }
    }

    // ── Agreement Content ─────────────────────────────────────────────────────

    private fun updateAgreementContent() {
        try {
            // Get raw content from strings.xml
            val termsRaw = getString(R.string.terms_full_content)
            val privacyRaw = getString(R.string.privacy_full_content)

            // Format content with HTML for better display
            val termsFormatted = formatContentForDisplay(termsRaw)
            val privacyFormatted = formatContentForDisplay(privacyRaw)

            // Find the agreement page view and update content
            val viewPager = binding.viewPager
            val recyclerView = viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
            if (recyclerView != null) {
                // Find the agreement page (position 3)
                val agreementViewHolder = recyclerView.findViewHolderForAdapterPosition(3)
                if (agreementViewHolder is OnboardingPagerAdapter.AgreementViewHolder) {
                    // Access the itemView and find the TextViews
                    val itemView = agreementViewHolder.itemView
                    val tvTerms = itemView.findViewById<TextView>(R.id.tv_terms_content)
                    val tvPrivacy = itemView.findViewById<TextView>(R.id.tv_privacy_content)

                    tvTerms?.text = Html.fromHtml(termsFormatted, Html.FROM_HTML_MODE_LEGACY)
                    tvPrivacy?.text = Html.fromHtml(privacyFormatted, Html.FROM_HTML_MODE_LEGACY)
                }
            }
        } catch (e: Exception) {
            // Content might not be ready yet - this is fine
        }
    }

    private fun formatContentForDisplay(rawContent: String): String {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return "<i>No content available</i>"
        }

        val sections = trimmed.split(Regex("\n\\s*\n"))
        val builder = StringBuilder()

        for (section in sections) {
            val lines = section.trim().split("\n")
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isNotEmpty()) {
                    if (trimmedLine.matches(Regex("^\\d+\\.\\s+.*"))) {
                        builder.append("<b>${trimmedLine}</b><br>")
                    } else if (trimmedLine.startsWith("•")) {
                        builder.append("$trimmedLine<br>")
                    } else {
                        builder.append("$trimmedLine<br>")
                    }
                }
            }
            builder.append("<br>")
        }

        val result = builder.toString()
        return if (result.isEmpty()) {
            "<i>No content available</i>"
        } else {
            result
        }
    }

    // ── Button state ──────────────────────────────────────────────────────────

    private fun updateNextButtonState() {
        val state = viewModel.uiState.value
        val isEnabled = state.termsAccepted && state.privacyAccepted
        binding.btnNext.isEnabled = isEnabled

        // Update button style based on enabled state
        if (isEnabled) {
            binding.btnNext.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
            binding.btnNext.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        } else {
            binding.btnNext.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.button_disabled)
            binding.btnNext.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        }
    }

    // ── Indicators ────────────────────────────────────────────────────────────

    private fun setupIndicators() {
        binding.indicatorContainer.removeAllViews()

        repeat(OnboardingViewModel.TOTAL_PAGES) { position ->
            val indicator = ImageView(requireContext()).apply {
                setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.indicator_inactive)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 8, 0)
                }
            }
            binding.indicatorContainer.addView(indicator)

            if (position == 2) {
                val separator = View(requireContext()).apply {
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
                    layoutParams = LinearLayout.LayoutParams(
                        2,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ).apply {
                        setMargins(16, 0, 16, 0)
                        height = 24
                    }
                }
                binding.indicatorContainer.addView(separator)
            }
        }
        updateIndicators(0)
    }

    private fun updateIndicators(position: Int) {
        var dotIndex = 0
        repeat(binding.indicatorContainer.childCount) { i ->
            val child = binding.indicatorContainer.getChildAt(i)
            if (child is ImageView) {
                child.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        if (dotIndex == position) R.drawable.indicator_active else R.drawable.indicator_inactive
                    )
                )
                dotIndex++
            }
        }
    }

    // ── Button visibility ─────────────────────────────────────────────────────

    private fun updateButtonsVisibility(position: Int) {
        binding.btnPrevious.visibility =
            if (position == 0) View.INVISIBLE else View.VISIBLE

        val isPart1 = position < 3
        val isAgreementPage = position == 3
        val isLocationPage = position == 4
        val isLastPage = position == OnboardingViewModel.TOTAL_PAGES - 1

        binding.btnSkip.visibility = if (isPart1 || isLocationPage) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }

        binding.btnNext.text = when {
            isLastPage -> getString(R.string.action_get_started)
            else -> getString(R.string.action_next)
        }

        if (isAgreementPage) {
            updateNextButtonState()
        } else {
            binding.btnNext.isEnabled = true
            binding.btnNext.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
            binding.btnNext.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        }
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnSkip.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem

            if (currentPosition < 3) {
                binding.viewPager.currentItem = 3
            } else if (currentPosition == 4) {
                binding.viewPager.currentItem = 5
            }
        }

        binding.btnPrevious.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current > 0) binding.viewPager.currentItem = current - 1
        }

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < OnboardingViewModel.TOTAL_PAGES - 1) {
                binding.viewPager.currentItem = current + 1
            } else {
                viewModel.completeOnboarding()
            }
        }
    }

    // ── ViewModel observation ─────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.onboardingComplete) {
                        navigateToHome()
                        return@collect
                    }
                    state.error?.let {
                        showError(it)
                        viewModel.clearError()
                    }

                    val currentPage = binding.viewPager.currentItem
                    if (currentPage == 3) {
                        updateNextButtonState()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentPage.collect { page ->
                    if (binding.viewPager.currentItem != page) {
                        binding.viewPager.setCurrentItem(page, true)
                    }
                }
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateToHome() {
        findNavController().navigate(R.id.action_onboarding_to_home)
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
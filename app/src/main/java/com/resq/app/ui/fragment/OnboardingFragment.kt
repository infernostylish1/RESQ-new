package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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
    }

    // ── ViewPager setup ───────────────────────────────────────────────────────

    private fun setupViewPager() {
        val pages = buildPageList()

        pagerAdapter = OnboardingPagerAdapter(
            pages = pages,
            onLocationChanged = ::onLocationFieldChanged
        )
        binding.viewPager.adapter = pagerAdapter

        // Disable swipe on the location page so scroll inside
        // NestedScrollView doesn't fight the ViewPager
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.goToPage(position)
                updateIndicators(position)
                updateButtonsVisibility(position)
            }
        })
    }

    private fun buildPageList(): List<OnboardingPage> = listOf(
        OnboardingPage.InfoPage(
            title = getString(R.string.onboarding_welcome_title),
            description = getString(R.string.onboarding_welcome_desc),
            imageRes = R.drawable.ic_launcher_foreground
        ),
        OnboardingPage.InfoPage(
            title = getString(R.string.onboarding_disasters_title),
            description = getString(R.string.onboarding_disasters_desc),
            imageRes = R.drawable.ic_launcher_foreground
        ),
        OnboardingPage.InfoPage(
            title = getString(R.string.onboarding_first_aid_title),
            description = getString(R.string.onboarding_first_aid_desc),
            imageRes = R.drawable.ic_launcher_foreground
        ),
        // Location question page — index 3
        OnboardingPage.LocationPage(),
        OnboardingPage.InfoPage(
            title = getString(R.string.onboarding_ready_title),
            description = getString(R.string.onboarding_ready_desc),
            imageRes = R.drawable.ic_launcher_foreground
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

    // ── Indicators ────────────────────────────────────────────────────────────

    private fun setupIndicators() {
        binding.indicatorContainer.removeAllViews()
        repeat(OnboardingViewModel.TOTAL_PAGES) {
            val indicator = ImageView(requireContext()).apply {
                setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.indicator_inactive)
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(8, 0, 8, 0) }
            }
            binding.indicatorContainer.addView(indicator)
        }
        updateIndicators(0)
    }

    private fun updateIndicators(position: Int) {
        repeat(binding.indicatorContainer.childCount) { i ->
            val indicator = binding.indicatorContainer.getChildAt(i) as ImageView
            indicator.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    if (i == position) R.drawable.indicator_active else R.drawable.indicator_inactive
                )
            )
        }
    }

    // ── Button visibility ─────────────────────────────────────────────────────

    private fun updateButtonsVisibility(position: Int) {
        binding.btnPrevious.visibility =
            if (position == 0) View.INVISIBLE else View.VISIBLE
        binding.btnNext.text =
            if (position == OnboardingViewModel.TOTAL_PAGES - 1) {
                getString(R.string.action_get_started)
            } else {
                getString(R.string.action_next)
            }
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnSkip.setOnClickListener {
            viewModel.skipOnboarding()
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
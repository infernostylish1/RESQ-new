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
import com.resq.app.R
import com.resq.app.databinding.FragmentOnboardingBinding
import com.resq.app.ui.adapter.OnboardingPagerAdapter
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

    private fun setupViewPager() {
        val pages = listOf(
            OnboardingPagerAdapter.OnboardingPage(
                title = getString(R.string.onboarding_welcome_title),
                description = getString(R.string.onboarding_welcome_desc),
                imageRes = R.drawable.ic_launcher_foreground
            ),
            OnboardingPagerAdapter.OnboardingPage(
                title = getString(R.string.onboarding_disasters_title),
                description = getString(R.string.onboarding_disasters_desc),
                imageRes = R.drawable.ic_launcher_foreground
            ),
            OnboardingPagerAdapter.OnboardingPage(
                title = getString(R.string.onboarding_first_aid_title),
                description = getString(R.string.onboarding_first_aid_desc),
                imageRes = R.drawable.ic_launcher_foreground
            ),
            OnboardingPagerAdapter.OnboardingPage(
                title = getString(R.string.onboarding_ready_title),
                description = getString(R.string.onboarding_ready_desc),
                imageRes = R.drawable.ic_launcher_foreground
            )
        )

        pagerAdapter = OnboardingPagerAdapter(pages)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.goToPage(position)
                updateIndicators(position)
                updateButtonsVisibility(position)
            }
        })
    }

    private fun setupIndicators() {
        val indicatorCount = OnboardingViewModel.TOTAL_PAGES
        binding.indicatorContainer.removeAllViews()

        for (i in 0 until indicatorCount) {
            val indicator = ImageView(requireContext()).apply {
                setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.indicator_inactive)
                )
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 8, 0)
                }
                layoutParams = params
            }
            binding.indicatorContainer.addView(indicator)
        }
        updateIndicators(0)
    }

    private fun updateIndicators(position: Int) {
        val childCount = binding.indicatorContainer.childCount
        for (i in 0 until childCount) {
            val indicator = binding.indicatorContainer.getChildAt(i) as ImageView
            val drawableRes = if (i == position) {
                R.drawable.indicator_active
            } else {
                R.drawable.indicator_inactive
            }
            indicator.setImageDrawable(ContextCompat.getDrawable(requireContext(), drawableRes))
        }
    }

    private fun updateButtonsVisibility(position: Int) {
        val isFirstPage = position == 0
        val isLastPage = position == OnboardingViewModel.TOTAL_PAGES - 1

        binding.btnPrevious.visibility = if (isFirstPage) View.INVISIBLE else View.VISIBLE
        binding.btnNext.text = if (isLastPage) {
            getString(R.string.action_get_started)
        } else {
            getString(R.string.action_next)
        }
    }

    private fun setupClickListeners() {
        binding.btnSkip.setOnClickListener {
            viewModel.skipOnboarding()
        }

        binding.btnPrevious.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem > 0) {
                binding.viewPager.currentItem = currentItem - 1
            }
        }

        binding.btnNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < OnboardingViewModel.TOTAL_PAGES - 1) {
                binding.viewPager.currentItem = currentItem + 1
            } else {
                viewModel.completeOnboarding()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.onboardingComplete) {
                        navigateToHome()
                    }

                    state.error?.let { error ->
                        showError(error)
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

    private fun navigateToHome() {
        findNavController().navigate(R.id.action_onboarding_to_home)
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
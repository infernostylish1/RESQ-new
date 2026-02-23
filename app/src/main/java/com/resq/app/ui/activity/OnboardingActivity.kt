package com.resq.app.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.resq.app.MainActivity
import com.resq.app.R
import com.resq.app.databinding.ActivityOnboardingBinding
import com.resq.app.ui.adapter.OnboardingPagerAdapter
import com.resq.app.ui.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val viewModel: OnboardingViewModel by viewModels()

    private lateinit var pagerAdapter: OnboardingPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            val indicator = ImageView(this).apply {
                setImageDrawable(
                    ContextCompat.getDrawable(
                        this@OnboardingActivity,
                        R.drawable.indicator_inactive
                    )
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
            indicator.setImageDrawable(ContextCompat.getDrawable(this, drawableRes))
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.onboardingComplete) {
                        navigateToMain()
                    }

                    state.error?.let { error ->
                        showError(error)
                        viewModel.clearError()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentPage.collect { page ->
                    if (binding.viewPager.currentItem != page) {
                        binding.viewPager.setCurrentItem(page, true)
                    }
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            message,
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).show()
    }
}
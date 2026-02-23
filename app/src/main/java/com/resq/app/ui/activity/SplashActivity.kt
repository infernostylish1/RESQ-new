package com.resq.app.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.resq.app.MainActivity
import com.resq.app.databinding.ActivitySplashBinding
import com.resq.app.ui.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: OnboardingViewModel by viewModels()

    private var keepSplashOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeOnboardingStatus()
    }

    private fun observeOnboardingStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Small delay for splash screen visibility
                    Handler(Looper.getMainLooper()).postDelayed({
                        keepSplashOnScreen = false
                        navigateToNextScreen(state.hasCompletedOnboarding)
                    }, SPLASH_DELAY)
                }
            }
        }
    }

    private fun navigateToNextScreen(hasCompletedOnboarding: Boolean) {
        val intent = if (hasCompletedOnboarding) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, OnboardingActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        private const val SPLASH_DELAY = 1500L
    }
}
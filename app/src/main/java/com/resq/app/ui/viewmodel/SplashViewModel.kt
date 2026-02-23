package com.resq.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.resq.app.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * ViewModel for SplashActivity.
 * Handles checking onboarding completion status.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    /**
     * Flow that emits whether the user has completed onboarding.
     */
    val hasCompletedOnboarding: Flow<Boolean> = userSettingsRepository
        .getSettings()
        .map { settings -> settings?.hasCompletedOnboarding ?: false }
}
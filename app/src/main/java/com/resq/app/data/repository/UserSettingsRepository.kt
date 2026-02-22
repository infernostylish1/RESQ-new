package com.resq.app.data.repository

import com.resq.app.data.dao.UserSettingsDao
import com.resq.app.data.entity.UserSettingsEntity
import com.resq.app.data.model.FontSize
import com.resq.app.data.model.TipsPreference
import kotlinx.coroutines.flow.Flow

/**
 * Repository for user settings operations.
 * All ViewModel-facing method names normalized here.
 */
class UserSettingsRepository(
    private val userSettingsDao: UserSettingsDao
) {

    /**
     * Flow-based settings observer.
     * Used as getUserSettings() by ViewModels.
     */
    fun getUserSettings(): Flow<UserSettingsEntity?> =
        userSettingsDao.getSettings()

    /** Legacy alias */
    fun getSettings(): Flow<UserSettingsEntity?> =
        userSettingsDao.getSettings()

    suspend fun getSettingsOnce(): UserSettingsEntity? =
        userSettingsDao.getSettingsOnce()

    /**
     * Upsert — used by SettingsViewModel and OnboardingViewModel.
     */
    suspend fun insertOrUpdateSettings(settings: UserSettingsEntity) {
        val existing = userSettingsDao.getSettingsOnce()
        if (existing == null) {
            userSettingsDao.insertSettings(settings)
        } else {
            userSettingsDao.updateSettings(settings.copy(id = existing.id))
        }
    }

    suspend fun insertSettings(settings: UserSettingsEntity) =
        userSettingsDao.insertSettings(settings)

    suspend fun updateSettings(settings: UserSettingsEntity) =
        userSettingsDao.updateSettings(settings)

    suspend fun updateDarkTheme(isDark: Boolean) =
        userSettingsDao.updateDarkTheme(isDark)

    suspend fun updateFontSize(fontSize: FontSize) =
        userSettingsDao.updateFontSize(fontSize)

    /**
     * TTS enabled toggle — used by SettingsViewModel.
     */
    suspend fun updateTTSEnabled(enabled: Boolean) =
        userSettingsDao.updateTtsEnabled(enabled)

    /** Legacy alias */
    suspend fun updateTtsEnabled(enabled: Boolean) =
        userSettingsDao.updateTtsEnabled(enabled)

    /**
     * TTS speed — used by SettingsViewModel.
     */
    suspend fun updateTTSSpeed(speed: Float) {
        val current = userSettingsDao.getSettingsOnce()
        val pitch = current?.ttsPitch ?: 1.0f
        userSettingsDao.updateTtsSettings(speed, pitch)
    }

    /**
     * TTS pitch — used by SettingsViewModel.
     */
    suspend fun updateTTSPitch(pitch: Float) {
        val current = userSettingsDao.getSettingsOnce()
        val speed = current?.ttsSpeed ?: 1.0f
        userSettingsDao.updateTtsSettings(speed, pitch)
    }

    suspend fun updateTtsSettings(speed: Float, pitch: Float) =
        userSettingsDao.updateTtsSettings(speed, pitch)

    suspend fun updateVoiceCommandEnabled(enabled: Boolean) =
        userSettingsDao.updateVoiceCommandEnabled(enabled)

    suspend fun updateTipsPreference(preference: TipsPreference) =
        userSettingsDao.updateTipsPreference(preference)

    suspend fun updateLanguage(language: String) =
        userSettingsDao.updateLanguage(language)

    /**
     * Marks onboarding as completed.
     * Used by OnboardingViewModel.
     */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        val current = userSettingsDao.getSettingsOnce()
        if (current != null) {
            userSettingsDao.updateSettings(current.copy(hasCompletedOnboarding = completed))
        } else {
            userSettingsDao.insertSettings(
                UserSettingsEntity(hasCompletedOnboarding = completed)
            )
        }
    }

    suspend fun getPreferredLanguage(): String =
        userSettingsDao.getPreferredLanguage() ?: "en"

    suspend fun isDarkTheme(): Boolean =
        userSettingsDao.isDarkTheme() ?: false

    suspend fun deleteSettings() =
        userSettingsDao.deleteSettings()
}
package com.resq.app.data.repository

import com.resq.app.data.dao.UserSettingsDao
import com.resq.app.data.entity.UserSettingsEntity
import com.resq.app.data.model.FontSize
import com.resq.app.data.model.TipsPreference
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user settings operations.
 * Handles app preferences and accessibility settings.
 */
@Singleton
class UserSettingsRepository @Inject constructor(
    private val userSettingsDao: UserSettingsDao
) {

    fun getSettings(): Flow<UserSettingsEntity?> = userSettingsDao.getSettings()

    suspend fun getSettingsOnce(): UserSettingsEntity? = userSettingsDao.getSettingsOnce()

    suspend fun insertSettings(settings: UserSettingsEntity) = userSettingsDao.insertSettings(settings)

    suspend fun updateSettings(settings: UserSettingsEntity) = userSettingsDao.updateSettings(settings)

    suspend fun updateDarkTheme(isDark: Boolean) = userSettingsDao.updateDarkTheme(isDark)

    suspend fun updateFontSize(fontSize: FontSize) = userSettingsDao.updateFontSize(fontSize)

    suspend fun updateTtsEnabled(enabled: Boolean) = userSettingsDao.updateTtsEnabled(enabled)

    suspend fun updateTtsSettings(speed: Float, pitch: Float) =
        userSettingsDao.updateTtsSettings(speed, pitch)

    suspend fun updateVoiceCommandEnabled(enabled: Boolean) =
        userSettingsDao.updateVoiceCommandEnabled(enabled)

    suspend fun updateTipsPreference(preference: TipsPreference) =
        userSettingsDao.updateTipsPreference(preference)

    suspend fun updateLanguage(language: String) = userSettingsDao.updateLanguage(language)

    suspend fun getPreferredLanguage(): String = userSettingsDao.getPreferredLanguage() ?: "en"

    suspend fun isDarkTheme(): Boolean = userSettingsDao.isDarkTheme() ?: false

    suspend fun deleteSettings() = userSettingsDao.deleteSettings()
}
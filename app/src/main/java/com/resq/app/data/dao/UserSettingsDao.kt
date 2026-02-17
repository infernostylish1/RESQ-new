package com.resq.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.resq.app.data.entity.UserSettingsEntity
import com.resq.app.data.model.FontSize
import com.resq.app.data.model.TipsPreference
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for UserSettings operations.
 * Handles app settings and preferences.
 */
@Dao
interface UserSettingsDao {

    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun getSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun getSettingsOnce(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: UserSettingsEntity)

    @Update
    suspend fun updateSettings(settings: UserSettingsEntity)

    @Query("UPDATE user_settings SET is_dark_theme = :isDark, updated_at = :updatedAt WHERE id = 1")
    suspend fun updateDarkTheme(isDark: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings SET font_size = :fontSize, updated_at = :updatedAt WHERE id = 1")
    suspend fun updateFontSize(fontSize: FontSize, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings SET is_tts_enabled = :enabled, updated_at = :updatedAt WHERE id = 1")
    suspend fun updateTtsEnabled(enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings SET tts_speed = :speed, tts_pitch = :pitch, updated_at = :updatedAt WHERE id = 1")
    suspend fun updateTtsSettings(speed: Float, pitch: Float, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings SET is_voice_command_enabled = :enabled, updated_at = :updatedAt WHERE id = 1")
    suspend fun updateVoiceCommandEnabled(enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings SET tips_preference = :preference, updated_at = :updatedAt WHERE id = 1")
    suspend fun updateTipsPreference(preference: TipsPreference, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_settings SET preferred_language = :language, updated_at = :updatedAt WHERE id = 1")
    suspend fun updateLanguage(language: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT preferred_language FROM user_settings WHERE id = 1")
    suspend fun getPreferredLanguage(): String?

    @Query("SELECT is_dark_theme FROM user_settings WHERE id = 1")
    suspend fun isDarkTheme(): Boolean?

    @Query("DELETE FROM user_settings")
    suspend fun deleteSettings()
}
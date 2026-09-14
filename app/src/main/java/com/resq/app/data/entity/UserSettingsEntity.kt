package com.resq.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.resq.app.data.model.FontSize
import com.resq.app.data.model.TipsPreference

/**
 * Entity representing user app settings.
 * This table should only contain one row (singleton pattern).
 */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,

    @ColumnInfo(name = "is_dark_theme")
    val isDarkTheme: Boolean = false,

    @ColumnInfo(name = "font_size")
    val fontSize: FontSize = FontSize.MEDIUM,

    @ColumnInfo(name = "is_tts_enabled")
    val isTtsEnabled: Boolean = true,

    @ColumnInfo(name = "tts_speed")
    val ttsSpeed: Float = 1.0f,

    @ColumnInfo(name = "tts_pitch")
    val ttsPitch: Float = 1.0f,

    @ColumnInfo(name = "is_voice_command_enabled")
    val isVoiceCommandEnabled: Boolean = true,

    @ColumnInfo(name = "tips_preference")
    val tipsPreference: TipsPreference = TipsPreference.RECOMMENDED,

    @ColumnInfo(name = "preferred_language")
    val preferredLanguage: String = "en",

    @ColumnInfo(name = "has_completed_onboarding")
    val hasCompletedOnboarding: Boolean = false,

    @ColumnInfo(name = "terms_accepted")
    val termsAccepted: Boolean = false,

    @ColumnInfo(name = "privacy_accepted")
    val privacyAccepted: Boolean = false,

    @ColumnInfo(name = "last_tip_id")
    val lastTipId: Long? = null,

    @ColumnInfo(name = "last_tip_date")
    val lastTipDate: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
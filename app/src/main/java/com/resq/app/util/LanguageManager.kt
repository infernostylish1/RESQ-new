package com.resq.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages language settings for RESQ.
 * Supports English and Tagalog (Filipino).
 */
@Singleton
class LanguageManager @Inject constructor() {

    companion object {
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_TAGALOG = "tl"

        val SUPPORTED_LANGUAGES = listOf(
            LanguageOption(LANGUAGE_ENGLISH, "English", "English"),
            LanguageOption(LANGUAGE_TAGALOG, "Tagalog", "Filipino")
        )
    }

    fun applyLanguage(context: Context, languageCode: String): Context {
        val locale = getLocaleForCode(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }

    fun getLocaleForCode(languageCode: String): Locale {
        return when (languageCode.lowercase()) {
            LANGUAGE_TAGALOG, "fil" -> Locale("fil", "PH")
            else -> Locale.ENGLISH
        }
    }

    fun getDisplayName(languageCode: String): String {
        return SUPPORTED_LANGUAGES.find { it.code == languageCode }?.displayName ?: "English"
    }

    fun getNativeName(languageCode: String): String {
        return SUPPORTED_LANGUAGES.find { it.code == languageCode }?.nativeName ?: "English"
    }

    fun isLanguageSupported(languageCode: String): Boolean {
        return SUPPORTED_LANGUAGES.any { it.code == languageCode }
    }

    fun getDeviceLanguage(): String {
        val locale = Locale.getDefault()
        return when {
            locale.language == "fil" || locale.language == "tl" -> LANGUAGE_TAGALOG
            else -> LANGUAGE_ENGLISH
        }
    }
}

data class LanguageOption(
    val code: String,
    val displayName: String,
    val nativeName: String
)
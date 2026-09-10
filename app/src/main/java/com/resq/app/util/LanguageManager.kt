package com.resq.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageManager @Inject constructor() {

    companion object {
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_TAGALOG = "tl"

        val SUPPORTED_LANGUAGES = listOf(
            Language(LANGUAGE_ENGLISH, "English"),
            Language(LANGUAGE_TAGALOG, "Tagalog")
        )
    }

    fun setAppLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return context.createConfigurationContext(config)
    }

    fun getCurrentLanguage(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]?.language ?: LANGUAGE_ENGLISH
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale?.language ?: LANGUAGE_ENGLISH
        }
    }

    data class Language(
        val code: String,
        val displayName: String
    )
}
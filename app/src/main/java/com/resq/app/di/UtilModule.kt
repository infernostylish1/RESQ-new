package com.resq.app.di

import com.resq.app.util.DateTimeHelper
import com.resq.app.util.FontSizeManager
import com.resq.app.util.LanguageManager
import com.resq.app.util.PermissionHelper
import com.resq.app.util.PhoneDialerHelper
import com.resq.app.util.SearchHelper
import com.resq.app.util.TextToSpeechManager
import com.resq.app.util.ThemeManager
import com.resq.app.util.VoiceCommandManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides utility class dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object UtilModule {

    @Provides
    @Singleton
    fun provideTextToSpeechManager(): TextToSpeechManager = TextToSpeechManager()

    @Provides
    @Singleton
    fun provideVoiceCommandManager(): VoiceCommandManager = VoiceCommandManager()

    @Provides
    @Singleton
    fun provideLanguageManager(): LanguageManager = LanguageManager()

    @Provides
    @Singleton
    fun provideDateTimeHelper(): DateTimeHelper = DateTimeHelper()

    @Provides
    @Singleton
    fun provideThemeManager(): ThemeManager = ThemeManager()

    @Provides
    @Singleton
    fun provideFontSizeManager(): FontSizeManager = FontSizeManager()

    @Provides
    @Singleton
    fun providePermissionHelper(): PermissionHelper = PermissionHelper()

    @Provides
    @Singleton
    fun providePhoneDialerHelper(): PhoneDialerHelper = PhoneDialerHelper()

    @Provides
    @Singleton
    fun provideSearchHelper(): SearchHelper = SearchHelper()
}
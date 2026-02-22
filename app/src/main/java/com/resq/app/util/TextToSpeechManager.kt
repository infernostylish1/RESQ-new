package com.resq.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Text-to-Speech functionality for RESQ.
 * Provides offline TTS capabilities in English and Tagalog.
 */
@Singleton
class TextToSpeechManager @Inject constructor() {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    private val _ttsState = MutableStateFlow<TTSState>(TTSState.Idle)
    val ttsState: StateFlow<TTSState> = _ttsState.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var currentSpeed: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentLanguage: String = "en"

    private var pendingText: String? = null
    private var utteranceId = 0

    /**
     * Initialize TTS engine
     */
    fun initialize(context: Context, onReady: (() -> Unit)? = null) {
        if (isInitialized && textToSpeech != null) {
            onReady?.invoke()
            return
        }

        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                _isReady.value = true
                setupTTS()
                onReady?.invoke()
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                _ttsState.value = TTSState.Error("TTS initialization failed")
            }
        }
    }

    private fun setupTTS() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _ttsState.value = TTSState.Speaking
            }

            override fun onDone(utteranceId: String?) {
                _ttsState.value = TTSState.Idle
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _ttsState.value = TTSState.Error("Speech error occurred")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _ttsState.value = TTSState.Error("Speech error: $errorCode")
            }
        })

        setLanguage(currentLanguage)
        setSpeechRate(currentSpeed)
        setPitch(currentPitch)
    }

    /**
     * Speak the given text
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!isInitialized || textToSpeech == null) {
            pendingText = text
            Log.w(TAG, "TTS not initialized, text queued")
            return
        }

        val params = android.os.Bundle()
        val currentUtteranceId = "tts_${++utteranceId}"

        textToSpeech?.speak(text, queueMode, params, currentUtteranceId)
        _ttsState.value = TTSState.Speaking
    }

    /**
     * Speak text that was queued before initialization
     */
    fun speakPendingText() {
        pendingText?.let {
            speak(it)
            pendingText = null
        }
    }

    /**
     * Stop speaking immediately
     */
    fun stop() {
        textToSpeech?.stop()
        _ttsState.value = TTSState.Idle
    }

    /**
     * Check if TTS is currently speaking
     */
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking == true
    }

    /**
     * Set speech rate (0.1 to 2.0, default 1.0)
     */
    fun setSpeechRate(rate: Float) {
        currentSpeed = rate.coerceIn(0.1f, 2.0f)
        textToSpeech?.setSpeechRate(currentSpeed)
    }

    /**
     * Set pitch (0.1 to 2.0, default 1.0)
     */
    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.1f, 2.0f)
        textToSpeech?.setPitch(currentPitch)
    }

    /**
     * Set language for TTS
     */
    fun setLanguage(languageCode: String): Boolean {
        currentLanguage = languageCode

        val locale = when (languageCode.lowercase()) {
            "tl", "fil", "tagalog" -> Locale("fil", "PH")
            else -> Locale.ENGLISH
        }

        val result = textToSpeech?.setLanguage(locale)

        return when (result) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                Log.w(TAG, "Language $languageCode not fully supported, falling back to English")
                textToSpeech?.setLanguage(Locale.ENGLISH)
                false
            }
            else -> true
        }
    }

    /**
     * Check if a language is available
     */
    fun isLanguageAvailable(languageCode: String): Boolean {
        val locale = when (languageCode.lowercase()) {
            "tl", "fil", "tagalog" -> Locale("fil", "PH")
            else -> Locale.ENGLISH
        }

        val result = textToSpeech?.isLanguageAvailable(locale)
        return result == TextToSpeech.LANG_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    /**
     * Get available languages
     */
    fun getAvailableLanguages(): List<Locale> {
        return textToSpeech?.availableLanguages?.toList() ?: emptyList()
    }

    /**
     * Update TTS settings
     */
    fun updateSettings(speed: Float, pitch: Float) {
        setSpeechRate(speed)
        setPitch(pitch)
    }

    /**
     * Release TTS resources
     */
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
        _isReady.value = false
        _ttsState.value = TTSState.Idle
    }

    companion object {
        private const val TAG = "TextToSpeechManager"
    }
}

/**
 * Represents the current state of TTS
 */
sealed class TTSState {
    data object Idle : TTSState()
    data object Speaking : TTSState()
    data object Paused : TTSState()
    data class Error(val message: String) : TTSState()
}
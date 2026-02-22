package com.resq.app.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages voice command recognition for RESQ.
 * Provides offline speech recognition with command parsing.
 */
@Singleton
class VoiceCommandManager @Inject constructor() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    private val _lastCommand = MutableStateFlow<VoiceCommand?>(null)
    val lastCommand: StateFlow<VoiceCommand?> = _lastCommand.asStateFlow()

    private var currentLanguage: String = "en"
    private var commandCallback: ((VoiceCommand) -> Unit)? = null

    /**
     * Initialize speech recognizer
     */
    fun initialize(context: Context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _recognitionState.value = RecognitionState.Error("Speech recognition not available")
            Log.e(TAG, "Speech recognition not available on this device")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }

        _recognitionState.value = RecognitionState.Ready
        Log.d(TAG, "Speech recognizer initialized")
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _recognitionState.value = RecognitionState.Listening
                Log.d(TAG, "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed - can be used for visual feedback
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Audio buffer received
            }

            override fun onEndOfSpeech() {
                _recognitionState.value = RecognitionState.Processing
                isListening = false
                Log.d(TAG, "Speech ended")
            }

            override fun onError(error: Int) {
                val errorMessage = getErrorMessage(error)
                _recognitionState.value = RecognitionState.Error(errorMessage)
                isListening = false
                Log.e(TAG, "Recognition error: $errorMessage")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull() ?: ""

                if (recognizedText.isNotEmpty()) {
                    val command = parseCommand(recognizedText)
                    _lastCommand.value = command
                    commandCallback?.invoke(command)
                    _recognitionState.value = RecognitionState.Result(recognizedText, command)
                    Log.d(TAG, "Recognized: $recognizedText -> Command: $command")
                } else {
                    _recognitionState.value = RecognitionState.Error("No speech recognized")
                }

                isListening = false
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull() ?: ""
                if (partialText.isNotEmpty()) {
                    _recognitionState.value = RecognitionState.PartialResult(partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "Recognition event: $eventType")
            }
        }
    }

    /**
     * Start listening for voice commands
     */
    fun startListening(callback: ((VoiceCommand) -> Unit)? = null) {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }

        if (speechRecognizer == null) {
            _recognitionState.value = RecognitionState.Error("Speech recognizer not initialized")
            return
        }

        commandCallback = callback
        isListening = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLocaleForLanguage())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            _recognitionState.value = RecognitionState.Starting
        } catch (e: Exception) {
            _recognitionState.value = RecognitionState.Error("Failed to start listening: ${e.message}")
            isListening = false
            Log.e(TAG, "Failed to start listening", e)
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        if (!isListening) return

        speechRecognizer?.stopListening()
        isListening = false
        _recognitionState.value = RecognitionState.Idle
    }

    /**
     * Cancel recognition
     */
    fun cancel() {
        speechRecognizer?.cancel()
        isListening = false
        _recognitionState.value = RecognitionState.Idle
    }

    /**
     * Set recognition language
     */
    fun setLanguage(languageCode: String) {
        currentLanguage = languageCode
    }

    private fun getLocaleForLanguage(): String {
        return when (currentLanguage.lowercase()) {
            "tl", "fil", "tagalog" -> "fil-PH"
            else -> "en-US"
        }
    }

    /**
     * Parse recognized text into a VoiceCommand
     */
    fun parseCommand(text: String): VoiceCommand {
        val normalizedText = text.lowercase().trim()

        // Navigation commands
        when {
            matchesAny(normalizedText, NAV_HOME_KEYWORDS) -> return VoiceCommand.NavigateHome
            matchesAny(normalizedText, NAV_DISASTERS_KEYWORDS) -> return VoiceCommand.NavigateDisasters
            matchesAny(normalizedText, NAV_FIRSTAID_KEYWORDS) -> return VoiceCommand.NavigateFirstAid
            matchesAny(normalizedText, NAV_TIPS_KEYWORDS) -> return VoiceCommand.NavigateTips
            matchesAny(normalizedText, NAV_SETTINGS_KEYWORDS) -> return VoiceCommand.NavigateSettings
            matchesAny(normalizedText, NAV_CONTACTS_KEYWORDS) -> return VoiceCommand.NavigateContacts
        }

        // Back command
        if (matchesAny(normalizedText, BACK_KEYWORDS)) {
            return VoiceCommand.GoBack
        }

        // Scroll commands
        if (matchesAny(normalizedText, SCROLL_UP_KEYWORDS)) {
            return VoiceCommand.ScrollUp
        }
        if (matchesAny(normalizedText, SCROLL_DOWN_KEYWORDS)) {
            return VoiceCommand.ScrollDown
        }

        // TTS commands
        when {
            matchesAny(normalizedText, READ_KEYWORDS) -> return VoiceCommand.ReadContent
            matchesAny(normalizedText, STOP_KEYWORDS) -> return VoiceCommand.StopReading
            matchesAny(normalizedText, PAUSE_KEYWORDS) -> return VoiceCommand.PauseReading
            matchesAny(normalizedText, RESUME_KEYWORDS) -> return VoiceCommand.ResumeReading
        }

        // Tip commands
        when {
            matchesAny(normalizedText, RANDOM_TIP_KEYWORDS) -> return VoiceCommand.GetRandomTip
            matchesAny(normalizedText, NEXT_TIP_KEYWORDS) -> return VoiceCommand.GetNextTip
        }

        // Emergency call
        if (matchesAny(normalizedText, EMERGENCY_CALL_KEYWORDS)) {
            return VoiceCommand.CallEmergency
        }

        // Call contact command
        CALL_PATTERNS.forEach { pattern ->
            val match = pattern.find(normalizedText)
            if (match != null) {
                val contactName = match.groupValues.getOrNull(1)?.trim()
                if (!contactName.isNullOrEmpty()) {
                    return VoiceCommand.CallContact(contactName)
                }
            }
        }

        // Search for specific disaster
        for ((disasterKey, keywords) in VoiceCommand.DISASTER_KEYWORDS) {
            if (keywords.any { normalizedText.contains(it) }) {
                return if (matchesAny(normalizedText, SEARCH_KEYWORDS)) {
                    VoiceCommand.SearchDisaster(disasterKey)
                } else {
                    VoiceCommand.OpenDisaster(disasterKey)
                }
            }
        }

        // Search for specific first aid
        for ((categoryKey, keywords) in VoiceCommand.FIRST_AID_KEYWORDS) {
            if (keywords.any { normalizedText.contains(it) }) {
                return if (matchesAny(normalizedText, SEARCH_KEYWORDS)) {
                    VoiceCommand.SearchFirstAid(categoryKey)
                } else {
                    VoiceCommand.OpenFirstAidCategory(categoryKey)
                }
            }
        }

        // General search
        SEARCH_PATTERNS.forEach { pattern ->
            val match = pattern.find(normalizedText)
            if (match != null) {
                val query = match.groupValues.getOrNull(1)?.trim()
                if (!query.isNullOrEmpty()) {
                    return VoiceCommand.GeneralSearch(query)
                }
            }
        }

        return VoiceCommand.Unknown(text)
    }

    private fun matchesAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error: $errorCode"
        }
    }

    /**
     * Check if currently listening
     */
    fun isCurrentlyListening(): Boolean = isListening

    /**
     * Release resources
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        _recognitionState.value = RecognitionState.Idle
    }

    companion object {
        private const val TAG = "VoiceCommandManager"

        private val NAV_HOME_KEYWORDS = listOf(
            "go home", "home", "main", "dashboard", "tahanan", "pangunahin"
        )
        private val NAV_DISASTERS_KEYWORDS = listOf(
            "disasters", "disaster information", "go to disasters", "sakuna",
            "disaster", "kalamidad", "show disasters"
        )
        private val NAV_FIRSTAID_KEYWORDS = listOf(
            "first aid", "firstaid", "emergency response", "medical",
            "pertolya", "go to first aid"
        )
        private val NAV_TIPS_KEYWORDS = listOf(
            "tips", "disaster tips", "advice", "payo", "mga tip", "go to tips"
        )
        private val NAV_SETTINGS_KEYWORDS = listOf(
            "settings", "preferences", "options", "configure", "setting", "mga setting"
        )
        private val NAV_CONTACTS_KEYWORDS = listOf(
            "contacts", "emergency contacts", "hotlines", "numbers",
            "mga contact", "emergency numbers"
        )

        private val BACK_KEYWORDS = listOf("go back", "back", "return", "balik", "previous")
        private val SCROLL_UP_KEYWORDS = listOf("scroll up", "up", "taas", "pataas")
        private val SCROLL_DOWN_KEYWORDS = listOf("scroll down", "down", "baba", "pababa")

        private val READ_KEYWORDS = listOf("read", "speak", "basahin", "sabihin", "read aloud")
        private val STOP_KEYWORDS = listOf("stop", "tigil", "stop reading", "quiet", "silence")
        private val PAUSE_KEYWORDS = listOf("pause", "sandali", "pause reading")
        private val RESUME_KEYWORDS = listOf("resume", "continue", "ituloy", "resume reading")

        private val RANDOM_TIP_KEYWORDS = listOf("random tip", "give tip", "tip please", "magbigay ng tip")
        private val NEXT_TIP_KEYWORDS = listOf("next tip", "another tip", "susunod na tip")

        private val EMERGENCY_CALL_KEYWORDS = listOf(
            "emergency", "call emergency", "911", "help", "tulong", "emergency call"
        )

        private val SEARCH_KEYWORDS = listOf("search", "find", "look for", "hanapin", "maghanap")

        private val CALL_PATTERNS = listOf(
            Regex("call\\s+(.+)"),
            Regex("dial\\s+(.+)"),
            Regex("tawagan\\s+(.+)"),
            Regex("contact\\s+(.+)")
        )

        private val SEARCH_PATTERNS = listOf(
            Regex("search\\s+(?:for\\s+)?(.+)"),
            Regex("find\\s+(.+)"),
            Regex("look\\s+(?:for\\s+)?(.+)"),
            Regex("hanapin\\s+(.+)")
        )
    }
}

/**
 * Represents the current state of voice recognition
 */
sealed class RecognitionState {
    data object Idle : RecognitionState()
    data object Ready : RecognitionState()
    data object Starting : RecognitionState()
    data object Listening : RecognitionState()
    data object Processing : RecognitionState()
    data class PartialResult(val text: String) : RecognitionState()
    data class Result(val text: String, val command: VoiceCommand) : RecognitionState()
    data class Error(val message: String) : RecognitionState()
}
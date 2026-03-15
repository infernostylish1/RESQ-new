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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages voice command recognition for RESQ.
 *
 * parseCommand() now strips intent prefixes before keyword matching so natural
 * questions like "what is an earthquake" or "paano ang lindol" resolve correctly.
 *
 * After parsing, OpenDisaster / OpenFirstAidCategory are emitted with the
 * canonical Title-Case name that matches the DB.  VoiceCommandViewModel then
 * does a one-shot DB lookup and re-emits OpenDisasterDetail / OpenFirstAidDetail
 * with the real id so MainActivity can navigate directly to the detail screen.
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

    // ── Initialisation ────────────────────────────────────────────────────────

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

    // ── Recognition listener ──────────────────────────────────────────────────

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _recognitionState.value = RecognitionState.Listening
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _recognitionState.value = RecognitionState.Processing
                isListening = false
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
                    Log.d(TAG, "Recognized: \"$recognizedText\" → Command: $command")
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

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    // ── Listening control ─────────────────────────────────────────────────────

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

    fun stopListening() {
        if (!isListening) return
        speechRecognizer?.stopListening()
        isListening = false
        _recognitionState.value = RecognitionState.Idle
    }

    fun cancel() {
        speechRecognizer?.cancel()
        isListening = false
        _recognitionState.value = RecognitionState.Idle
    }

    fun setLanguage(languageCode: String) {
        currentLanguage = languageCode
    }

    private fun getLocaleForLanguage(): String = when (currentLanguage.lowercase()) {
        "tl", "fil", "tagalog" -> "fil-PH"
        else -> "en-US"
    }

    fun isCurrentlyListening(): Boolean = isListening

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        _recognitionState.value = RecognitionState.Idle
    }

    // ── Command parsing ───────────────────────────────────────────────────────

    /**
     * Converts raw recognized text into a [VoiceCommand].
     *
     * Strategy:
     * 1. Normalize to lowercase.
     * 2. Try exact navigation / control / TTS / tip / emergency keywords first
     *    (these need no prefix stripping).
     * 3. Strip intent prefixes ("what is", "paano ang", etc.) to get the subject.
     * 4. Match the subject against disaster and first-aid keyword tables.
     * 5. Fall back to general search patterns.
     * 6. Emit Unknown if nothing matches.
     *
     * Note: This method emits [VoiceCommand.OpenDisaster] / [VoiceCommand.OpenFirstAidCategory]
     * with the canonical Title-Case key (matching the DB name column).
     * [VoiceCommandViewModel] will resolve these to [VoiceCommand.OpenDisasterDetail] /
     * [VoiceCommand.OpenFirstAidDetail] via a one-shot DB lookup before forwarding to MainActivity.
     */
    fun parseCommand(text: String): VoiceCommand {
        val normalized = text.lowercase().trim()

        // ── 1. Hard navigation commands ───────────────────────────────────────
        when {
            matchesAny(normalized, NAV_HOME_KEYWORDS)     -> return VoiceCommand.NavigateHome
            matchesAny(normalized, NAV_DISASTERS_KEYWORDS) -> return VoiceCommand.NavigateDisasters
            matchesAny(normalized, NAV_FIRSTAID_KEYWORDS)  -> return VoiceCommand.NavigateFirstAid
            matchesAny(normalized, NAV_TIPS_KEYWORDS)      -> return VoiceCommand.NavigateTips
            matchesAny(normalized, NAV_SETTINGS_KEYWORDS)  -> return VoiceCommand.NavigateSettings
            matchesAny(normalized, NAV_CONTACTS_KEYWORDS)  -> return VoiceCommand.NavigateContacts
        }

        // ── 2. App-control commands ───────────────────────────────────────────
        if (matchesAny(normalized, BACK_KEYWORDS))        return VoiceCommand.GoBack
        if (matchesAny(normalized, SCROLL_UP_KEYWORDS))   return VoiceCommand.ScrollUp
        if (matchesAny(normalized, SCROLL_DOWN_KEYWORDS)) return VoiceCommand.ScrollDown

        // ── 3. TTS commands ───────────────────────────────────────────────────
        when {
            matchesAny(normalized, READ_KEYWORDS)   -> return VoiceCommand.ReadContent
            matchesAny(normalized, STOP_KEYWORDS)   -> return VoiceCommand.StopReading
            matchesAny(normalized, PAUSE_KEYWORDS)  -> return VoiceCommand.PauseReading
            matchesAny(normalized, RESUME_KEYWORDS) -> return VoiceCommand.ResumeReading
        }

        // ── 4. Tip commands ───────────────────────────────────────────────────
        when {
            matchesAny(normalized, RANDOM_TIP_KEYWORDS) -> return VoiceCommand.GetRandomTip
            matchesAny(normalized, NEXT_TIP_KEYWORDS)   -> return VoiceCommand.GetNextTip
        }

        // ── 5. Emergency call ─────────────────────────────────────────────────
        if (matchesAny(normalized, EMERGENCY_CALL_KEYWORDS)) return VoiceCommand.CallEmergency

        // ── 6. Call a specific contact ────────────────────────────────────────
        CALL_PATTERNS.forEach { pattern ->
            val match = pattern.find(normalized)
            val contactName = match?.groupValues?.getOrNull(1)?.trim()
            if (!contactName.isNullOrEmpty()) return VoiceCommand.CallContact(contactName)
        }

        // ── 7. Strip intent prefix then match subject ─────────────────────────
        val subject = stripIntentPrefix(normalized)

        // 7a. Disaster keywords
        for ((disasterKey, keywords) in VoiceCommand.DISASTER_KEYWORDS) {
            if (keywords.any { subject.contains(it) || normalized.contains(it) }) {
                // disasterKey is already Title-Case (e.g. "Earthquake")
                return VoiceCommand.OpenDisaster(disasterKey)
            }
        }

        // 7b. First-aid keywords
        for ((categoryKey, keywords) in VoiceCommand.FIRST_AID_KEYWORDS) {
            if (keywords.any { subject.contains(it) || normalized.contains(it) }) {
                // categoryKey is already Title-Case (e.g. "CPR & Choking")
                return VoiceCommand.OpenFirstAidCategory(categoryKey)
            }
        }

        // ── 8. General search patterns ────────────────────────────────────────
        SEARCH_PATTERNS.forEach { pattern ->
            val match = pattern.find(normalized)
            val query = match?.groupValues?.getOrNull(1)?.trim()
            if (!query.isNullOrEmpty()) return VoiceCommand.GeneralSearch(query)
        }

        // ── 9. Nothing matched ────────────────────────────────────────────────
        return VoiceCommand.Unknown(text)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun matchesAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }

    /**
     * Removes the longest matching intent prefix from [text] and returns
     * the remaining subject string, also stripped of leading filler articles.
     *
     * e.g. "what is an earthquake" → "earthquake"
     *      "paano ang lindol"       → "lindol"
     *      "how to treat burns"     → "burns"
     */
    private fun stripIntentPrefix(text: String): String {
        // Sort longest-first so "what to do during" wins over "what"
        val sortedPrefixes = VoiceCommand.INTENT_PREFIXES.sortedByDescending { it.length }
        var stripped = text

        for (prefix in sortedPrefixes) {
            if (stripped.startsWith(prefix)) {
                stripped = stripped.removePrefix(prefix).trim()
                break
            }
        }

        // Remove leading filler words/articles that may remain
        val fillers = listOf("an", "a", "the", "ang", "yung", "mga", "about", "during", "ng", "sa")
        var changed = true
        while (changed) {
            changed = false
            for (filler in fillers) {
                val withSpace = "$filler "
                if (stripped.startsWith(withSpace)) {
                    stripped = stripped.removePrefix(withSpace).trim()
                    changed = true
                }
            }
        }

        return stripped
    }

    private fun getErrorMessage(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO               -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT              -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK             -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT     -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH            -> "No speech match found"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY     -> "Recognition service busy"
        SpeechRecognizer.ERROR_SERVER              -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT      -> "No speech input"
        else                                       -> "Unknown error: $errorCode"
    }

    // ── Keyword tables ────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "VoiceCommandManager"

        private val NAV_HOME_KEYWORDS = listOf(
            "go home", "home screen", "go to home", "tahanan", "pangunahin",
            "main screen", "main page"
        )
        private val NAV_DISASTERS_KEYWORDS = listOf(
            "go to disasters", "open disasters", "show disasters",
            "disaster list", "disaster module", "sakuna", "kalamidad",
            "list of disasters"
        )
        private val NAV_FIRSTAID_KEYWORDS = listOf(
            "go to first aid", "open first aid", "show first aid",
            "first aid list", "first aid module", "pertolya",
            "medical help", "first aid menu"
        )
        private val NAV_TIPS_KEYWORDS = listOf(
            "go to tips", "open tips", "show tips",
            "disaster tips", "mga tip", "payo", "tip list"
        )
        private val NAV_SETTINGS_KEYWORDS = listOf(
            "go to settings", "open settings", "settings menu",
            "preferences", "configure", "mga setting", "setting"
        )
        private val NAV_CONTACTS_KEYWORDS = listOf(
            "go to contacts", "open contacts", "emergency contacts",
            "hotlines", "emergency numbers", "mga contact",
            "contact list", "show contacts"
        )

        // These are intentionally narrow — single words like "home", "tips",
        // "disasters" alone are NOT in NAV_*_KEYWORDS because those same words
        // appear in subject phrases like "what is a flood" or "tips about fire".
        // The navigation keywords require an explicit navigation verb.

        private val BACK_KEYWORDS = listOf(
            "go back", "back", "return", "balik", "previous", "bumalik"
        )
        private val SCROLL_UP_KEYWORDS = listOf(
            "scroll up", "scroll to top", "taas", "pataas", "umakyat"
        )
        private val SCROLL_DOWN_KEYWORDS = listOf(
            "scroll down", "scroll to bottom", "baba", "pababa", "bumaba"
        )

        private val READ_KEYWORDS = listOf(
            "read", "read aloud", "read this", "speak", "basahin",
            "sabihin", "read content", "read page"
        )
        private val STOP_KEYWORDS = listOf(
            "stop reading", "stop", "tigil", "tumigil", "silence",
            "quiet", "huwag basahin"
        )
        private val PAUSE_KEYWORDS = listOf(
            "pause", "pause reading", "sandali", "maghintay"
        )
        private val RESUME_KEYWORDS = listOf(
            "resume", "resume reading", "continue", "ituloy", "magpatuloy"
        )

        private val RANDOM_TIP_KEYWORDS = listOf(
            "random tip", "give me a tip", "tip please",
            "magbigay ng tip", "bigyan ng tip", "any tip"
        )
        private val NEXT_TIP_KEYWORDS = listOf(
            "next tip", "another tip", "susunod na tip",
            "iba pang tip", "show next tip"
        )

        private val EMERGENCY_CALL_KEYWORDS = listOf(
            "call emergency", "emergency call", "call 911", "911",
            "tulong", "help me", "call for help", "i need help"
        )

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
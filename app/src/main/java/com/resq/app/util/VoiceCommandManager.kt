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

@Singleton
class VoiceCommandManager @Inject constructor() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentLanguage: String = "en"
    private var isListening = false

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    private var onCommandRecognized: ((VoiceCommand) -> Unit)? = null

    fun initialize(context: Context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _recognitionState.value = RecognitionState.Error("Speech recognition not available")
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _recognitionState.value = RecognitionState.Listening
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _recognitionState.value = RecognitionState.Processing
        }
        override fun onError(error: Int) {
            isListening = false
            _recognitionState.value = RecognitionState.Error(getErrorMessage(error))
        }
        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                // Try all candidates — use the first that isn't Unknown
                val command = matches
                    .map { parseCommand(it) }
                    .firstOrNull { it !is VoiceCommand.Unknown }
                    ?: VoiceCommand.Unknown(matches.first())

                val best = if (command is VoiceCommand.Unknown) matches.first() else matches.first()
                _recognitionState.value = RecognitionState.Result(best, command)
                onCommandRecognized?.invoke(command)
            } else {
                _recognitionState.value = RecognitionState.Error("No speech recognized")
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partial.isNullOrEmpty()) {
                _recognitionState.value = RecognitionState.PartialResult(partial)
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening(onCommand: (VoiceCommand) -> Unit) {
        if (isListening) return
        isListening = true
        onCommandRecognized = onCommand

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // ── KEY FIX: supply both locales so STT understands Filipino ─────
            // Primary  = Filipino/Tagalog
            // Fallback = English
            // The extra EXTRA_LANGUAGE_PREFERENCE lets the engine prefer fil-PH
            // while EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE=false allows English too.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fil-PH")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fil-PH")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US"))
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            // Return up to 5 alternatives — we pick best non-Unknown parse
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        try {
            speechRecognizer?.startListening(intent)
            _recognitionState.value = RecognitionState.Starting
        } catch (e: Exception) {
            _recognitionState.value =
                RecognitionState.Error("Failed to start listening: ${e.message}")
            isListening = false
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

    fun setLanguage(languageCode: String) { currentLanguage = languageCode }
    fun isCurrentlyListening(): Boolean = isListening

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        _recognitionState.value = RecognitionState.Idle
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Command parsing
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Parse steps (order matters):
     *
     *  1.  Normalize.
     *  2.  Apply phonetic Tagalog normalizer — converts common STT mis-
     *      transcriptions into their canonical equivalents BEFORE any keyword
     *      matching.  This fixes the "Filipino sounds like English" problem.
     *  3.  Hard navigation (require explicit nav verb).
     *  4.  App-control / TTS / tip browsing / emergency.
     *  5.  Phase + disaster combo → OpenDisasterPhase.
     *  6.  Tab-switch (user already on detail screen).
     *  7.  Tip trigger phrases.
     *  8.  Strip intent prefix → disaster keywords → OpenDisaster.
     *  9.  Strip intent prefix → first-aid keywords → OpenFirstAidCategory.
     * 10.  General search patterns.
     * 11.  Unknown fallback.
     */
    fun parseCommand(rawText: String): VoiceCommand {
        // Step 1 — lowercase
        val lowered = rawText.lowercase().trim()

        // Step 2 — phonetic normalization
        val normalized = normalizeTagalog(lowered)

        Log.d(TAG, "raw='$rawText'  normalized='$normalized'")

        // ── Step 3: Hard navigation ───────────────────────────────────────────
        when {
            matchesAny(normalized, NAV_HOME_KEYWORDS)      -> return VoiceCommand.NavigateHome
            matchesAny(normalized, NAV_DISASTERS_KEYWORDS) -> return VoiceCommand.NavigateDisasters
            matchesAny(normalized, NAV_FIRSTAID_KEYWORDS)  -> return VoiceCommand.NavigateFirstAid
            matchesAny(normalized, NAV_TIPS_KEYWORDS)      -> return VoiceCommand.NavigateTips
            matchesAny(normalized, NAV_SETTINGS_KEYWORDS)  -> return VoiceCommand.NavigateSettings
            matchesAny(normalized, NAV_CONTACTS_KEYWORDS)  -> return VoiceCommand.NavigateContacts
        }

        // ── Step 4: App-control ───────────────────────────────────────────────
        if (matchesAny(normalized, BACK_KEYWORDS))        return VoiceCommand.GoBack
        if (matchesAny(normalized, SCROLL_UP_KEYWORDS))   return VoiceCommand.ScrollUp
        if (matchesAny(normalized, SCROLL_DOWN_KEYWORDS)) return VoiceCommand.ScrollDown

        when {
            matchesAny(normalized, READ_KEYWORDS)   -> return VoiceCommand.ReadContent
            matchesAny(normalized, STOP_KEYWORDS)   -> return VoiceCommand.StopReading
            matchesAny(normalized, PAUSE_KEYWORDS)  -> return VoiceCommand.PauseReading
            matchesAny(normalized, RESUME_KEYWORDS) -> return VoiceCommand.ResumeReading
        }

        when {
            matchesAny(normalized, RANDOM_TIP_KEYWORDS) -> return VoiceCommand.GetRandomTip
            matchesAny(normalized, NEXT_TIP_KEYWORDS)   -> return VoiceCommand.GetNextTip
        }

        if (matchesAny(normalized, EMERGENCY_CALL_KEYWORDS)) return VoiceCommand.CallEmergency

        CALL_PATTERNS.forEach { pattern ->
            val match = pattern.find(normalized)
            val contactName = match?.groupValues?.getOrNull(1)?.trim()
            if (!contactName.isNullOrEmpty()) return VoiceCommand.CallContact(contactName)
        }

        // ── Step 5: Phase + disaster combo ───────────────────────────────────
        // Detect a phase word first.  If we also find a disaster keyword the
        // command is OpenDisasterPhase; if only a phase with NO disaster it
        // becomes SwitchTab (user is already on a detail screen).
        val detectedPhase: String? = VoiceCommand.PHASE_KEYWORDS.entries
            .firstOrNull { (_, keywords) -> keywords.any { normalized.contains(it) } }
            ?.key

        if (detectedPhase != null) {
            // Do we also have a disaster keyword?
            for ((disasterKey, keywords) in VoiceCommand.DISASTER_KEYWORDS) {
                if (keywords.any { normalized.contains(it) }) {
                    return VoiceCommand.OpenDisasterPhase(disasterKey, detectedPhase)
                }
            }
            // Phase word present, no disaster → switch tab on current screen
            return VoiceCommand.SwitchTab(detectedPhase)
        }

        // ── Step 6: Generic tab-switch (steps, symptoms, treatment, etc.) ─────
        for ((tabLabel, keywords) in VoiceCommand.TAB_KEYWORD_MAP) {
            if (keywords.any { normalized.contains(it) }) {
                return VoiceCommand.SwitchTab(tabLabel)
            }
        }

        // ── Step 7: Tip trigger phrases ───────────────────────────────────────
        val hasTipTrigger = VoiceCommand.TIP_TRIGGER_PHRASES.any { normalized.contains(it) }
        if (hasTipTrigger) {
            for ((disasterKey, keywords) in VoiceCommand.DISASTER_KEYWORDS) {
                if (keywords.any { normalized.contains(it) }) {
                    return VoiceCommand.OpenTipDetail(disasterKey)
                }
            }
            return VoiceCommand.NavigateTips
        }

        // ── Steps 8–9: Strip prefix, match subject ────────────────────────────
        val subject = stripIntentPrefix(normalized)

        for ((disasterKey, keywords) in VoiceCommand.DISASTER_KEYWORDS) {
            if (keywords.any { subject.contains(it) || normalized.contains(it) }) {
                return VoiceCommand.OpenDisaster(disasterKey)
            }
        }

        for ((categoryKey, keywords) in VoiceCommand.FIRST_AID_KEYWORDS) {
            if (keywords.any { subject.contains(it) || normalized.contains(it) }) {
                return VoiceCommand.OpenFirstAidCategory(categoryKey)
            }
        }

        // ── Step 10: General search ───────────────────────────────────────────
        SEARCH_PATTERNS.forEach { pattern ->
            val match = pattern.find(normalized)
            val query = match?.groupValues?.getOrNull(1)?.trim()
            if (!query.isNullOrEmpty()) return VoiceCommand.GeneralSearch(query)
        }

        return VoiceCommand.Unknown(rawText)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Tagalog phonetic normalizer
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Google STT (English model) transcribes Filipino words phonetically.
     * This function replaces the most common mis-transcriptions with the
     * canonical Tagalog words so all keyword tables work correctly.
     *
     * Each pair is  phonetic → canonical.  Longer strings are checked first
     * (the list is pre-sorted in [applyPhoneticReplacements]).
     */
    private fun normalizeTagalog(text: String): String {
        var result = text

        // ── Disaster-name phonetics ───────────────────────────────────────────
        result = result
            .replace("lin doll", "lindol")
            .replace("lin-dol",  "lindol")
            .replace("lean dol", "lindol")
            .replace("lean doll","lindol")
            .replace("lin dol",  "lindol")
            .replace("lee-nol",  "lindol")
            .replace("bag yo",   "bagyo")
            .replace("bag-yo",   "bagyo")
            .replace("ba huh",   "baha")
            .replace("ba-ha",    "baha")
            .replace("bool can", "bulkan")
            .replace("bool-can", "bulkan")
            .replace("bul can",  "bulkan")
            .replace("goo ho",   "guho")
            .replace("su nog",   "sunog")
            .replace("su-nog",   "sunog")
            .replace("tag tu yot","tagtuyot")
            .replace("tag-tuyot","tagtuyot")

        // ── Phase-word phonetics ──────────────────────────────────────────────
        result = result
            // "bago" → before
            .replace("bag go",   "bago")
            .replace("bag-go",   "bago")
            .replace("ba-go",    "bago")
            // "habang" → during
            .replace("ha-bang",  "habang")
            .replace("ha bang",  "habang")
            .replace("hah bang", "habang")
            .replace("habit ang","habang")
            // "pagkatapos" → after
            .replace("pag-ka-tapos","pagkatapos")
            .replace("pag ka tapos","pagkatapos")
            .replace("mag-ka-tapos","pagkatapos")
            .replace("pagca tapos", "pagkatapos")
            // "matapos" → after
            .replace("ma-tapos",  "matapos")
            .replace("ma tapos",  "matapos")
            .replace("mata pos",  "matapos")
            // "paghanda" → before/preparation
            .replace("pag-handa", "paghanda")
            .replace("pag handa", "paghanda")
            .replace("pa-handa",  "paghanda")

        // ── General Tagalog phonetics ─────────────────────────────────────────
        result = result
            .replace("pa-a-no",  "paano")
            .replace("pa a no",  "paano")
            .replace("paa-no",   "paano")
            .replace("pa ano",   "paano")
            .replace("ga-win",   "gawin")
            .replace("ga win",   "gawin")
            .replace("i-pa-ki-ta","ipakita")
            .replace("i pa ki ta","ipakita")
            .replace("i pakita", "ipakita")
            .replace("pu-mun-ta","pumunta")
            .replace("pu munta", "pumunta")
            .replace("bu-ksan",  "buksan")
            .replace("bu ksan",  "buksan")
            .replace("sa-bi-hin","sabihin")
            .replace("sa bi hin","sabihin")
            .replace("ma-ram-ing","maraming")
            .replace("i-ngat",   "ingat")
            .replace("ha-na-pin","hanapin")
            .replace("ha na pin","hanapin")
            .replace("mga",      "mga")          // keep as-is (already correct)

        // ── Scroll phonetics (Tagalog words for up/down) ──────────────────────
        result = result
            .replace("pa-ta-as", "pataas")
            .replace("pa taas",  "pataas")
            .replace("pa-ba-ba", "pababa")
            .replace("pa baba",  "pababa")
            .replace("u-ma-kyat","umakyat")
            .replace("u makyat", "umakyat")
            .replace("bu-ma-ba", "bumaba")
            .replace("bu maba",  "bumaba")

        return result
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private fun matchesAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }

    private fun stripIntentPrefix(text: String): String {
        val sortedPrefixes = VoiceCommand.INTENT_PREFIXES.sortedByDescending { it.length }
        var stripped = text
        for (prefix in sortedPrefixes) {
            if (stripped.startsWith(prefix)) {
                stripped = stripped.removePrefix(prefix).trim()
                break
            }
        }
        val fillers = listOf("an", "a", "the", "ang", "yung", "mga",
            "about", "during", "ng", "sa")
        var changed = true
        while (changed) {
            changed = false
            for (filler in fillers) {
                if (stripped.startsWith("$filler ")) {
                    stripped = stripped.removePrefix("$filler ").trim()
                    changed = true
                }
            }
        }
        return stripped
    }

    private fun getErrorMessage(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO                    -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT                   -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK                  -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT          -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH                 -> "No speech match found"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY          -> "Recognition service busy"
        SpeechRecognizer.ERROR_SERVER                   -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT           -> "No speech input"
        else                                            -> "Unknown error: $errorCode"
    }

    companion object {
        private const val TAG = "VoiceCommandManager"

        private val NAV_HOME_KEYWORDS = listOf(
            "go home", "home screen", "go to home", "tahanan",
            "pangunahin", "main screen", "main page"
        )
        private val NAV_DISASTERS_KEYWORDS = listOf(
            "go to disasters", "open disasters", "show disasters",
            "disaster list", "sakuna", "kalamidad", "list of disasters",
            "disaster module"
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
            "preferences", "configure", "mga setting"
        )
        private val NAV_CONTACTS_KEYWORDS = listOf(
            "go to contacts", "open contacts", "emergency contacts",
            "hotlines", "emergency numbers", "mga contact", "contact list"
        )

        private val BACK_KEYWORDS = listOf(
            "go back", "back", "return", "balik", "previous", "bumalik"
        )
        private val SCROLL_UP_KEYWORDS = listOf(
            "scroll up", "scroll to top", "taas", "pataas", "umakyat",
            "i-scroll pataas", "scroll pataas", "itaas", "up"
        )
        private val SCROLL_DOWN_KEYWORDS = listOf(
            "scroll down", "scroll to bottom", "baba", "pababa", "bumaba",
            "i-scroll pababa", "scroll pababa", "ibaba", "down"
        )

        private val READ_KEYWORDS = listOf(
            "read", "read aloud", "read this", "speak", "basahin",
            "sabihin", "read content", "read page", "i-read"
        )
        private val STOP_KEYWORDS = listOf(
            "stop reading", "stop", "tigil", "tumigil", "silence",
            "quiet", "huwag basahin", "itigil"
        )
        private val PAUSE_KEYWORDS = listOf(
            "pause", "pause reading", "sandali", "maghintay", "i-pause"
        )
        private val RESUME_KEYWORDS = listOf(
            "resume", "resume reading", "continue", "ituloy",
            "magpatuloy", "i-resume"
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
            "tulong", "help me", "call for help", "i need help",
            "emergency"
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

    fun resetToIdle() {
        _recognitionState.value = RecognitionState.Idle
    }
}
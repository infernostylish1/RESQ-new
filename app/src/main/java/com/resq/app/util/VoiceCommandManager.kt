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
    private var retryCount = 0

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    private var onCommandRecognized: ((VoiceCommand) -> Unit)? = null

    fun initialize(context: Context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _recognitionState.value = RecognitionState.Error("Speech recognition not available")
            return
        }
        speechRecognizer?.destroy()

        speechRecognizer = if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            Log.d(TAG, "Using ON-DEVICE speech recognizer")
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            Log.d(TAG, "Using default speech recognizer (may need network)")
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        speechRecognizer?.setRecognitionListener(createRecognitionListener())
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
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        Log.d(TAG, "No match/timeout — auto-retry #$retryCount")
                        isListening = false
                        onCommandRecognized?.let { callback ->
                            startListening(callback)
                        } ?: run {
                            isListening = false
                            _recognitionState.value = RecognitionState.Error(
                                "No speech detected. Tap to try again."
                            )
                        }
                    } else {
                        retryCount = 0
                        isListening = false
                        _recognitionState.value = RecognitionState.Error(
                            "No speech detected. Tap to try again."
                        )
                    }
                }
                else -> {
                    retryCount = 0
                    isListening = false
                    _recognitionState.value = RecognitionState.Error(getErrorMessage(error))
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            retryCount = 0
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val command = matches
                    .map { parseCommand(it) }
                    .firstOrNull { it !is VoiceCommand.Unknown }
                    ?: VoiceCommand.Unknown(matches.first())

                val bestText = matches.first()
                _recognitionState.value = RecognitionState.Result(bestText, command)
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(
                "android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES",
                arrayOf("fil-PH")
            )
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                3000L
            )
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
        retryCount = 0
        _recognitionState.value = RecognitionState.Idle
    }

    fun setLanguage(languageCode: String) { currentLanguage = languageCode }
    fun isCurrentlyListening(): Boolean = isListening

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        retryCount = 0
        _recognitionState.value = RecognitionState.Idle
    }

    fun promptOfflineLanguageDownload(context: Context) {
        try {
            val downloadIntent =
                Intent("com.google.android.googlequicksearchbox.VOICE_SEARCH_PARAMS")
            downloadIntent.putExtra("language", "en-US")
            context.startActivity(downloadIntent)
        } catch (e: Exception) {
            try {
                val settingsIntent =
                    Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
                context.startActivity(settingsIntent)
            } catch (_: Exception) {
                Log.w(TAG, "Cannot open language download settings")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Command parsing
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Parse steps (order matters):
     *
     *  1.  Normalize.
     *  2.  Apply phonetic Tagalog normalizer.
     *  3.  Hard navigation (require explicit nav verb).
     *  3.5 List commands (before generic app-control).
     *  4.  App-control / TTS / tip browsing / emergency.
     *  5.  Phase + disaster combo → OpenDisasterPhase.
     *  5.5 Tab + module combo → NavigateDisastersTab / NavigateTipsTab.  ← NEW
     *  6.  Tab-switch (user already on detail screen).
     *  7.  Tip trigger phrases.
     *  8.  Strip intent prefix → disaster keywords → OpenDisaster.
     *  9.  Strip intent prefix → first-aid keywords → OpenFirstAidCategory.
     * 10.  General search patterns.
     * 11.  Unknown fallback.
     */
    fun parseCommand(rawText: String): VoiceCommand {
        // Step 1
        val lowered = rawText.lowercase().trim()

        // Step 2
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

        // ── Step 3.5: List commands ───────────────────────────────────────────
        when {
            matchesAny(normalized, LIST_ALL_DISASTERS_KEYWORDS) -> return VoiceCommand.ListAllDisasters
            matchesAny(normalized, LIST_ALL_FIRSTAID_KEYWORDS)  -> return VoiceCommand.ListAllFirstAid
            matchesAny(normalized, LIST_ALL_TIPS_KEYWORDS)      -> return VoiceCommand.ListAllTips
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

        // ── Step 5: Phase + disaster combo ────────────────────────────────────
        val detectedPhase: String? = VoiceCommand.PHASE_KEYWORDS.entries
            .firstOrNull { (_, keywords) -> keywords.any { normalized.contains(it) } }
            ?.key

        if (detectedPhase != null) {
            for ((disasterKey, keywords) in VoiceCommand.DISASTER_KEYWORDS) {
                if (keywords.any { normalized.contains(it) }) {
                    return VoiceCommand.OpenDisasterPhase(disasterKey, detectedPhase)
                }
            }
            return VoiceCommand.SwitchTab(detectedPhase)
        }

        // ── Step 5.5: Tab + module combo → navigate to screen with tab (NEW) ─
        // Handles: "recommended disasters", "all tips", "show all disasters",
        //          "rekomendado na sakuna", etc.
        // Only fires for "recommended" and "all" — tabs that exist on list screens.
        run {
            val moduleTabLabels = listOf("recommended", "all")
            for (tab in moduleTabLabels) {
                val tabKeywords = VoiceCommand.TAB_KEYWORD_MAP[tab] ?: continue
                if (tabKeywords.any { normalized.contains(it) }) {
                    val mentionsDisasters = SCREEN_DISASTER_KEYWORDS.any { normalized.contains(it) }
                    val mentionsTips      = SCREEN_TIPS_KEYWORDS.any { normalized.contains(it) }
                    if (mentionsDisasters) return VoiceCommand.NavigateDisastersTab(tab)
                    if (mentionsTips)      return VoiceCommand.NavigateTipsTab(tab)
                    // No screen keyword found → fall through to Step 6 for generic SwitchTab
                }
            }
        }

        // ── Step 6: Generic tab-switch ────────────────────────────────────────
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

        // ── Steps 8–9: Strip prefix, match subject ───────────────────────────
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

    private fun normalizeTagalog(text: String): String {
        var result = text

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

        result = result
            .replace("bag go",   "bago")
            .replace("bag-go",   "bago")
            .replace("ba-go",    "bago")
            .replace("ha-bang",  "habang")
            .replace("ha bang",  "habang")
            .replace("hah bang", "habang")
            .replace("habit ang","habang")
            .replace("pag-ka-tapos","pagkatapos")
            .replace("pag ka tapos","pagkatapos")
            .replace("mag-ka-tapos","pagkatapos")
            .replace("pagca tapos", "pagkatapos")
            .replace("ma-tapos",  "matapos")
            .replace("ma tapos",  "matapos")
            .replace("mata pos",  "matapos")
            .replace("pag-handa", "paghanda")
            .replace("pag handa", "paghanda")
            .replace("pa-handa",  "paghanda")

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
            .replace("mga",      "mga")

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
        SpeechRecognizer.ERROR_NETWORK                  -> "Network error — enable offline speech pack in device settings"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT          -> "Network timeout — enable offline speech pack in device settings"
        SpeechRecognizer.ERROR_NO_MATCH                 -> "No speech detected. Tap to try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY          -> "Recognition service busy"
        SpeechRecognizer.ERROR_SERVER                   -> "Server error — enable offline speech pack in device settings"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT           -> "No speech input"
        else                                            -> "Unknown error: $errorCode"
    }

    companion object {
        private const val TAG = "VoiceCommandManager"
        private const val MAX_RETRIES = 2

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

        // ── List-command keywords ─────────────────────────────────────────────
        private val LIST_ALL_DISASTERS_KEYWORDS = listOf(
            "list all disasters", "show all disasters", "all disasters",
            "list disasters", "lahat ng sakuna", "lahat ng kalamidad",
            "ipakita lahat ng sakuna", "complete disaster list"
        )
        private val LIST_ALL_FIRSTAID_KEYWORDS = listOf(
            "list all first aid", "show all first aid", "all first aid",
            "list first aid", "lahat ng first aid", "lahat ng pertolya",
            "complete first aid list"
        )
        private val LIST_ALL_TIPS_KEYWORDS = listOf(
            "list all tips", "show all tips", "all tips",
            "list tips", "lahat ng tip", "lahat ng payo",
            "complete tip list"
        )

        // ── Screen-level keywords (generic, NOT specific disaster names) (NEW) ─
        private val SCREEN_DISASTER_KEYWORDS = listOf(
            "disaster", "disasters", "sakuna", "kalamidad", "mga sakuna"
        )
        private val SCREEN_TIPS_KEYWORDS = listOf(
            "tips", "mga tips", "payo", "mga payo"
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
package com.resq.app.util

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.resq.app.util.voice.SherpaAsrEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voice command manager powered entirely by offline sherpa-onnx.
 *
 * Recognition is delegated to [SherpaAsrEngine], which runs a single
 * multilingual Omnilingual ASR CTC model (English + Filipino) gated by
 * Silero VAD. No network access is ever required.
 *
 * The language switch is a no-op — the model is multilingual and auto-detects
 * the spoken language, so both English and Filipino commands are recognized
 * without configuration changes.
 */
@Singleton
class VoiceCommandManager @Inject constructor() {

    private var sherpaEngine: SherpaAsrEngine? = null
    private var isListening = false
    private var modelReady = false

    /** A start request that arrived before the model finished loading. */
    private var pendingStart: ((VoiceCommand) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timeoutJob: Job? = null

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    private var onCommandRecognized: ((VoiceCommand) -> Unit)? = null

    // ══════════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    /** Creates the Sherpa engine and starts loading its model in the background. */
    fun initialize(context: Context) {
        if (sherpaEngine == null) {
            val engine = SherpaAsrEngine(context.applicationContext)
            sherpaEngine = engine
            Log.d(TAG, "Sherpa ASR engine created")

            scope.launch {
                val result = engine.initialize()
                if (result.isSuccess) {
                    modelReady = true
                    Log.d(TAG, "Sherpa ASR engine ready")
                    pendingStart?.let { cb ->
                        pendingStart = null
                        startListening(cb)
                    }
                } else {
                    val error = result.exceptionOrNull() ?: Exception("Unknown init error")
                    Log.e(TAG, "Sherpa ASR engine failed to initialize", error)
                    _recognitionState.value =
                        RecognitionState.Error("Failed to load speech model: ${error.message}")
                }
            }
        }
    }

    /**
     * No-op. The Omnilingual ASR model is multilingual and auto-detects the
     * spoken language. Kept so callers (VoiceCommandViewModel) don't need to
     * change.
     */
    @Suppress("UNUSED_PARAMETER")
    fun setLanguage(languageCode: String) {
        // Intentionally empty — see class doc.
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Listening
    // ══════════════════════════════════════════════════════════════════════════

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening(onCommand: (VoiceCommand) -> Unit) {
        if (isListening) return
        onCommandRecognized = onCommand

        val engine = sherpaEngine ?: run {
            _recognitionState.value = RecognitionState.Error("Speech engine not initialized")
            return
        }

        if (!modelReady || !engine.isReady) {
            pendingStart = onCommand
            _recognitionState.value = RecognitionState.Starting
            return
        }

        isListening = true
        _recognitionState.value = RecognitionState.Starting
        scheduleTimeout()

        engine.startListening(
            onFinal = { text ->
                handleFinal(text)
            },
            onError = { e ->
                Log.e(TAG, "Sherpa ASR error", e)
                stopListeningInternal()
                _recognitionState.value =
                    RecognitionState.Error("Speech error: ${e.message ?: "unknown"}")
            },
        )
    }

    private fun handleFinal(text: String) {
        // Mark not-listening first so nothing re-enters the callback.
        isListening = false
        timeoutJob?.cancel()
        timeoutJob = null

        if (text.isBlank()) {
            _recognitionState.value =
                RecognitionState.Error("No speech detected. Tap to try again.")
            sherpaEngine?.stopListening()
            return
        }

        val command = parseCommand(text)
        _recognitionState.value = RecognitionState.Result(text, command)
        onCommandRecognized?.invoke(command)

        sherpaEngine?.stopListening()
    }

    /** Stop the microphone immediately. Safe to call from any state. */
    fun stopListening() {
        stopListeningInternal()
    }

    private fun stopListeningInternal() {
        timeoutJob?.cancel()
        timeoutJob = null
        sherpaEngine?.stopListening()
        isListening = false
        if (_recognitionState.value !is RecognitionState.Result) {
            _recognitionState.value = RecognitionState.Idle
        }
    }

    fun cancel() {
        stopListeningInternal()
        pendingStart = null
        _recognitionState.value = RecognitionState.Idle
    }

    fun isCurrentlyListening(): Boolean = isListening

    fun destroy() {
        stopListeningInternal()
        pendingStart = null
        sherpaEngine?.release()
        sherpaEngine = null
        modelReady = false
        _recognitionState.value = RecognitionState.Idle
    }

    fun resetToIdle() {
        _recognitionState.value = RecognitionState.Idle
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Silence timeout
    // ══════════════════════════════════════════════════════════════════════════

    private fun scheduleTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(MAX_LISTEN_MS)
            if (isListening) {
                Log.d(TAG, "Listening timeout after ${MAX_LISTEN_MS}ms")
                stopListeningInternal()
                _recognitionState.value =
                    RecognitionState.Error("No speech detected. Tap to try again.")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Command parsing  (UNCHANGED)
    // ══════════════════════════════════════════════════════════════════════════

    fun parseCommand(rawText: String): VoiceCommand {
        val lowered = rawText.lowercase().trim()
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

        // ── Step 5.5: Tab + module combo ──────────────────────────────────────
        run {
            val moduleTabLabels = listOf("recommended", "all")
            for (tab in moduleTabLabels) {
                val tabKeywords = VoiceCommand.TAB_KEYWORD_MAP[tab] ?: continue
                if (tabKeywords.any { normalized.contains(it) }) {
                    val mentionsDisasters = SCREEN_DISASTER_KEYWORDS.any { normalized.contains(it) }
                    val mentionsTips      = SCREEN_TIPS_KEYWORDS.any { normalized.contains(it) }
                    if (mentionsDisasters) return VoiceCommand.NavigateDisastersTab(tab)
                    if (mentionsTips)      return VoiceCommand.NavigateTipsTab(tab)
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
    //  Tagalog phonetic normalizer  (UNCHANGED)
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
    //  Helpers  (UNCHANGED)
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

    companion object {
        private const val TAG = "VoiceCommandManager"
        private const val MAX_LISTEN_MS = 8_000L

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
}
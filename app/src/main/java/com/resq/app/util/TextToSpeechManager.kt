package com.resq.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.resq.app.util.tts.PcmPlayer
import com.resq.app.util.tts.SherpaTtsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Text-to-Speech functionality for RESQ.
 *
 * English  → Android system TextToSpeech (existing behaviour).
 * Filipino → Sherpa-ONNX with the bundled MMS-TTS VITS model
 *            (assets/tts/model.onnx + assets/tts/tokens.txt), so no
 *            external TTS engine / data download is required.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    // ── System TTS (English) ────────────────────────────────────────────────
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

    // ── Sherpa-ONNX (Filipino) ──────────────────────────────────────────────
    private val sherpaScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sherpaEngine: SherpaTtsEngine? = null
    private var pcmPlayer: PcmPlayer? = null
    private var sherpaReady = false
    private var sherpaInitializing = false
    private var sherpaJob: Job? = null

    /**
     * Initialize both engines. System TTS is initialized synchronously via
     * its callback; Sherpa-ONNX loads in the background (2–5 s on first run).
     */
    fun initialize(context: Context, onReady: (() -> Unit)? = null) {
        if (isInitialized && textToSpeech != null) {
            onReady?.invoke()
        } else {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    _isReady.value = true
                    setupTTS()
                    onReady?.invoke()
                    Log.d(TAG, "System TTS initialized successfully")
                } else {
                    Log.e(TAG, "System TTS initialization failed with status: $status")
                    _ttsState.value = TTSState.Error("TTS initialization failed")
                }
            }
        }

        initSherpaIfNeeded()
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

    // ── Sherpa init ─────────────────────────────────────────────────────────

    private fun initSherpaIfNeeded() {
        if (sherpaReady || sherpaInitializing) return
        sherpaInitializing = true

        sherpaScope.launch {
            try {
                val engine = SherpaTtsEngine(appContext)
                engine.initialize()
                withContext(Dispatchers.Main) {
                    sherpaEngine = engine
                    pcmPlayer   = PcmPlayer()
                    sherpaReady = true
                    sherpaInitializing = false
                    Log.d(
                        TAG,
                        "Sherpa-ONNX Filipino engine ready (sampleRate=${engine.sampleRate})"
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Sherpa-ONNX init failed — Filipino will fall back to system TTS", t)
                sherpaInitializing = false
            }
        }
    }

    // ── Public speak ────────────────────────────────────────────────────────

    /**
     * Speak the given text.
     *
     * Routes Filipino ("tl" / "fil" / "tagalog") to Sherpa-ONNX when ready,
     * otherwise falls back to the Android system TextToSpeech engine.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        val wantFilipino = currentLanguage.lowercase() in FILIPINO_CODES

        if (wantFilipino && sherpaReady) {
            speakFilipino(text)
            return
        }

        // ── System TTS path (English, or Filipino fallback) ─────────────────
        if (!isInitialized || textToSpeech == null) {
            pendingText = text
            Log.w(TAG, "TTS not initialized, text queued")
            return
        }

        val processedText = preProcessText(text)
        val params = android.os.Bundle()
        params.putInt(TextToSpeech.Engine.KEY_FEATURE_NETWORK_TIMEOUT_MS, 5000)

        val currentUtteranceId = "tts_${++utteranceId}"
        textToSpeech?.speak(processedText, queueMode, params, currentUtteranceId)
        _ttsState.value = TTSState.Speaking
    }

    private fun speakFilipino(text: String) {
        val engine = sherpaEngine ?: return
        val player = pcmPlayer ?: return

        // Cancel any previous synthesis and stop any audio still playing.
        sherpaJob?.cancel()
        player.stop()
        textToSpeech?.stop()
        _ttsState.value = TTSState.Speaking

        val speed = currentSpeed
        sherpaJob = sherpaScope.launch {
            try {
                val audio = engine.generate(preProcessFilipino(text), speed = speed)
                if (audio == null || audio.samples.isEmpty()) {
                    _ttsState.value = TTSState.Idle
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    player.play(audio.samples, audio.sampleRate)
                }

                val durationMs = audio.samples.size * 1000L / audio.sampleRate
                delay(durationMs + 150L)
                _ttsState.value = TTSState.Idle
            } catch (t: Throwable) {
                Log.e(TAG, "Filipino synthesis failed", t)
                _ttsState.value = TTSState.Error("Filipino TTS failed: ${t.message}")
            }
        }
    }

    /**
     * Filipino-safe preprocessing.
     * Do NOT reuse the English one — "911" → "nine one one" would be wrong
     * for a Filipino listener.
     */
    private fun preProcessFilipino(text: String): String = text
        .replace("CPR", "C P R")
        .replace("911", "siyam isa isa")
        .replace("117", "isa isa pito")
        .replace("NDRRMC", "N D R R M C")
        .replace("PAGASA", "PAG-ASA")

    /**
     * English preprocessing — unchanged from before.
     */
    private fun preProcessText(text: String): String {
        return text
            .replace("CPR", "C P R")
            .replace("RESQ", "Rescue")
            .replace("911", "nine one one")
            .replace("117", "one one seven")
            .replace("PH", "Philippines")
            .replace("PAGASA", "PAG-ASA")
            .replace("NDRRMC", "N D R R M C")
    }

    // ── Controls ────────────────────────────────────────────────────────────

    fun stop() {
        textToSpeech?.stop()
        sherpaJob?.cancel()
        pcmPlayer?.stop()
        _ttsState.value = TTSState.Idle
    }

    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking == true || pcmPlayer?.isPlaying() == true
    }

    fun setSpeechRate(rate: Float) {
        currentSpeed = rate.coerceIn(0.5f, 2.0f)
        textToSpeech?.setSpeechRate(currentSpeed)
    }

    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        textToSpeech?.setPitch(currentPitch)
        // VITS has no pitch parameter — this only affects the system TTS path.
    }

    fun setLanguage(languageCode: String): Boolean {
        currentLanguage = languageCode

        val locale = when (languageCode.lowercase()) {
            "tl", "fil", "tagalog" -> Locale("fil", "PH")
            else -> Locale.ENGLISH
        }

        val result = textToSpeech?.setLanguage(locale)

        return when (result) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                Log.w(TAG, "Language $languageCode not supported by system TTS, falling back to English")
                textToSpeech?.setLanguage(Locale.ENGLISH)
                false
            }
            else -> true
        }
    }

    fun updateSettings(speed: Float, pitch: Float) {
        setSpeechRate(speed)
        setPitch(pitch)
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false

        sherpaJob?.cancel()
        pcmPlayer?.stop()
        sherpaEngine?.release()
        sherpaEngine = null
        pcmPlayer = null
        sherpaReady = false

        _isReady.value = false
        _ttsState.value = TTSState.Idle
    }

    companion object {
        private const val TAG = "TextToSpeechManager"
        private val FILIPINO_CODES = setOf("tl", "fil", "tagalog")
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
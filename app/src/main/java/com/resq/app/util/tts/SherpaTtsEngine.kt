package com.resq.app.util.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

/**
 * Offline Filipino TTS via Sherpa-ONNX, using the MMS-TTS VITS model.
 * Model assets: assets/tts/model.onnx and assets/tts/tokens.txt
 * Output: 16 kHz mono FloatArray (normalized to [-1, 1]).
 *
 * Thread-safety: [generate] is safe to call from a background thread.
 * Do NOT call [generate] on the main thread — inference takes 1-3 seconds.
 */
class SherpaTtsEngine(private val appContext: Context) {

    companion object {
        private const val TAG = "SherpaTtsEngine"
        private const val MODEL_ASSET  = "tts/model.onnx"
        private const val TOKENS_ASSET = "tts/tokens.txt"
    }

    @Volatile private var tts: OfflineTts? = null
    @Volatile private var ready = false

    val isReady: Boolean get() = ready

    /**
     * Loads the model. Safe to call multiple times — subsequent calls are no-ops.
     * Call this off the main thread. Expect 2-5 seconds on first run.
     */
    fun initialize() {
        if (ready) return
        synchronized(this) {
            if (ready) return

            try {
                val vitsConfig = OfflineTtsVitsModelConfig(
                    model   = MODEL_ASSET,
                    tokens  = TOKENS_ASSET,
                    lexicon = "",      // character-based MMS — no lexicon needed
                    dataDir = "",      // no espeak-ng-data needed
                )

                val modelConfig = OfflineTtsModelConfig(
                    vits       = vitsConfig,
                    numThreads = 2,
                    debug      = false,
                    provider   = "cpu",
                )

                val config = OfflineTtsConfig(
                    model = modelConfig,
                )

                // newFromAsset reads directly from assets — no 114 MB extraction.
                tts = OfflineTts(assetManager = appContext.assets, config = config)
                ready = true
                Log.d(TAG, "Initialized. sampleRate=${tts?.sampleRate()}")
            } catch (t: Throwable) {
                Log.e(TAG, "Init failed", t)
                tts = null
                ready = false
                throw t
            }
        }
    }

    /**
     * Synthesizes [text] to mono FloatArray in [-1, 1].
     * @param speed 1.0 = normal, >1 = faster, <1 = slower.
     * Returns null if the engine is not ready or synthesis fails.
     */
    fun generate(text: String, speed: Float = 1.0f): GeneratedAudio? {
        val engine = tts ?: return null
        if (text.isBlank()) return null
        return try {
            engine.generate(text, sid = 0, speed = speed)
        } catch (t: Throwable) {
            Log.e(TAG, "generate() failed for: \"$text\"", t)
            null
        }
    }

    /** Sample rate of generated audio (typically 16000 for MMS-TTS). */
    val sampleRate: Int get() = tts?.sampleRate() ?: 16000

    fun release() {
        synchronized(this) {
            try { tts?.release() } catch (_: Throwable) {}
            tts = null
            ready = false
        }
    }
}
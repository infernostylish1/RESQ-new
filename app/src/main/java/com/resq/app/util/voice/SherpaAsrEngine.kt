package com.resq.app.util.voice

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineOmnilingualAsrCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline speech recognition engine built on sherpa-onnx.
 *
 * Pipeline:
 *   1. Microphone is captured via [AudioRecord] at 16 kHz mono PCM16.
 *   2. Frames are fed to a Silero VAD model which segments the stream into
 *      contiguous speech regions (ignoring silence and background noise).
 *   3. Each speech segment is transcribed by the Omnilingual ASR CTC model
 *      which covers both English (eng_Latn) and Filipino (tgl_Latn) without
 *      language switching.
 *
 * Bundled assets (app/src/main/assets/):
 *   • asr/model.int8.onnx   — Omnilingual ASR CTC int8 (~348 MB)
 *   • asr/tokens.txt        — token table for the above model
 *   • silero_vad.int8.onnx  — Silero VAD int8 (~1-2 MB)
 *
 * All assets are copied to filesDir on first use. Subsequent launches reuse
 * the extracted copies — the ASR model file is large, so this extraction is
 * one-time only.
 */
class SherpaAsrEngine(private val appContext: Context) {

    companion object {
        private const val TAG = "SherpaAsrEngine"

        private const val ASR_MODEL_ASSET = "asr/model.int8.onnx"
        private const val ASR_TOKENS_ASSET = "asr/tokens.txt"
        private const val VAD_MODEL_ASSET = "silero_vad.int8.onnx"

        private const val SAMPLE_RATE = 16000
        private const val WINDOW_SIZE = 512
    }

    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val isRecording = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var isReady: Boolean = false
        private set

    /**
     * Unpacks the model files (first call) and constructs the VAD + recognizer.
     * Call off the main thread — first-run extraction of ~350 MB takes several
     * seconds.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val asrModel = copyAssetToFile(ASR_MODEL_ASSET)
            val tokens = copyAssetToFile(ASR_TOKENS_ASSET)
            val vadModel = copyAssetToFile(VAD_MODEL_ASSET)

            // ── Recognizer ───────────────────────────────────────────────────
            val omnilingual = OfflineOmnilingualAsrCtcModelConfig(
                model = asrModel.absolutePath,
            )
            val modelConfig = OfflineModelConfig(
                omnilingual = omnilingual,
                tokens = tokens.absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
            )
            val recConfig = OfflineRecognizerConfig(
                modelConfig = modelConfig,
                decodingMethod = "greedy_search",
            )
            recognizer = OfflineRecognizer(config = recConfig)

            // ── VAD ──────────────────────────────────────────────────────────
            val sileroConfig = SileroVadModelConfig(
                model = vadModel.absolutePath,
                threshold = 0.5f,
                minSilenceDuration = 0.5f,
                minSpeechDuration = 0.25f,
                windowSize = WINDOW_SIZE,
                maxSpeechDuration = 8.0f,
            )
            val vadConfig = VadModelConfig(
                sileroVadModelConfig = sileroConfig,
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
                debug = false,
            )
            vad = Vad(config = vadConfig)

            isReady = true
            Log.d(TAG, "Sherpa ASR + VAD initialized")
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "Initialization failed", t)
            release()
            Result.failure(t)
        }
    }

    /**
     * Start capturing audio. [onFinal] fires once with the transcription of
     * the first complete speech segment the VAD detects. The engine stops
     * itself automatically after emitting a result.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening(
        onFinal: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        if (isRecording.get()) return
        if (!isReady) {
            onError(IllegalStateException("ASR engine not ready — call initialize() first"))
            return
        }

        isRecording.set(true)

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSize = maxOf(minBuf, WINDOW_SIZE * 2 * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
            )
            audioRecord?.startRecording()
        } catch (t: Throwable) {
            isRecording.set(false)
            onError(t)
            return
        }

        recordingJob = scope.launch {
            val buffer = ShortArray(WINDOW_SIZE)

            while (isActive && isRecording.get()) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read <= 0) continue

                val samples = FloatArray(read) { buffer[it] / 32768.0f }
                vad?.acceptWaveform(samples)

                val v = vad ?: continue
                while (!v.empty()) {
                    val segment = v.front()
                    val text = runRecognition(segment.samples)
                    v.pop()

                    if (text.isNotBlank()) {
                        withContext(Dispatchers.Main) { onFinal(text) }
                        isRecording.set(false)
                        stopMicrophone()
                        return@launch
                    }
                }
            }
        }
    }

    private fun runRecognition(samples: FloatArray): String {
        val r = recognizer ?: return ""
        if (samples.isEmpty()) return ""
        val stream = r.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            r.decode(stream)
            r.getResult(stream).text
        } catch (t: Throwable) {
            Log.e(TAG, "Recognition failed", t)
            ""
        } finally {
            try { stream.release() } catch (_: Throwable) {}
        }
    }

    /** Stop the current capture session. Safe to call when idle. */
    fun stopListening() {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null
        stopMicrophone()
        try { vad?.flush() } catch (_: Throwable) {}
        try { vad?.clear() } catch (_: Throwable) {}
    }

    private fun stopMicrophone() {
        audioRecord?.let {
            try { it.stop() } catch (_: Throwable) {}
            try { it.release() } catch (_: Throwable) {}
        }
        audioRecord = null
    }

    /** Release every native resource. The engine cannot be used after this. */
    fun release() {
        stopListening()
        try { vad?.release() } catch (_: Throwable) {}
        vad = null
        try { recognizer?.release() } catch (_: Throwable) {}
        recognizer = null
        scope.cancel()
        isReady = false
        Log.d(TAG, "Sherpa ASR engine released")
    }

    /**
     * Copy an asset into filesDir. If the destination already exists and the
     * source is uncompressed (so we can read its length via openFd), skip the
     * copy when sizes match. Compressed assets are always re-copied once.
     */
    private fun copyAssetToFile(assetPath: String): File {
        val assets = appContext.assets
        val target = File(appContext.filesDir, File(assetPath).name)

        val assetSize: Long = try {
            assets.openFd(assetPath).use { it.length }
        } catch (_: Throwable) {
            -1L
        }

        if (target.exists() && assetSize >= 0 && target.length() == assetSize) {
            return target
        }

        assets.open(assetPath).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output, bufferSize = 1 shl 16)
            }
        }
        return target
    }
}
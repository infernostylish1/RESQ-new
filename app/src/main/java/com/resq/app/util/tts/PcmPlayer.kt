package com.resq.app.util.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Minimal mono FloatArray → AudioTrack player.
 * One instance per active utterance. Call [play] then [stop] when done.
 */
class PcmPlayer {

    companion object { private const val TAG = "PcmPlayer" }

    @Volatile private var track: AudioTrack? = null

    /**
     * Converts [samples] (Float in [-1, 1]) to PCM16 and plays asynchronously.
     * Returns immediately; audio plays on the AudioTrack's own thread.
     */
    fun play(samples: FloatArray, sampleRate: Int) {
        stop()
        if (samples.isEmpty()) return

        val pcm16 = FloatArrayToPcm16(samples)
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = maxOf(pcm16.size * 2, minBuf)

        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        t.play()
        // Blocking write — AudioTrack plays the buffer asynchronously.
        t.write(pcm16, 0, pcm16.size)
        track = t
    }

    fun stop() {
        track?.let {
            try { it.pause()   } catch (_: Throwable) {}
            try { it.flush()   } catch (_: Throwable) {}
            try { it.stop()    } catch (_: Throwable) {}
            try { it.release() } catch (_: Throwable) {}
        }
        track = null
    }

    fun isPlaying(): Boolean = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    private fun FloatArrayToPcm16(src: FloatArray): ShortArray {
        val out = ShortArray(src.size)
        for (i in src.indices) {
            out[i] = (src[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }
}
package com.example.aura.data.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Thread-safe audio player for raw PCM 16-bit data from the Gemini Live API.
 *
 * Model output: 24kHz, Mono, PCM 16-bit Little-Endian
 *
 * Key design choices:
 * - Uses @Synchronized on all public methods to prevent race conditions
 *   between concurrent audio chunk writes and stop() calls.
 * - Uses AudioAttributes.USAGE_MEDIA for proper audio routing.
 * - Large buffer (8× minimum) to prevent underruns that cause static/crackling.
 * - start() is idempotent — safe to call on every chunk.
 */
class LiveAudioPlayer {

    companion object {
        private const val TAG = "LiveAudioPlayer"
    }

    private var audioTrack: AudioTrack? = null

    private val sampleRate = 24000
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    // Use a large buffer to prevent underruns causing static
    private val bufferSize = minBuf * 8

    @Synchronized
    fun start() {
        if (audioTrack != null) return

        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.play()
                Log.d(TAG, "AudioTrack started (24kHz, mono, 16-bit, buf=${bufferSize})")
            } else {
                Log.e(TAG, "AudioTrack failed to initialize")
                audioTrack?.release()
                audioTrack = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack: ${e.message}", e)
            audioTrack = null
        }
    }

    /**
     * Writes a chunk of raw PCM 16-bit data to the AudioTrack.
     * This is a blocking call — must NOT be called from the main thread.
     */
    @Synchronized
    fun playAudioChunk(pcmData: ByteArray) {
        val track = audioTrack ?: return
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return

        try {
            track.write(pcmData, 0, pcmData.size)
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack write failed: ${e.message}")
        }
    }

    @Synchronized
    fun stop() {
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                }
                track.release()
                Log.d(TAG, "AudioTrack stopped and released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack stop error: ${e.message}")
        } finally {
            audioTrack = null
        }
    }
}

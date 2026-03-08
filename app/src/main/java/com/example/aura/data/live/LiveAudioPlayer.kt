package com.example.aura.data.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Plays raw audio data received from the Gemini Live API.
 * 
 * Model output: 24kHz, Mono, PCM 16-bit
 */
class LiveAudioPlayer {

    private var audioTrack: AudioTrack? = null
    
    private val sampleRate = 24000
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4

    @Suppress("DEPRECATION")
    fun start() {
        if (audioTrack != null) return

        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Plays a chunk of raw PCM 16-bit data.
     */
    fun playAudioChunk(pcmData: ByteArray) {
        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            audioTrack?.write(pcmData, 0, pcmData.size)
        }
    }

    fun stop() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        } finally {
            audioTrack = null
        }
    }
}

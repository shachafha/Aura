package com.example.aura.data.live

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Captures raw microphone data for the Gemini Live API.
 *
 * Optimized for low latency:
 * - 16kHz, Mono, PCM 16-bit (Gemini requirement)
 * - Smallest practical buffer (2× minimum)
 * - Silence detection with configurable timeout
 */
class LiveAudioRecorder {

    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // Use 2× minimum buffer for lower latency (was 4×)
    private val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val bufferSize = minBuf * 2

    // Silence detection
    private val silenceThreshold = 800
    private val silenceTimeoutMs = 5000L
    private var lastSoundTimestamp = 0L

    var onSilenceDetected: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    suspend fun startRecording(onAudioData: (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        if (isRecording) return@withContext

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Optimized for voice
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext
            }

            audioRecord?.startRecording()
            isRecording = true
            lastSoundTimestamp = System.currentTimeMillis()

            // Use a small read buffer for frequent, small chunks → lower latency
            val readSize = minBuf  // ~100ms of audio per read
            val buffer = ByteArray(readSize)

            while (isActive && isRecording) {
                val readResult = audioRecord?.read(buffer, 0, readSize) ?: 0
                if (readResult > 0) {
                    val audioChunk = buffer.copyOfRange(0, readResult)
                    onAudioData(audioChunk)

                    // Silence detection
                    val rms = computeRms(audioChunk)
                    if (rms > silenceThreshold) {
                        lastSoundTimestamp = System.currentTimeMillis()
                    } else {
                        val silenceDuration = System.currentTimeMillis() - lastSoundTimestamp
                        if (silenceDuration >= silenceTimeoutMs) {
                            withContext(Dispatchers.Main) {
                                onSilenceDetected?.invoke()
                            }
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            stopRecording()
        }
    }

    fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) { }
        finally { audioRecord = null }
    }

    private fun computeRms(pcmData: ByteArray): Double {
        if (pcmData.size < 2) return 0.0
        var sumOfSquares = 0.0
        val sampleCount = pcmData.size / 2
        for (i in 0 until sampleCount) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sumOfSquares += (sample * sample).toDouble()
        }
        return sqrt(sumOfSquares / sampleCount)
    }
}

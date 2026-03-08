package com.example.aura.data.live

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Captures raw microphone data for the Gemini Live API.
 * 
 * Model requirement: 16kHz, Mono, PCM 16-bit
 * 
 * Features:
 * - Streams raw PCM bytes to the callback
 * - Detects silence (5 seconds) and fires onSilenceDetected
 */
class LiveAudioRecorder {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4

    // Silence detection
    private val silenceThreshold = 800  // RMS below this = silence
    private val silenceTimeoutMs = 5000L // 5 seconds of silence = auto-stop
    private var lastSoundTimestamp = 0L

    /** Called when 5 seconds of continuous silence is detected */
    var onSilenceDetected: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    suspend fun startRecording(onAudioData: (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        if (isRecording) return@withContext

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
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

            val buffer = ByteArray(bufferSize)

            while (isActive && isRecording) {
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readResult > 0) {
                    val audioChunk = buffer.copyOfRange(0, readResult)
                    onAudioData(audioChunk)

                    // Check audio energy for silence detection
                    val rms = computeRms(audioChunk)
                    if (rms > silenceThreshold) {
                        lastSoundTimestamp = System.currentTimeMillis()
                    } else {
                        val silenceDuration = System.currentTimeMillis() - lastSoundTimestamp
                        if (silenceDuration >= silenceTimeoutMs) {
                            // 5 seconds of silence detected → auto-stop
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
        } catch (e: Exception) {
            // Ignore
        } finally {
            audioRecord = null
        }
    }

    /**
     * Computes RMS (Root Mean Square) energy of a PCM 16-bit audio buffer.
     * Higher values = louder audio. Values near 0 = silence.
     */
    private fun computeRms(pcmData: ByteArray): Double {
        if (pcmData.size < 2) return 0.0

        var sumOfSquares = 0.0
        val sampleCount = pcmData.size / 2 // 16-bit = 2 bytes per sample

        for (i in 0 until sampleCount) {
            // Little-endian PCM 16-bit
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sumOfSquares += (sample * sample).toDouble()
        }

        return sqrt(sumOfSquares / sampleCount)
    }
}

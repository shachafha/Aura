package com.example.aura.data.live

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Captures raw microphone data for the Gemini Live API.
 * 
 * Model requirement: 16kHz, Mono, PCM 16-bit
 */
class LiveAudioRecorder {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4

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

            val buffer = ByteArray(bufferSize)

            while (isActive && isRecording) {
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readResult > 0) {
                    // Pass the raw byte array to the callback (to be base64-encoded and sent)
                    onAudioData(buffer.copyOfRange(0, readResult))
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
}

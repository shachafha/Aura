package com.example.aura.data.live

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Always-on wake word detector using Android SpeechRecognizer.
 * 
 * Listens passively for "Hey Aura" (or "Aura") and invokes onWakeWordDetected.
 * Auto-restarts after each recognition cycle to keep listening perpetually.
 */
class WakeWordDetector(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningForWakeWord = false

    private val _isPassiveListening = MutableStateFlow(false)
    val isPassiveListening: StateFlow<Boolean> = _isPassiveListening.asStateFlow()

    /** Called when the wake phrase is detected */
    var onWakeWordDetected: (() -> Unit)? = null

    private val wakeWords = listOf("hey aura", "aura", "hey ora", "a aura", "hey aurora")

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    /**
     * Start passively listening for the wake word.
     */
    fun startListening() {
        if (isListeningForWakeWord) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        isListeningForWakeWord = true
        _isPassiveListening.value = true
        createAndStartRecognizer()
    }

    private fun createAndStartRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                // Auto-restart on error (timeout, no match, etc.)
                if (isListeningForWakeWord) {
                    try {
                        createAndStartRecognizer()
                    } catch (_: Exception) { }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (checkForWakeWord(matches)) {
                    // Wake word detected!
                    isListeningForWakeWord = false
                    _isPassiveListening.value = false
                    onWakeWordDetected?.invoke()
                } else {
                    // Not the wake word → keep listening
                    if (isListeningForWakeWord) {
                        try {
                            createAndStartRecognizer()
                        } catch (_: Exception) { }
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (checkForWakeWord(matches)) {
                    isListeningForWakeWord = false
                    _isPassiveListening.value = false
                    onWakeWordDetected?.invoke()
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkForWakeWord(matches: List<String>?): Boolean {
        if (matches.isNullOrEmpty()) return false
        return matches.any { text ->
            val lower = text.lowercase().trim()
            wakeWords.any { wake -> lower.contains(wake) }
        }
    }

    fun stopListening() {
        isListeningForWakeWord = false
        _isPassiveListening.value = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) { }
        speechRecognizer = null
    }

    fun shutdown() {
        stopListening()
    }
}

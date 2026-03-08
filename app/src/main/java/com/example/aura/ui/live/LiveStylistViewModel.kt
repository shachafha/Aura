package com.example.aura.ui.live

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.aura.data.model.ChatMessage
import com.example.aura.data.model.MessageRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Robust demo-mode ViewModel — fully on-device, zero API calls.
 *
 * KEY DESIGN:
 *  • SpeechRecognizer MUST be created/started/destroyed on the MAIN thread.
 *  • All recognizer work goes through [mainHandler].
 *  • A single [pendingRestart] Runnable is tracked: scheduling a new restart
 *    always cancels the previous one → no duplicate recognizers.
 *  • [recognizerBusy] guards against overlapping starts; only set in
 *    [startRecognizerOnMain] and cleared in [onReadyForSpeech] or on failure.
 *
 * State machine:
 *   PASSIVE_LISTENING ──(wake word)──▶ greeting TTS ──▶ ACTIVE_LISTENING
 *   ACTIVE_LISTENING  ──(speech)────▶ response TTS   ──▶ ACTIVE_LISTENING
 *   ACTIVE_LISTENING  ──("goodbye")──▶ farewell TTS  ──▶ PASSIVE_LISTENING
 */
class LiveStylistViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "AuraDemo"
        private const val DEBOUNCE_MS = 400L       // between recognizer cycles
        private const val POST_TTS_DELAY_MS = 700L  // after TTS finishes → start listening
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // ─── UI State ──────────────────────────────────────────

    enum class AuraState { PASSIVE_LISTENING, ACTIVE_LISTENING, PROCESSING, SPEAKING }

    private val _auraState = MutableStateFlow(AuraState.PASSIVE_LISTENING)
    val auraState: StateFlow<AuraState> = _auraState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _userTranscription = MutableStateFlow("")
    val userTranscription: StateFlow<String> = _userTranscription.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /** Incremented when user says "take picture" — Screen observes this to fire ImageCapture */
    private val _captureTrigger = MutableStateFlow(0)
    val captureTrigger: StateFlow<Int> = _captureTrigger.asStateFlow()

    // ─── Internal State ────────────────────────────────────

    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile private var recognizerBusy = false
    @Volatile private var isShutdown = false
    @Volatile private var sessionActive = false

    /**
     * Tracks the single pending restart Runnable. Any new schedule removes the
     * previous pending restart, preventing duplicate recognizer starts.
     */
    private var pendingRestart: Runnable? = null

    private fun makeRecognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    // ─── Text-to-Speech ────────────────────────────────────

    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.05f)
                tts?.setPitch(1.1f)
                ttsReady = true
                Log.d(TAG, "✅ TTS ready")

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _auraState.value = AuraState.SPEAKING
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS done → will restart listener (session=$sessionActive)")
                        _isSpeaking.value = false
                        afterTtsFinished()
                    }

                    @Deprecated("Deprecated")
                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "TTS error → will restart listener")
                        _isSpeaking.value = false
                        afterTtsFinished()
                    }
                })
            } else {
                Log.e(TAG, "TTS init failed with status=$status")
            }
        }

        // Boot into passive listening after init delay
        mainHandler.postDelayed({ requestPassiveListening() }, 1200L)
    }

    /**
     * Called on a TTS thread after speech finishes. Posts restart to main handler.
     */
    private fun afterTtsFinished() {
        scheduleRestart(POST_TTS_DELAY_MS) {
            if (isShutdown) return@scheduleRestart
            if (sessionActive) {
                Log.d(TAG, "After TTS → active listening")
                requestActiveListening()
            } else {
                Log.d(TAG, "After TTS → passive listening")
                requestPassiveListening()
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SCHEDULE / CANCEL — single pending restart
    // ══════════════════════════════════════════════════════════

    /**
     * Cancels any previously scheduled restart and posts a new one.
     * Thread-safe: always posts to [mainHandler].
     */
    private fun scheduleRestart(delayMs: Long, action: () -> Unit) {
        // Remove any previously pending restart
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { action() }
        pendingRestart = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    /**
     * Cancel all pending restarts. Call this before any immediate recognizer
     * operation to prevent stale callbacks from interfering.
     */
    private fun cancelPendingRestart() {
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        pendingRestart = null
    }

    // ══════════════════════════════════════════════════════════
    //  RECOGNIZER LIFECYCLE — all on main thread
    // ══════════════════════════════════════════════════════════

    /**
     * Destroys the current recognizer. Does NOT touch [recognizerBusy].
     */
    private fun killRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "killRecognizer: ${e.message}")
        }
        speechRecognizer = null
    }

    // ── Request Passive (wake word) listening ──────────────

    private fun requestPassiveListening() {
        if (isShutdown) return

        cancelPendingRestart()  // Cancel any stale schedule
        sessionActive = false
        _auraState.value = AuraState.PASSIVE_LISTENING
        _isListening.value = false
        _userTranscription.value = ""

        scheduleRestart(DEBOUNCE_MS) {
            if (!isShutdown && !_isSpeaking.value) {
                startRecognizerOnMain(passive = true)
            }
        }
    }

    // ── Request Active (conversation) listening ───────────

    private fun requestActiveListening() {
        if (isShutdown) return

        cancelPendingRestart()
        _auraState.value = AuraState.ACTIVE_LISTENING
        _isListening.value = true
        _userTranscription.value = ""

        scheduleRestart(DEBOUNCE_MS) {
            if (!isShutdown && !_isSpeaking.value) {
                startRecognizerOnMain(passive = false)
            }
        }
    }

    // ── Start Recognizer (MUST run on main thread) ────────

    private fun startRecognizerOnMain(passive: Boolean) {
        if (isShutdown) return
        if (recognizerBusy) {
            Log.w(TAG, "startRecognizer skipped: busy")
            // Retry after delay
            scheduleRestart(DEBOUNCE_MS * 2) {
                startRecognizerOnMain(passive)
            }
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition unavailable on this device")
            return
        }

        // Set busy BEFORE any async work
        recognizerBusy = true

        // Kill old recognizer
        killRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer!!.setRecognitionListener(Listener(passive))
            speechRecognizer!!.startListening(makeRecognizerIntent())

            if (!passive) {
                _isListening.value = true
                _auraState.value = AuraState.ACTIVE_LISTENING
            }
            Log.d(TAG, "🎙 Recognizer started (passive=$passive)")
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            recognizerBusy = false
            killRecognizer()
            // Retry
            scheduleRestart(DEBOUNCE_MS * 3) {
                if (passive) requestPassiveListening() else requestActiveListening()
            }
        }
    }

    // ── RecognitionListener ────────────────────────────────

    private inner class Listener(private val passive: Boolean) : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            // Recognizer is alive and listening — clear busy flag
            recognizerBusy = false
            Log.d(TAG, "  onReady (passive=$passive)")
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            Log.d(TAG, "  onEndOfSpeech (passive=$passive)")
        }

        override fun onError(error: Int) {
            recognizerBusy = false
            val name = errorName(error)
            Log.d(TAG, "  onError: $name (passive=$passive, session=$sessionActive)")

            if (isShutdown || _isSpeaking.value) return

            // Restart after a debounce delay
            scheduleRestart(DEBOUNCE_MS) {
                if (isShutdown || _isSpeaking.value) return@scheduleRestart
                if (passive) {
                    // Always restart passive listening on error (timeout/no-match is normal)
                    requestPassiveListening()
                } else if (sessionActive) {
                    // Keep active listening alive in continuous mode
                    requestActiveListening()
                } else {
                    // Fall back to passive
                    _isListening.value = false
                    requestPassiveListening()
                }
            }
        }

        override fun onPartialResults(partial: Bundle?) {
            val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return

            if (passive) {
                if (isWakeWord(text)) {
                    Log.d(TAG, "🎯 Wake word in partial: '$text'")
                    mainHandler.post { onWakeWordDetected() }
                }
            } else {
                _userTranscription.value = text
            }
        }

        override fun onResults(results: Bundle?) {
            recognizerBusy = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""
            Log.d(TAG, "  onResults: '$text' (passive=$passive)")

            if (passive) {
                if (isWakeWord(text)) {
                    mainHandler.post { onWakeWordDetected() }
                } else if (!isShutdown && !_isSpeaking.value) {
                    requestPassiveListening()
                }
            } else {
                _isListening.value = false
                _userTranscription.value = text
                if (text.isNotBlank()) {
                    processUserInput(text)
                } else if (sessionActive) {
                    requestActiveListening()
                } else {
                    requestPassiveListening()
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}

        private fun errorName(e: Int) = when (e) {
            SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NET_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
            SpeechRecognizer.ERROR_SERVER -> "SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
            else -> "UNKNOWN($e)"
        }
    }

    // ─── Wake Word ─────────────────────────────────────────

    private val wakePatterns = listOf(
        "hey aura", "aura", "hey ora", "aurora", "hey aurora",
        "a aura", "hey oura", "hey ora", "hey or a"
    )

    private fun isWakeWord(text: String): Boolean {
        val lower = text.lowercase().trim()
        return wakePatterns.any { lower.contains(it) }
    }

    /**
     * Wake word detected!
     * 1. Cancel pending restarts (no duplicates)
     * 2. Kill the current (passive) recognizer
     * 3. Mark session active
     * 4. Speak greeting → TTS onDone will call requestActiveListening()
     */
    private fun onWakeWordDetected() {
        Log.d(TAG, "🎯 Wake word activated!")
        cancelPendingRestart()
        killRecognizer()
        recognizerBusy = false
        sessionActive = true
        _isListening.value = false
        speak("I'm here! What can I help you with?")
    }

    // ─── Public API (Screen / manual mic) ──────────────────

    fun startVoiceInput() {
        cancelPendingRestart()
        tts?.stop()
        _isSpeaking.value = false
        sessionActive = true
        _userTranscription.value = ""
        _partialText.value = ""
        mainHandler.post { startRecognizerOnMain(passive = false) }
    }

    fun stopVoiceInput() {
        cancelPendingRestart()
        sessionActive = false
        mainHandler.post {
            killRecognizer()
            recognizerBusy = false
        }
        _isListening.value = false
        requestPassiveListening()
    }

    fun sendMessage(message: String) {
        if (message.isNotBlank()) processUserInput(message)
    }

    fun connectLive() { /* no-op */ }
    fun sendCameraFrame(base64Image: String) { /* no-op */ }

    fun analyzeGalleryImage(bitmap: Bitmap) {
        speak("I can see the photo you picked! The colors work beautifully together and the fit looks great. I'd suggest adding a statement belt or watch to complete the look.")
    }

    fun reset() {
        cancelPendingRestart()
        mainHandler.post {
            killRecognizer()
            recognizerBusy = false
        }
        tts?.stop()
        _isListening.value = false
        _isSpeaking.value = false
        _auraState.value = AuraState.PASSIVE_LISTENING
    }

    // ─── Process User Input ────────────────────────────────

    private fun processUserInput(userText: String) {
        Log.d(TAG, "💬 Processing: '$userText'")

        _chatMessages.value = _chatMessages.value + ChatMessage(role = MessageRole.USER, content = userText)
        _auraState.value = AuraState.PROCESSING
        _isLoading.value = true

        val lower = userText.lowercase()

        // ── Stop commands (ends continuous session) ──
        if (lower.contains("stop listening") || lower.contains("goodbye") ||
            lower.contains("bye bye") || lower.contains("that's all") ||
            lower.contains("stop aura") || lower.contains("go to sleep")) {
            sessionActive = false
            speak("Goodbye! Say Hey Aura when you need me again.")
            return
        }

        // ── Camera commands ──
        if (lower.contains("click picture") || lower.contains("take picture") ||
            lower.contains("take photo") || lower.contains("click photo") ||
            lower.contains("take a picture") || lower.contains("take a photo") ||
            lower.contains("capture") || lower.contains("snap") ||
            lower.contains("cheese") || lower.contains("take a pic") ||
            lower.contains("photo") || lower.contains("picture")) {
            _captureTrigger.value += 1
            speak("Done! I've captured that for you. Looking great!")
            return
        }

        // ── Flip camera ──
        if (lower.contains("flip camera") || lower.contains("switch camera") ||
            lower.contains("back camera") || lower.contains("front camera")) {
            speak("Sure! Tap the flip camera button in the top right corner to switch cameras.")
            return
        }

        // ── Fashion responses ──
        speak(generateFashionResponse(lower))
    }

    // ─── Fashion Response Engine ───────────────────────────

    private fun generateFashionResponse(input: String): String = when {
        (input.contains("hello") || input.contains("hi ") || input.startsWith("hi")) &&
            !input.contains("aura") ->
            "Hey there! I'm Aura, your personal AI stylist. I can see you through the camera! You're looking great. Want me to analyze your outfit or suggest something new?"

        input.contains("how do i look") || input.contains("how am i looking") ||
        input.contains("rate my") || input.contains("what do you think") ||
        input.contains("do i look good") || input.contains("look okay") ->
            "You're looking absolutely fantastic! I love the color coordination. The fit is great and suits you perfectly. I'd rate this a solid 9 out of 10! A simple watch or bracelet could take it even higher."

        input.contains("what should i wear") || input.contains("what to wear") ||
        input.contains("suggest") || input.contains("outfit idea") ->
            "I'd suggest pairing your current look with slim-fit dark denim and clean white sneakers. For something more polished, swap the sneakers for Chelsea boots and add a minimalist watch."

        input.contains("color") || input.contains("colour") ->
            "Great question! You'd look amazing in earth tones like olive green, rust, and warm browns. Navy blue and burgundy would also complement you perfectly."

        input.contains("accessor") || input.contains("jewelry") ||
        input.contains("watch") || input.contains("bracelet") ->
            "I'd recommend a classic minimalist watch — Daniel Wellington or MVMT. A simple chain necklace adds a nice touch too. Tortoiseshell glasses frames are super trendy right now!"

        input.contains("shoe") || input.contains("sneaker") || input.contains("footwear") ||
        input.contains("boot") ->
            "White Nike Air Force 1s are timeless at about 110 dollars. New Balance 550s are trending for around 100. For dressier, Clarks Desert Boots in beeswax at 120 dollars."

        input.contains("buy") || input.contains("shop") || input.contains("purchase") ||
        input.contains("recommend") || input.contains("products") || input.contains("price") ->
            "My top picks: Levi's 501 jeans around 70 dollars, Uniqlo Supima Cotton Tee for just 15 dollars, and Nike Tech Fleece Joggers at 110 dollars — comfortable and stylish!"

        input.contains("weather") || input.contains("rain") || input.contains("cold") ||
        input.contains("hot") || input.contains("warm") || input.contains("summer") ||
        input.contains("winter") ->
            "For warm days, go with breathable cotton and linen in light colors. For chilly weather, layer with a denim jacket or classic bomber. A hoodie-under-blazer look is trending right now!"

        input.contains("formal") || input.contains("office") || input.contains("meeting") ||
        input.contains("interview") || input.contains("professional") ->
            "A well-fitted blazer in navy paired with chinos and a crisp white button-down is perfect. Roll the sleeves slightly for a modern touch. Brown leather Oxfords complete the look!"

        input.contains("casual") || input.contains("relax") || input.contains("weekend") ||
        input.contains("chill") || input.contains("comfort") ->
            "Casual vibes! Go with a graphic tee, comfortable joggers, and fresh sneakers. Add a baseball cap for extra style. Simple and comfortable is the key to great casual style!"

        input.contains("date") || input.contains("night out") || input.contains("dinner") ||
        input.contains("party") || input.contains("club") ->
            "For a date night, a dark fitted henley with slim dark jeans and Chelsea boots is a winning combo. Add some cologne — confidence is the best accessory!"

        input.contains("hair") || input.contains("hairstyle") ->
            "Your hair looks great! For a change, a textured crop or clean fade on the sides is trending. Use a matte clay for that effortless styled look."

        input.contains("gym") || input.contains("workout") || input.contains("sport") ||
        input.contains("athletic") || input.contains("exercise") ->
            "Nike Dri-FIT or Under Armour are great for workouts. Lululemon ABC pants work for gym and casual. Nike Pegasus or Ultraboosts for shoes — essential!"

        input.contains("thank") || input.contains("thanks") || input.contains("awesome") ||
        input.contains("perfect") || input.contains("love it") ->
            "You're welcome! The best outfit is the one you feel confident in. You've got great style — keep rocking it!"

        input.contains("help") || input.contains("what can you do") ->
            "I can analyze your outfit, suggest new looks, recommend products with prices, give occasion-specific advice, and take photos when you say 'take a picture'. Just talk to me!"

        input.contains("work") ->
            "For work, a smart casual look with chinos, a well-fitted polo or button-down, and clean leather sneakers works great. Layer with a light blazer if needed!"

        else ->
            "Great question! From what I can see, you've put together a really nice look. The vibe is on point. Tell me where you're headed and I can give more specific advice!"
    }

    // ─── TTS ───────────────────────────────────────────────

    /**
     * Speaks a response via TTS.
     * Flow: cancel pending → kill recognizer → speak → (onDone → afterTtsFinished)
     */
    private fun speak(response: String) {
        _isLoading.value = false

        _chatMessages.value = _chatMessages.value + ChatMessage(role = MessageRole.ASSISTANT, content = response)
        _partialText.value = response

        // Cancel pending restarts and kill recognizer before speaking
        cancelPendingRestart()
        mainHandler.post {
            killRecognizer()
            recognizerBusy = false
        }

        if (ttsReady) {
            _auraState.value = AuraState.SPEAKING
            _isSpeaking.value = true
            _isListening.value = false
            tts?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "aura_${System.currentTimeMillis()}")
        } else {
            // TTS not ready — show text, then restart listening after delay
            Log.w(TAG, "TTS not ready, falling back to text")
            scheduleRestart(2500L) { afterTtsFinished() }
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        isShutdown = true
        cancelPendingRestart()
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            killRecognizer()
            recognizerBusy = false
        }
        tts?.stop()
        tts?.shutdown()
    }
}

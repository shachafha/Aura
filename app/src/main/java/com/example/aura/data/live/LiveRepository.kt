package com.example.aura.data.live

import android.util.Base64
import android.util.Log
import com.example.aura.BuildConfig
import com.example.aura.data.model.ChatMessage
import com.example.aura.data.model.MessageRole
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages the OkHttp WebSocket connection to the Gemini Live API backend.
 *
 * Optimized for minimum latency:
 * - Audio sent as RAW BINARY WebSocket frames (no base64/JSON overhead)
 * - Images and text sent as JSON text frames
 * - Pre-connects on app launch
 */
class LiveRepository {

    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS) // Keep connection alive
        .build()

    private var webSocket: WebSocket? = null
    private val gson = Gson()

    private val sessionId = UUID.randomUUID().toString().take(8)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    /** What the AI is saying (output transcription) */
    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    /** What the user is saying (input audio transcription — shown live) */
    private val _userTranscription = MutableStateFlow("")
    val userTranscription: StateFlow<String> = _userTranscription.asStateFlow()

    // Callbacks
    var onAudioReceived: ((ByteArray) -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null

    companion object {
        private const val TAG = "LiveRepository"
    }

    /**
     * Connects to ws://backend/ws/{session_id}
     * Call this early (on app launch) for instant readiness.
     */
    fun connect() {
        if (webSocket != null) return

        val wsUrl = BuildConfig.BACKEND_URL.replace("http://", "ws://") + "ws/$sessionId"

        val request = Request.Builder()
            .url(wsUrl)
            .build()
            
        Log.d(TAG, "Attempting connection to WS URL: $wsUrl")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ WebSocket Connected!")
                _isConnected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Log.d(TAG, "↓ Received message (len=${text.length})")
                handleServerMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "❌ WebSocket Closed: $reason")
                _isConnected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "❌ WebSocket Failure: ${t.message}", t)
                _isConnected.value = false
                // Auto-reconnect after 2 seconds
                t.printStackTrace()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
    }

    // ─── SEND: Ultra-Low Latency ─────────────────────────

    /**
     * Send raw PCM audio as a BINARY WebSocket frame.
     * No base64 encoding, no JSON wrapping — zero overhead.
     * The backend handles binary frames directly (server.py line 413).
     */
    fun sendAudio(pcmData: ByteArray) {
        webSocket?.send(pcmData.toByteString())
    }

    /**
     * Send base64 camera frame as JSON text frame.
     */
    fun sendImage(base64Jpeg: String) {
        if (_isConnected.value) {
            val json = JsonObject().apply {
                addProperty("type", "image")
                addProperty("data", base64Jpeg)
                addProperty("mimeType", "image/jpeg")
            }
            webSocket?.send(json.toString())
        }
    }

    /**
     * Send base64 camera frame + text prompt as a SINGLE JSON message.
     * The backend bundles them into one Content so Gemini sees the image with the question.
     */
    fun sendImageWithText(base64Jpeg: String, prompt: String) {
        if (_isConnected.value) {
            val json = JsonObject().apply {
                addProperty("type", "image_with_text")
                addProperty("data", base64Jpeg)
                addProperty("mimeType", "image/jpeg")
                addProperty("text", prompt)
            }
            webSocket?.send(json.toString())
            Log.d("LiveRepository", "📸 Sent image+text (${base64Jpeg.length} base64 chars)")
        }
    }

    /**
     * Send a standard text message.
     */
    fun sendText(text: String) {
        if (_isConnected.value) {
            val userMsg = ChatMessage(role = MessageRole.USER, content = text)
            _chatMessages.value = _chatMessages.value + userMsg

            val json = JsonObject().apply {
                addProperty("type", "text")
                addProperty("text", text)
            }
            webSocket?.send(json.toString())
        }
    }

    /** Clear the live transcription text */
    fun clearUserTranscription() {
        _userTranscription.value = ""
    }

    // ─── RECEIVE: Parse ADK Events ──────────────────────

    private fun handleServerMessage(jsonString: String) {
        try {
            val element = gson.fromJson(jsonString, JsonObject::class.java)

            // 1. Audio and Text output from AI
            if (element.has("content")) {
                val content = element.getAsJsonObject("content")
                if (content.has("parts")) {
                    for (partItem in content.getAsJsonArray("parts")) {
                        val part = partItem.asJsonObject
                        
                        // Audio PCM data
                        if (part.has("inlineData")) {
                            val inlineData = part.getAsJsonObject("inlineData")
                            if (inlineData.has("mimeType") && inlineData.get("mimeType").asString.startsWith("audio/pcm")) {
                                val audioBase64 = inlineData.get("data").asString
                                val pcmBytes = Base64.decode(audioBase64, Base64.URL_SAFE)
                                onAudioReceived?.invoke(pcmBytes)
                            }
                        }
                        
                        // Text message block
                        if (part.has("text")) {
                            val text = part.get("text").asString
                            if (text.isNotBlank()) {
                                val aiMsg = ChatMessage(role = MessageRole.ASSISTANT, content = text)
                                _chatMessages.value = _chatMessages.value + aiMsg
                                _partialText.value = text
                            }
                        }
                    }
                }
            }

            // 2. Output Audio Transcription (live typing out of Aura's voice)
            val outTrans = element.getAsJsonObject("outputTranscription")
                ?: element.getAsJsonObject("output_transcription")
            if (outTrans != null) {
                val transcript = outTrans.get("text")?.asString
                    ?: outTrans.get("transcript")?.asString
                    ?: outTrans.get("final_transcript")?.asString
                    ?: ""
                if (transcript.isNotBlank()) {
                    _partialText.value = transcript
                }
            }

            // 3. Input Audio Transcription (live typing out of User's voice)
            val inTrans = element.getAsJsonObject("inputTranscription")
                ?: element.getAsJsonObject("input_transcription")
            if (inTrans != null) {
                val transcript = inTrans.get("text")?.asString
                    ?: inTrans.get("transcript")?.asString
                    ?: inTrans.get("final_transcript")?.asString
                    ?: ""
                if (transcript.isNotBlank()) {
                    _userTranscription.value = transcript
                    
                    // Note: In ADK, input transcriptions stream continuously.
                    // We only want to add it to history when the turn completes, or if it's final.
                    // But for now, just show it on the top screen.
                }
            }

            // 4. Client Content (User's complete text block sent back)
            if (element.has("clientContent")) {
                val cc = element.getAsJsonObject("clientContent")
                if (cc.has("turns")) {
                    for (turnItem in cc.getAsJsonArray("turns")) {
                        val turn = turnItem.asJsonObject
                        if (turn.get("role").asString == "user" && turn.has("parts")) {
                            for (partItem in turn.getAsJsonArray("parts")) {
                                val part = partItem.asJsonObject
                                if (part.has("text")) {
                                    val text = part.get("text").asString
                                    if (text.isNotBlank()) {
                                        _userTranscription.value = text
                                        // Save final user text to history only once it's recognized as clientContent
                                        // But to prevent duplicates, rely on the ViewModel
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Turn Complete
            if (element.has("turnComplete") && element.get("turnComplete").asBoolean) {
                onTurnComplete?.invoke()
            }
            if (element.has("turn_complete") && element.get("turn_complete").asBoolean) {
                onTurnComplete?.invoke()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ADK Event: ${e.message}", e)
        }
    }
}

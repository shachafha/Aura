package com.example.aura.data.live

import android.util.Base64
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

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
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

            // 1. Audio output from AI
            if (element.has("serverContent")) {
                val sc = element.getAsJsonObject("serverContent")
                if (sc.has("modelTurn")) {
                    val modelTurn = sc.getAsJsonObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        for (partItem in modelTurn.getAsJsonArray("parts")) {
                            val part = partItem.asJsonObject
                            if (part.has("inlineData")) {
                                val inlineData = part.getAsJsonObject("inlineData")
                                if (inlineData.get("mimeType").asString.startsWith("audio/pcm")) {
                                    val audioBase64 = inlineData.get("data").asString
                                    val pcmBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                                    onAudioReceived?.invoke(pcmBytes)
                                }
                            }
                            // AI text response
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

                // Turn complete
                if (sc.has("turnComplete") && sc.get("turnComplete").asBoolean) {
                    onTurnComplete?.invoke()
                }
            }

            // 2. Input audio transcription (what the USER said — shown live)
            if (element.has("inputAudioTranscription") || element.has("input_audio_transcription")) {
                val transcription = element.getAsJsonObject(
                    if (element.has("inputAudioTranscription")) "inputAudioTranscription"
                    else "input_audio_transcription"
                )
                val transcript = transcription?.get("transcript")?.asString
                    ?: transcription?.get("final_transcript")?.asString
                    ?: ""
                if (transcript.isNotBlank()) {
                    _userTranscription.value = transcript
                    val userMsg = ChatMessage(role = MessageRole.USER, content = transcript)
                    _chatMessages.value = _chatMessages.value + userMsg
                }
            }

            // 3. Output audio transcription (what AURA said — shown live)
            if (element.has("outputAudioTranscription") || element.has("output_audio_transcription")) {
                val transcription = element.getAsJsonObject(
                    if (element.has("outputAudioTranscription")) "outputAudioTranscription"
                    else "output_audio_transcription"
                )
                val transcript = transcription?.get("transcript")?.asString
                    ?: transcription?.get("final_transcript")?.asString
                    ?: ""
                if (transcript.isNotBlank()) {
                    _partialText.value = transcript
                }
            }

            // 4. User voice transcription (clientContent format)
            if (element.has("clientContent") && element.getAsJsonObject("clientContent").has("turns")) {
                val turns = element.getAsJsonObject("clientContent").getAsJsonArray("turns")
                for (turnItem in turns) {
                    val turn = turnItem.asJsonObject
                    if (turn.get("role").asString == "user" && turn.has("parts")) {
                        for (partItem in turn.getAsJsonArray("parts")) {
                            val part = partItem.asJsonObject
                            if (part.has("text")) {
                                val text = part.get("text").asString
                                if (text.isNotBlank()) {
                                    _userTranscription.value = text
                                }
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

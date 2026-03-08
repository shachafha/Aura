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
import java.util.UUID

/**
 * Manages the OkHttp WebSocket connection to the Gemini Live API Backend streaming endpoint.
 */
class LiveRepository {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    
    private val sessionId = UUID.randomUUID().toString().take(8)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    // Provide raw PCM audio bytes to be played back
    var onAudioReceived: ((ByteArray) -> Unit)? = null

    /**
     * Connects to ws://backend/ws/{session_id}
     */
    fun connect() {
        if (webSocket != null) return

        // Replace http:// with ws://
        val wsUrl = BuildConfig.BACKEND_URL.replace("http://", "ws://") + "ws/$sessionId"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
                System.out.println("LiveRepository: WebSocket Opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                System.out.println("LiveRepository: WebSocket Closed - $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
                t.printStackTrace()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
    }

    /**
     * Send raw PCM microphone audio bytes
     */
    fun sendAudio(pcmData: ByteArray) {
        if (_isConnected.value) {
            val base64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            val json = JsonObject().apply {
                addProperty("type", "audio")
                addProperty("data", base64)
            }
            webSocket?.send(json.toString())
        }
    }

    /**
     * Send base64 camera frame 
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
     * Send a standard text message
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

    /**
     * Parses the ADK JSON events coming from the server.
     * Looks for audio bytes to play, and transcriptions to show in the UI.
     */
    private fun handleServerMessage(jsonString: String) {
        try {
            val element = gson.fromJson(jsonString, JsonObject::class.java)
            
            // 1. Check for audio output
            if (element.has("serverContent") && element.getAsJsonObject("serverContent").has("modelTurn")) {
                val modelTurn = element.getAsJsonObject("serverContent").getAsJsonObject("modelTurn")
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
                    }
                }
            }

            // 2. Check for AI transcription (final AI response)
            if (element.has("serverContent") && element.getAsJsonObject("serverContent").has("modelTurn")) {
                val modelTurn = element.getAsJsonObject("serverContent").getAsJsonObject("modelTurn")
                if (modelTurn.has("parts")) {
                     for (partItem in modelTurn.getAsJsonArray("parts")) {
                        val part = partItem.asJsonObject
                        if (part.has("text")) {
                           val text = part.get("text").asString
                           // Avoid adding empty turns
                           if (text.isNotBlank()) {
                               val aiMsg = ChatMessage(role = MessageRole.ASSISTANT, content = text)
                               _chatMessages.value = _chatMessages.value + aiMsg
                               _partialText.value = text // Update the bubble
                           }
                        }
                    }
                }
            }
            
            // 3. User Voice Transcription
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
                                    val userMsg = ChatMessage(role = MessageRole.USER, content = text)
                                    _chatMessages.value = _chatMessages.value + userMsg
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

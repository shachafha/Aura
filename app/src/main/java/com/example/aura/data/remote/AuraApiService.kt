package com.example.aura.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface for the Aura Cloud Run backend.
 *
 * This replaces direct Gemini API calls. All AI logic now
 * lives on the backend (ADK agent).
 */
interface AuraApiService {

    @GET("health")
    suspend fun healthCheck(): HealthResponse

    /**
     * Analyze an outfit image. Backend runs Gemini Vision + weather tools.
     *
     * @param request Image (base64) and user location
     * @return Outfit analysis, weather context, and greeting
     */
    @POST("analyze")
    suspend fun analyzeOutfit(@Body request: AnalyzeRequest): AnalyzeResponse

    /**
     * Send a chat message to the ADK stylist agent.
     * Backend handles search grounding, weather, and outfit history.
     *
     * @param request Message with outfit context and history
     * @return AI response with optional product recommendations
     */
    @POST("chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}

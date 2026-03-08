package com.example.aura.data.remote

import com.example.aura.data.model.ClothingItem
import com.example.aura.data.model.Recommendation
import com.google.gson.annotations.SerializedName

/**
 * Request / response models for the Aura backend API.
 *
 * These mirror the Pydantic models in server.py.
 */

// ─── Health Check ────────────────────────────────────────────────

data class HealthResponse(
    val status: String,
    val agent: String
)

// ─── Analyze Endpoint ────────────────────────────────────────────

data class AnalyzeRequest(
    @SerializedName("image_base64") val imageBase64: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

data class AnalyzeResponse(
    @SerializedName("outfit_analysis") val outfitAnalysis: OutfitAnalysisDto,
    val weather: WeatherDto? = null,
    val greeting: String = ""
)

data class OutfitAnalysisDto(
    val items: List<ClothingItemDto> = emptyList(),
    @SerializedName("overall_style") val overallStyle: String = "",
    @SerializedName("dominant_colors") val dominantColors: List<String> = emptyList(),
    val summary: String = ""
)

data class ClothingItemDto(
    val name: String,
    val category: String = "",
    val color: String = ""
) {
    fun toModel() = ClothingItem(name, category, color)
}

data class WeatherDto(
    @SerializedName("temp_f") val tempF: Int = 0,
    val condition: String = "",
    val description: String = "",
    val city: String = "",
    @SerializedName("styling_note") val stylingNote: String = ""
)

// ─── Chat Endpoint ───────────────────────────────────────────────

data class ChatRequest(
    val message: String,
    @SerializedName("outfit_context") val outfitContext: OutfitAnalysisDto? = null,
    @SerializedName("outfit_history") val outfitHistory: List<OutfitHistoryDto> = emptyList(),
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

data class ChatResponse(
    val message: String,
    val recommendations: List<RecommendationDto> = emptyList()
)

data class RecommendationDto(
    @SerializedName("item_name") val itemName: String,
    val description: String = "",
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("shopping_url") val shoppingUrl: String? = null,
    val category: String = ""
) {
    fun toModel() = Recommendation(
        itemName = itemName,
        description = description,
        imageUrl = imageUrl,
        category = category
    )
}

data class OutfitHistoryDto(
    val date: String,
    val items: List<ClothingItemDto> = emptyList(),
    val style: String = ""
)

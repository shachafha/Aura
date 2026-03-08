package com.example.aura.data.repository

import android.graphics.Bitmap
import com.example.aura.data.model.ChatMessage
import com.example.aura.data.model.ClothingItem
import com.example.aura.data.model.MessageRole
import com.example.aura.data.model.OutfitAnalysis
import com.example.aura.data.model.Recommendation
import com.example.aura.data.remote.WeatherDto
import kotlinx.coroutines.delay

/**
 * Mock repository for testing the full voice-camera flow without API keys.
 *
 * Returns realistic-looking fake data with short delays to simulate
 * network calls. Activate via BuildConfig.USE_MOCK = true.
 */
class MockRepository : AuraRepository(apiService = null, historyManager = null) {

    private val mockAnalysis = OutfitAnalysis(
        items = listOf(
            ClothingItem("White Blouse", "tops", "White"),
            ClothingItem("Black Skinny Jeans", "bottoms", "Black"),
            ClothingItem("Gold Necklace", "accessories", "Gold"),
            ClothingItem("White Sneakers", "shoes", "White")
        ),
        overallStyle = "Chic Casual",
        dominantColors = listOf("#FFFFFF", "#1A1A1A", "#D4AF37"),
        summary = "Love this look! You've got a clean white blouse with black skinny jeans — very chic casual. The gold necklace adds a perfect touch of warmth. Let me help you style this further!"
    )

    private val mockWeather = WeatherDto(
        tempF = 68,
        condition = "Partly Cloudy",
        description = "Partly cloudy with a light breeze",
        city = "New York",
        stylingNote = "Great weather for layering — a light jacket would complete this look."
    )

    private val mockResponses = listOf(
        "A structured tan tote bag would complement your black and white palette beautifully! The warm leather tone ties in with your gold necklace. Try Coach or Madewell for great options under \$200.",
        "For a bag, I'd suggest a camel crossbody — it bridges the black and white effortlessly. A mini bucket bag would also work if you want something trendier!",
        "That outfit would transition perfectly from day to evening! Swap the sneakers for black ankle boots and add a blazer. You'd look amazing at any dinner.",
        "Based on your style, check out Zara's new minimalist collection — lots of pieces that match your clean aesthetic. Uniqlo also has great basics that pair well.",
        "For jewelry, try layering delicate gold bracelets — they'd match your necklace and add dimension. Mejuri and Ana Luisa have gorgeous affordable options!"
    )

    private var responseIndex = 0

    override suspend fun analyzeOutfit(image: Bitmap) {
        _isLoading.value = true
        delay(1500)

        _outfitAnalysis.value = mockAnalysis
        _weather.value = mockWeather

        _chatMessages.value = listOf(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                content = mockAnalysis.summary
            )
        )
        _isLoading.value = false
    }

    override suspend fun sendMessage(message: String) {
        val userMsg = ChatMessage(role = MessageRole.USER, content = message)
        _chatMessages.value = _chatMessages.value + userMsg

        _isLoading.value = true
        delay(1200)

        val response = mockResponses[responseIndex % mockResponses.size]
        responseIndex++

        val mockRecs = if (responseIndex % 2 == 0) {
            listOf(
                Recommendation("Tan Leather Tote", "Structured leather tote in camel", "https://via.placeholder.com/200x200/D4A574/fff?text=Tote", "bags"),
                Recommendation("Gold Chain Bracelet", "Delicate layering bracelet", "https://via.placeholder.com/200x200/D4AF37/fff?text=Bracelet", "jewelry"),
                Recommendation("Black Ankle Boots", "Classic pointed toe bootie", "https://via.placeholder.com/200x200/1A1A1A/fff?text=Boots", "shoes")
            )
        } else null

        val aiMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = response,
            recommendations = mockRecs
        )
        _chatMessages.value = _chatMessages.value + aiMsg
        _isLoading.value = false
    }

    override fun clearSession() {
        _outfitAnalysis.value = null
        _chatMessages.value = emptyList()
        _error.value = null
        _weather.value = null
        responseIndex = 0
    }
}

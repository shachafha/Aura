package com.example.aura.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.aura.data.model.OutfitAnalysis
import com.example.aura.data.remote.ClothingItemDto
import com.example.aura.data.remote.OutfitHistoryDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages on-device outfit history.
 *
 * Saves past outfit analyses with dates so the backend agent
 * can reference what the user wore recently and suggest variety.
 * Uses SharedPreferences for hackathon speed — swap to Room later.
 */
class OutfitHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aura_outfit_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        private const val KEY_HISTORY = "outfit_history"
        private const val MAX_HISTORY = 30 // Keep last 30 outfits
    }

    /**
     * Save an analyzed outfit to history.
     */
    fun saveOutfit(analysis: OutfitAnalysis) {
        val history = getHistoryMutable()

        val entry = OutfitHistoryDto(
            date = dateFormat.format(Date()),
            items = analysis.items.map { ClothingItemDto(it.name, it.category, it.color) },
            style = analysis.overallStyle
        )

        history.add(entry)

        // Trim to max size
        while (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }

        prefs.edit()
            .putString(KEY_HISTORY, gson.toJson(history))
            .apply()
    }

    /**
     * Get recent outfit history as DTOs for the backend API.
     *
     * @param limit Number of recent outfits to return (default 7)
     */
    fun getRecentHistory(limit: Int = 7): List<OutfitHistoryDto> {
        return getHistoryMutable().takeLast(limit)
    }

    /**
     * Get formatted summary for display.
     */
    fun getHistorySummary(): String {
        val history = getRecentHistory(5)
        if (history.isEmpty()) return "No outfit history yet"

        return history.joinToString("\n") { outfit ->
            val items = outfit.items.joinToString(", ") { it.name }
            "${outfit.date}: $items (${outfit.style})"
        }
    }

    /**
     * Clear all history.
     */
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun getHistoryMutable(): MutableList<OutfitHistoryDto> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<OutfitHistoryDto>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }
}

package com.example.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Compact weather badge showing current temperature and condition.
 * Displayed at the top of analysis and chat screens.
 *
 * @param tempF Temperature in Fahrenheit
 * @param condition Weather condition (e.g. "Sunny", "Rain")
 * @param city City name
 */
@Composable
fun WeatherBadge(
    tempF: Int,
    condition: String,
    city: String = ""
) {
    val weatherEmoji = when {
        condition.contains("rain", true) || condition.contains("drizzle", true) -> "🌧️"
        condition.contains("snow", true) -> "❄️"
        condition.contains("cloud", true) -> "☁️"
        condition.contains("sun", true) || condition.contains("clear", true) -> "☀️"
        condition.contains("thunder", true) -> "⛈️"
        condition.contains("fog", true) || condition.contains("mist", true) -> "🌫️"
        else -> "🌤️"
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = weatherEmoji,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "${tempF}°F",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (city.isNotBlank()) {
            Text(
                text = city,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

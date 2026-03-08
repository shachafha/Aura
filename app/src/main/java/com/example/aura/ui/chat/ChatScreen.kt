package com.example.aura.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aura.data.model.ChatMessage
import com.example.aura.data.model.MessageRole
import com.example.aura.data.remote.WeatherDto
import com.example.aura.ui.chat.components.ChatInput
import com.example.aura.ui.chat.components.MessageBubble
import com.example.aura.ui.chat.components.RecommendationCard
import com.example.aura.ui.chat.components.SuggestionChips
import com.example.aura.ui.chat.components.TypingIndicator
import com.example.aura.ui.components.AuraTopBar
import com.example.aura.ui.components.WeatherBadge

/**
 * Main chat screen for the AI stylist conversation.
 *
 * Now includes weather badge and supports product recommendation cards
 * with real shopping links from Google Search grounding.
 * Features:
 * - Animated message bubbles with Aura avatar
 * - Bouncing typing indicator while AI responds
 * - Quick suggestion chips for onboarding
 * - Horizontally scrollable recommendation cards
 *
 * @param viewModel ChatViewModel instance
 * @param onBack Navigate back to analysis screen
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    weather: WeatherDto? = null,
    onBack: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()

    // Show suggestion chips only until the user sends their first message
    val showSuggestions by remember {
        derivedStateOf {
            messages.none { it.role == MessageRole.USER }
        }
    }

    val suggestions = remember {
        listOf(
            "What bag matches this?",
            "Is this date-ready?",
            "Suggest shoes",
            "Color advice"
        )
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar with optional weather
        AuraTopBar(
            title = "Aura Stylist",
            onBackClick = onBack
        )

        // Weather badge below top bar
        weather?.let { w ->
            if (w.tempF > 0) {
                WeatherBadge(
                    tempF = w.tempF,
                    condition = w.condition,
                    city = w.city
                )
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            reverseLayout = false
        ) {
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    isUser = message.role == MessageRole.USER
                )

                // Show recommendation cards if present
                message.recommendations?.let { recs ->
                    if (recs.isNotEmpty()) {
                        RecommendationCard(recommendations = recs)
                    }
                }
            }

            // Typing indicator
            if (isLoading) {
                item {
                    TypingIndicator()
                }
            }
        }

        // Quick suggestion chips (visible until first user message)
        SuggestionChips(
            suggestions = suggestions,
            visible = showSuggestions,
            onSuggestionClick = { viewModel.sendMessage(it) }
        )

        // Input Bar
        ChatInput(
            onSendMessage = { viewModel.sendMessage(it) },
            isLoading = isLoading
        )
    }
}

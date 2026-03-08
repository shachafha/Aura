package com.example.aura.ui.live

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aura.data.model.ChatMessage
import com.example.aura.data.model.MessageRole
import com.example.aura.ui.chat.components.MessageBubble
import com.example.aura.ui.chat.components.RecommendationCard

/**
 * Full conversation transcript in a bottom sheet.
 *
 * Reuses [MessageBubble] and [RecommendationCard] from the chat UI module.
 * Opens when the user taps "View Chat" on the live stylist screen.
 *
 * @param messages Full chat history
 * @param onDismiss Called when the sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptSheet(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = {
            Spacer(modifier = Modifier.height(12.dp))
        }
    ) {
        // Header
        Text(
            text = "Conversation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        if (messages.isEmpty()) {
            Text(
                text = "No messages yet. Capture your outfit to start!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .weight(1f)
            ) {
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        isUser = message.role == MessageRole.USER
                    )

                    // Show recommendation cards inline
                    message.recommendations?.let { recs ->
                        if (recs.isNotEmpty()) {
                            RecommendationCard(recommendations = recs)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

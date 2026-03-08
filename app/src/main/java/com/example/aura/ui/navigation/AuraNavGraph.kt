package com.example.aura.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.aura.ui.live.LiveStylistScreen
import com.example.aura.ui.live.LiveStylistViewModel
import com.example.aura.ui.live.TranscriptSheet

/**
 * Simplified navigation for the voice-first live agent.
 *
 * Single screen: LiveStylistScreen with camera + voice overlay.
 * TranscriptSheet opens as a bottom sheet over the live view.
 */
@Composable
fun AuraNavGraph() {
    val viewModel = remember { LiveStylistViewModel() }
    val messages by viewModel.chatMessages.collectAsState()

    // Bottom sheet state
    var showTranscript by remember { mutableStateOf(false) }

    // ── Main live stylist screen ──
    LiveStylistScreen(
        viewModel = viewModel,
        onViewTranscript = { showTranscript = true }
    )

    // ── Transcript bottom sheet ──
    if (showTranscript) {
        TranscriptSheet(
            messages = messages,
            onDismiss = { showTranscript = false }
        )
    }
}

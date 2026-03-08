package com.example.aura.ui.live

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The main live stylist screen — camera-first, voice-driven.
 *
 * Features:
 * - Full-screen camera preview (always live)
 * - "Hey Aura" wake word detection (always on)
 * - Auto-stop after 5 seconds of silence
 * - Gallery photo picker for analyzing past outfits
 * - Manual mic toggle as fallback
 */
@Composable
fun LiveStylistScreen(
    viewModel: LiveStylistViewModel,
    onViewTranscript: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State
    val auraState by viewModel.auraState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val partialText by viewModel.partialText.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    // Camera
    var useFrontCamera by remember { mutableStateOf(true) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    // Image analyzer for streaming frames to Gemini
    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, com.example.aura.data.live.LiveImageAnalyzer { base64 ->
                    if (isListening) {
                        viewModel.sendCameraFrame(base64)
                    }
                })
            }
    }

    // Gallery photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    viewModel.analyzeGalleryImage(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ─── Full-screen Camera Preview ─────────────────
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val cameraSelector = if (useFrontCamera)
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    else
                        CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                    } catch (_: Exception) { }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // ─── Dark gradient overlay at bottom ────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        // ─── Top Bar ────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 20.dp, end = 20.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Aura",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                // Connection indicator
                if (isConnected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Gallery button
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Pick from gallery",
                        tint = Color.White
                    )
                }

                // Camera flip button
                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip camera",
                        tint = Color.White
                    )
                }

                // View Chat transcript button
                IconButton(
                    onClick = onViewTranscript,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "View transcript",
                        tint = Color.White
                    )
                }
            }
        }

        // ─── Bottom Controls ────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // State label
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = when (auraState) {
                        LiveStylistViewModel.AuraState.PASSIVE_LISTENING -> "✨ Say \"Hey Aura\" to start"
                        LiveStylistViewModel.AuraState.ACTIVE_LISTENING -> "🎤 Aura is listening..."
                        LiveStylistViewModel.AuraState.PROCESSING -> "💭 Aura is thinking..."
                        LiveStylistViewModel.AuraState.SPEAKING -> "💬 Aura is speaking..."
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = when (auraState) {
                        LiveStylistViewModel.AuraState.PASSIVE_LISTENING -> Color.White.copy(alpha = 0.6f)
                        LiveStylistViewModel.AuraState.ACTIVE_LISTENING -> Color(0xFFFF5252) // Red
                        LiveStylistViewModel.AuraState.PROCESSING -> Color(0xFFFFD740) // Gold
                        LiveStylistViewModel.AuraState.SPEAKING -> Color(0xFF69F0AE) // Green
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Transcript bubble
            AnimatedVisibility(
                visible = partialText.isNotBlank(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                TranscriptBubble(
                    message = partialText,
                    isAura = !isListening
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Live Voice & Vision Toggle (fallback for manual control) ──
            MicButton(
                isListening = isListening,
                isSpeaking = isSpeaking,
                isLoading = isLoading,
                onToggle = {
                    if (isListening) {
                        viewModel.stopVoiceInput()
                    } else {
                        viewModel.startVoiceInput()
                    }
                }
            )
        }
    }
}

/**
 * Floating transcript bubble showing the last AI message or live speech.
 */
@Composable
private fun TranscriptBubble(
    message: String,
    isAura: Boolean
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isAura) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                else Color.White.copy(alpha = 0.15f)
            )
            .border(
                width = 1.dp,
                color = if (isAura) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .animateContentSize()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isAura) MaterialTheme.colorScheme.onSurfaceVariant
            else Color.White,
            textAlign = TextAlign.Start,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Mic button — pulsing animation when listening, gold when idle.
 */
@Composable
private fun MicButton(
    isListening: Boolean,
    isSpeaking: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.3f else 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "mic_scale"
    )

    // Outer ripple when listening
    if (isListening) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
        )
    }

    FloatingActionButton(
        onClick = { if (!isLoading) onToggle() },
        modifier = Modifier
            .size(72.dp)
            .then(if (isListening) Modifier.scale(pulseScale * 0.8f) else Modifier),
        shape = CircleShape,
        containerColor = when {
            isListening -> MaterialTheme.colorScheme.error
            isSpeaking -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.primary
        },
        elevation = FloatingActionButtonDefaults.elevation(8.dp)
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isListening) "Stop listening" else "Start listening",
            modifier = Modifier.size(32.dp),
            tint = when {
                isListening -> Color.White
                else -> MaterialTheme.colorScheme.onPrimary
            }
        )
    }
}

package com.example.aura.ui.live

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
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
 * Layout:
 * - Full-screen camera preview (always live)
 * - Bottom overlay: transcript bubble + mic button + chat button
 * - Before capture: shows capture button
 * - After capture: shows mic button for voice conversation
 *
 * @param viewModel LiveStylistViewModel instance
 * @param onViewTranscript Called when user taps "View Chat" to open transcript sheet
 */
@Composable
fun LiveStylistScreen(
    viewModel: LiveStylistViewModel,
    onViewTranscript: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State
    val isLoading by viewModel.isLoading.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val partialText by viewModel.partialText.collectAsState()

    // Camera — front camera by default for fashion selfie
    var useFrontCamera by remember { mutableStateOf(true) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, com.example.aura.data.live.LiveImageAnalyzer { base64 ->
                    // Only send frames if we are actively talking to Aura
                    if (isListening) {
                        viewModel.sendCameraFrame(base64)
                    }
                })
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
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                visible = isListening || isSpeaking || isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = when {
                        isListening -> "🎤 Aura is listening..."
                        isLoading -> "✨ Aura is thinking..."
                        isSpeaking -> "💬 Aura is speaking..."
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            AnimatedVisibility(
                visible = partialText.isNotBlank() || isLoading,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                TranscriptBubble(
                    message = when {
                        isListening && partialText.isNotBlank() -> "🎤 $partialText"
                        isLoading -> "✨ Analyzing..."
                        else -> partialText
                    },
                    isAura = !isListening
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Live Voice & Vision Toggle ──
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
 * Capture button — large white circle with camera icon.
 */
@Composable
private fun CaptureButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = { if (!isLoading) onClick() },
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        containerColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(8.dp)
    ) {
        if (isLoading) {
            val infiniteTransition = rememberInfiniteTransition(label = "capture_pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "pulse"
            )
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Analyzing",
                modifier = Modifier.size(36.dp).scale(scale),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Capture outfit",
                modifier = Modifier.size(36.dp),
                tint = Color.Black
            )
        }
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

/**
 * Convert an ImageProxy to a Bitmap with correct rotation.
 */
private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    return if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}

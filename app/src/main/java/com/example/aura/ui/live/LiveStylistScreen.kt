package com.example.aura.ui.live

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveStylistScreen(
    viewModel: LiveStylistViewModel,
    onViewTranscript: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ─── Collect ViewModel state ────────────────────────
    val auraState by viewModel.auraState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val partialText by viewModel.partialText.collectAsState()
    val userTranscription by viewModel.userTranscription.collectAsState()
    val captureTrigger by viewModel.captureTrigger.collectAsState()

    // ─── Camera ─────────────────────────────────────────
    var useFrontCamera by remember { mutableStateOf(true) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Track camera flip to force rebind
    var cameraFlipKey by remember { mutableIntStateOf(0) }

    // ─── Voice-triggered photo capture + Gemini analysis ──────────────────
    LaunchedEffect(captureTrigger) {
        if (captureTrigger > 0) {
            Log.d("AuraDemo", "📸 captureTrigger=$captureTrigger → capturing + analyzing")

            // 1. Save to gallery
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,
                    "Aura_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Aura")
                }
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        Toast.makeText(context, "📸 Analyzing your look...", Toast.LENGTH_SHORT).show()

                        // Read the saved image back and send to Gemini for analysis
                        try {
                            val savedUri = output.savedUri
                            if (savedUri != null) {
                                val inputStream = context.contentResolver.openInputStream(savedUri)
                                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                                inputStream?.close()

                                if (bitmap != null) {
                                    val stream = java.io.ByteArrayOutputStream()
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, stream)
                                    val base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)

                                    // Send image + analysis prompt bundled together to Gemini
                                    viewModel.sendCameraFrameWithPrompt(
                                        base64,
                                        "I just shared a photo of myself. Please analyze my outfit, tell me what you see, and give me styling advice!"
                                    )
                                    Log.d("AuraDemo", "📸 Photo sent to Gemini for analysis (${stream.size()} bytes)")
                                    bitmap.recycle()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AuraDemo", "Failed to send photo to Gemini: ${e.message}", e)
                        }
                    }
                    override fun onError(exc: ImageCaptureException) {
                        Log.e("AuraDemo", "Photo capture failed: ${exc.message}")
                        Toast.makeText(context, "Photo capture failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // ─── Gallery photo picker ───────────────────────────
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val stream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(stream)
                stream?.close()
                if (bitmap != null) viewModel.analyzeGalleryImage(bitmap)
            } catch (e: Exception) {
                Log.e("AuraDemo", "Gallery error", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ═══ Camera Preview ═════════════════════════════
        // key(cameraFlipKey) forces the AndroidView to be recreated when flipping.
        // This properly rebinds Preview + ImageCapture to the new camera.
        key(cameraFlipKey) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().apply {
                                surfaceProvider = previewView.surfaceProvider
                            }

                            val selector = if (useFrontCamera)
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            else
                                CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageCapture
                            )
                            Log.d("AuraDemo", "Camera bound (front=$useFrontCamera)")
                        } catch (e: Exception) {
                            Log.e("AuraDemo", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ═══ Bottom Gradient ════════════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        // ═══ Top Bar ════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 20.dp, end = 20.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Aura",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                // Green dot (always on in demo mode)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Gallery
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
                    Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White)
                }

                // Flip camera
                IconButton(
                    onClick = {
                        useFrontCamera = !useFrontCamera
                        cameraFlipKey++
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = Color.White)
                }

                // Transcript
                IconButton(
                    onClick = onViewTranscript,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, "Transcript", tint = Color.White)
                }
            }
        }

        // ═══ Bottom Controls ════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // State label
            Text(
                text = when (auraState) {
                    LiveStylistViewModel.AuraState.PASSIVE_LISTENING -> "✨ Say \"Hey Aura\" to start"
                    LiveStylistViewModel.AuraState.ACTIVE_LISTENING -> "🎤 Listening..."
                    LiveStylistViewModel.AuraState.PROCESSING -> "💭 Thinking..."
                    LiveStylistViewModel.AuraState.SPEAKING -> "💬 Aura is speaking..."
                },
                style = MaterialTheme.typography.labelLarge,
                color = when (auraState) {
                    LiveStylistViewModel.AuraState.PASSIVE_LISTENING -> Color.White.copy(alpha = 0.6f)
                    LiveStylistViewModel.AuraState.ACTIVE_LISTENING -> Color(0xFFFF5252)
                    LiveStylistViewModel.AuraState.PROCESSING -> Color(0xFFFFD740)
                    LiveStylistViewModel.AuraState.SPEAKING -> Color(0xFF69F0AE)
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // User speech bubble
            AnimatedVisibility(
                visible = isListening && userTranscription.isNotBlank(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                TranscriptBubble("🎤 $userTranscription", isAura = false)
            }

            // AI response bubble
            AnimatedVisibility(
                visible = (isSpeaking || auraState == LiveStylistViewModel.AuraState.SPEAKING) && partialText.isNotBlank(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                TranscriptBubble(partialText, isAura = true)
            }

            Spacer(Modifier.height(16.dp))

            // Mic button
            MicButton(
                isListening = isListening,
                isSpeaking = isSpeaking,
                isLoading = isLoading,
                onToggle = {
                    if (isListening) viewModel.stopVoiceInput() else viewModel.startVoiceInput()
                }
            )
        }
    }
}

// ═══ Composables ════════════════════════════════════════════

@Composable
private fun TranscriptBubble(message: String, isAura: Boolean) {
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
            color = if (isAura) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
            textAlign = TextAlign.Start,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MicButton(
    isListening: Boolean,
    isSpeaking: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.3f else 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
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
                contentDescription = if (isListening) "Stop" else "Talk to Aura",
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }
    }
}

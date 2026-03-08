# 👥 AURA — Team Skills & Parallel Work Breakdown

> This document defines isolated work modules so each team member (or AI agent) can work in parallel without merge conflicts. Each module has clear boundaries, inputs, outputs, and integration points.

---

## Module Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PARALLEL WORK STREAMS                          │
├──────────────┬──────────────┬───────────────┬────────────────────────┤
│   MODULE A   │   MODULE B   │   MODULE C    │       MODULE D         │
│   Camera     │   Gemini AI  │   Chat UI     │   Theme & Polish       │
│              │   Service    │               │                        │
│  CameraX     │  GenAI SDK   │  Compose Chat │  Colors, Fonts,        │
│  Preview     │  Prompts     │  MessageList  │  Animations, Chips,    │
│  Capture     │  Parsing     │  Input Bar    │  Loading States        │
│  Permissions │  Repository  │  Rec Cards    │  App Icon, Branding    │
├──────────────┼──────────────┼───────────────┼────────────────────────┤
│  ZERO deps   │  ZERO deps   │  Uses models  │  Provides theme to     │
│  on others   │  on others   │  from B       │  all modules           │
└──────────────┴──────────────┴───────────────┴────────────────────────┘
                                    │
                              MODULE E (Integration)
                              Wire everything together
                              in ViewModel + Navigation
```

---

## MODULE A — Camera & Image Capture

**Owner:** Team Member 1 (or Agent 1)

**Files to create/modify:**
```
ui/camera/CameraScreen.kt
ui/camera/CameraViewModel.kt
util/BitmapUtils.kt
AndroidManifest.xml (add CAMERA permission)
```

**What to build:**
1. Request CAMERA permission at runtime
2. CameraX Preview composable (full-screen viewfinder)
3. Capture button (floating, centered bottom)
4. On capture: convert ImageProxy → Bitmap, compress, pass to ViewModel
5. "Retake" button after capture

**Inputs:** None (standalone)

**Outputs:**
- `Bitmap` — the captured outfit image, exposed via `CameraViewModel.capturedImage: StateFlow<Bitmap?>`

**Integration contract:**
```kotlin
// Other modules call this to get the image
cameraViewModel.capturedImage.collect { bitmap ->
    // bitmap is ready for Gemini analysis
}
```

**Key dependencies to add:**
```toml
# libs.versions.toml
camerax = "1.4.0"

# libraries
androidx-camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
```

**Acceptance criteria:**
- [ ] Camera permission requested and handled
- [ ] Live camera preview shows on screen
- [ ] Capture button takes a photo
- [ ] Bitmap is accessible via StateFlow
- [ ] Retake button resets to preview

---

## MODULE B — Gemini AI Service

**Owner:** Team Member 2 (or Agent 2)

**Files to create:**
```
data/remote/GeminiService.kt
data/repository/GeminiRepository.kt
data/model/OutfitAnalysis.kt
data/model/ChatMessage.kt
data/model/Recommendation.kt
data/model/StylistResponse.kt
util/PromptTemplates.kt
```

**What to build:**
1. Initialize `GenerativeModel` with Gemini API key
2. `analyzeOutfit(bitmap)` — sends image to Gemini with analysis prompt, parses JSON response into `OutfitAnalysis`
3. `sendStylistMessage(outfitImage, history, message)` — multimodal chat with outfit context
4. Parse Gemini responses into structured data (handle JSON in response)
5. System prompt that makes Gemini act as a fashion stylist

**Inputs:** `Bitmap` (from Module A), `String` messages (from Module C)

**Outputs:**
- `OutfitAnalysis` — structured outfit data
- `StylistResponse` — chat response + optional recommendations

**Integration contract:**
```kotlin
// GeminiRepository.kt
class GeminiRepository(private val service: GeminiService) {
    
    suspend fun analyzeOutfit(image: Bitmap): Result<OutfitAnalysis>
    
    suspend fun sendMessage(
        outfitImage: Bitmap,
        history: List<ChatMessage>,
        userMessage: String
    ): Result<StylistResponse>
}
```

**Key dependencies to add:**
```toml
# libs.versions.toml
generativeai = "0.9.0"
gson = "2.11.0"

# libraries
google-generativeai = { group = "com.google.ai.client", name = "generativeai", version.ref = "generativeai" }
google-gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
```

**Acceptance criteria:**
- [ ] Can send a Bitmap and get an OutfitAnalysis back
- [ ] Can have a multi-turn conversation with outfit context
- [ ] Responses are parsed into structured Kotlin data classes
- [ ] Error handling for API failures (timeouts, rate limits)
- [ ] System prompt produces natural, stylist-like responses

---

## MODULE C — Chat UI & Recommendations

**Owner:** Team Member 3 (or Agent 3)

**Files to create:**
```
ui/chat/ChatScreen.kt
ui/chat/ChatViewModel.kt
ui/chat/components/MessageBubble.kt
ui/chat/components/ChatInput.kt
ui/chat/components/RecommendationCard.kt
ui/analysis/AnalysisScreen.kt
ui/components/OutfitTagChip.kt
ui/components/LoadingAnimation.kt
```

**What to build:**
1. **AnalysisScreen** — shows outfit photo + detected items as chips + "Start Styling" button
2. **ChatScreen** — message list + input bar
3. **MessageBubble** — user messages (right, accent color) + Aura messages (left, surface color)
4. **RecommendationCard** — horizontal scrollable cards showing suggested items
5. **ChatInput** — text field + send button (+ optional mic icon)
6. **LoadingAnimation** — shimmer or pulse animation for "Analyzing..." state

**Inputs:** Uses data models from Module B (`ChatMessage`, `OutfitAnalysis`, `Recommendation`)

**Outputs:** Compose screens ready to wire into navigation

**Integration contract:**
```kotlin
// ChatScreen expects these parameters
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    outfitAnalysis: OutfitAnalysis,
    isLoading: Boolean,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit
)

@Composable
fun AnalysisScreen(
    outfitImage: Bitmap,
    analysis: OutfitAnalysis?,
    isLoading: Boolean,
    onStartChat: () -> Unit,
    onRetake: () -> Unit
)
```

**Key dependencies to add:**
```toml
# libs.versions.toml
coil = "2.7.0"
navigationCompose = "2.7.7"

# libraries
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
```

**Acceptance criteria:**
- [ ] Chat messages display in scrollable list
- [ ] User messages appear on right, Aura on left
- [ ] Text input with send button works
- [ ] Recommendation cards show horizontally when present
- [ ] Analysis screen shows detected items as colored chips
- [ ] Loading animation looks polished

---

## MODULE D — Theme, Branding & Polish

**Owner:** Team Member 4 (or Agent 4 — can run in parallel with all others)

**Files to modify/create:**
```
ui/theme/Color.kt
ui/theme/Theme.kt
ui/theme/Type.kt
ui/components/AuraTopBar.kt
res/values/strings.xml
res/values/colors.xml
res/drawable/ (custom icons)
```

**What to build:**
1. **Color palette** — dark-mode first, fashion-forward (think SSENSE, Zara)
2. **Typography** — Google Fonts (Inter or Outfit)
3. **App branding** — app icon, splash theme
4. **Shared components** — `AuraTopBar`, gradient backgrounds
5. **Micro-animations** — fade-ins, slide-ups for messages, shimmer for loading

**Suggested Color Palette:**
```kotlin
// Dark Mode (Primary)
val AuraBlack = Color(0xFF0D0D0D)
val AuraSurface = Color(0xFF1A1A1A)
val AuraAccent = Color(0xFFE8C547)      // Gold accent
val AuraSecondary = Color(0xFFA78BFA)   // Soft purple
val AuraText = Color(0xFFF5F5F5)
val AuraTextSecondary = Color(0xFF9CA3AF)
val AuraSuccess = Color(0xFF34D399)
```

**Acceptance criteria:**
- [ ] Dark mode looks premium and fashion-forward
- [ ] Typography is clean (no default Roboto feel)
- [ ] App icon is set and looks good
- [ ] Loading states have smooth animations
- [ ] Overall feel: SSENSE/Net-a-Porter quality, not bootcamp-project quality

---

## MODULE E — Integration & Navigation (After A-D are done)

**Owner:** Tech Lead / anyone

**Files to create/modify:**
```
MainActivity.kt
ui/navigation/AuraNavGraph.kt
di/AppModule.kt (optional, can skip DI for hackathon)
```

**What to build:**
1. Wire CameraScreen → AnalysisScreen → ChatScreen navigation
2. Create shared ViewModel or pass data between screens
3. Connect Camera output → Gemini Service → Chat UI
4. Add API key configuration (BuildConfig or local.properties)
5. Final end-to-end testing

**Integration Flow:**
```kotlin
// AuraNavGraph.kt
NavHost(navController, startDestination = "camera") {
    composable("camera") {
        CameraScreen(
            onImageCaptured = { bitmap ->
                sharedViewModel.setCapturedImage(bitmap)
                sharedViewModel.analyzeOutfit()
                navController.navigate("analysis")
            }
        )
    }
    composable("analysis") {
        AnalysisScreen(
            outfitImage = sharedViewModel.capturedImage,
            analysis = sharedViewModel.outfitAnalysis,
            isLoading = sharedViewModel.isAnalyzing,
            onStartChat = { navController.navigate("chat") },
            onRetake = { navController.popBackStack() }
        )
    }
    composable("chat") {
        ChatScreen(
            messages = sharedViewModel.chatMessages,
            outfitAnalysis = sharedViewModel.outfitAnalysis,
            isLoading = sharedViewModel.isChatLoading,
            onSendMessage = { sharedViewModel.sendMessage(it) },
            onBack = { navController.popBackStack() }
        )
    }
}
```

---

## Dependency Matrix

| Module | Depends On | Can Start Immediately |
|--------|-----------|----------------------|
| **A (Camera)** | Nothing | ✅ Yes |
| **B (Gemini AI)** | Nothing | ✅ Yes |
| **C (Chat UI)** | B (models only) | ✅ Yes (use model files from B) |
| **D (Theme)** | Nothing | ✅ Yes |
| **E (Integration)** | A, B, C, D | ❌ Wait for others |

> [!TIP]
> **All four main modules can start simultaneously.** Module C only needs the data class files from Module B (which should be created first — takes 5 minutes). Module E is the final wiring step.

---

## Git Branch Strategy

```
main
├── feature/camera          ← Module A
├── feature/gemini-service  ← Module B  
├── feature/chat-ui         ← Module C
├── feature/theme           ← Module D
└── feature/integration     ← Module E (merge all into here first)
```

Each module works on its own branch. Module E merges everything together.

# 🏗️ AURA — Architecture Document

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| **Platform** | Android (Kotlin, Jetpack Compose) | Native camera + performance |
| **AI Backend** | Google Gemini API (GenAI SDK) | Required by hackathon, native multimodal |
| **Cloud** | Google Cloud (Cloud Run or Firebase) | Required by hackathon |
| **Camera** | CameraX (Jetpack) | Modern Android camera API |
| **Networking** | Retrofit / OkHttp | API calls to Gemini |
| **Image Loading** | Coil (Compose-native) | Loading recommendation images |
| **State** | Kotlin StateFlow + ViewModel | Standard Compose state management |
| **Navigation** | Jetpack Navigation Compose | Screen transitions |
| **DI** | Hilt (or manual — hackathon speed) | Dependency injection |

---

## Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                        AURA ANDROID APP                         │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────────┐ │
│  │  Camera      │  │  Chat       │  │  Recommendations         │ │
│  │  Screen      │  │  Screen     │  │  Screen / Overlay        │ │
│  │             │  │             │  │                          │ │
│  │  - Preview   │  │  - Messages │  │  - Item cards            │ │
│  │  - Capture   │  │  - Input    │  │  - Images                │ │
│  │  - Tags      │  │  - Voice?   │  │  - Descriptions          │ │
│  └──────┬──────┘  └──────┬──────┘  └───────────┬──────────────┘ │
│         │               │                     │                 │
│  ───────┴───────────────┴─────────────────────┴──────────────── │
│                      VIEWMODEL LAYER                             │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  AuraViewModel                                            │   │
│  │  - outfitState: StateFlow<OutfitAnalysis>                 │   │
│  │  - chatMessages: StateFlow<List<ChatMessage>>             │   │
│  │  - recommendations: StateFlow<List<Recommendation>>       │   │
│  │  - captureOutfit(bitmap)                                  │   │
│  │  - sendMessage(text)                                      │   │
│  │  - generateRecommendations(query)                         │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                    │
│  ───────────────────────────┴────────────────────────────────── │
│                      REPOSITORY LAYER                            │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  GeminiRepository                                         │   │
│  │  - analyzeOutfit(image: Bitmap): OutfitAnalysis           │   │
│  │  - chat(history, image, message): StylistResponse         │   │
│  │  - getRecommendations(outfit, query): List<Recommendation>│   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                    │
│  ───────────────────────────┴────────────────────────────────── │
│                      GEMINI SERVICE LAYER                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  GeminiService (Google GenAI SDK)                         │   │
│  │  - model: GenerativeModel("gemini-2.0-flash")             │   │
│  │  - generateContent(prompt, image)                         │   │
│  │  - startChat(history) → ChatSession                       │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
         │
         │  HTTPS / Google GenAI SDK
         ▼
┌──────────────────────────────────────────────────────────────────┐
│                    GOOGLE CLOUD                                   │
│  ┌─────────────────────┐  ┌───────────────────────────────────┐ │
│  │  Gemini API          │  │  (Optional) Cloud Run Backend     │ │
│  │  - Vision analysis   │  │  - Rate limiting                 │ │
│  │  - Chat completion   │  │  - API key management            │ │
│  │  - Image generation  │  │  - Outfit history (Firestore)    │ │
│  └─────────────────────┘  └───────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
com.example.aura/
├── MainActivity.kt                    # Entry point, navigation host
├── di/                                # Dependency injection
│   └── AppModule.kt                   # Hilt module (Gemini service, repos)
│
├── data/                              # Data layer
│   ├── model/                         # Data classes
│   │   ├── OutfitAnalysis.kt          # Detected items, style, colors
│   │   ├── ChatMessage.kt            # Role, content, timestamp
│   │   ├── Recommendation.kt         # Item name, description, imageUrl
│   │   └── StylistResponse.kt        # AI response with optional recs
│   │
│   ├── remote/                        # Network / AI services
│   │   └── GeminiService.kt          # Gemini API wrapper
│   │
│   └── repository/                    # Repository pattern
│       └── GeminiRepository.kt       # Orchestrates AI calls
│
├── ui/                                # Presentation layer
│   ├── theme/                         # Already exists
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   │
│   ├── navigation/                    # Navigation
│   │   └── AuraNavGraph.kt           # NavHost + routes
│   │
│   ├── camera/                        # Camera feature
│   │   ├── CameraScreen.kt           # Camera preview + capture UI
│   │   └── CameraViewModel.kt        # Camera state management
│   │
│   ├── analysis/                      # Outfit analysis display
│   │   ├── AnalysisScreen.kt         # Shows detected items + starts chat
│   │   └── AnalysisViewModel.kt      # Analysis state
│   │
│   ├── chat/                          # Stylist chat
│   │   ├── ChatScreen.kt             # Chat UI (messages + input)
│   │   ├── ChatViewModel.kt          # Chat history + Gemini calls
│   │   └── components/
│   │       ├── MessageBubble.kt      # Single message display
│   │       ├── ChatInput.kt          # Text input + send button
│   │       └── RecommendationCard.kt # Product suggestion card
│   │
│   └── components/                    # Shared components
│       ├── OutfitTagChip.kt          # "Black Jeans" chip
│       ├── LoadingAnimation.kt       # "Analyzing..." shimmer
│       └── AuraTopBar.kt             # App bar
│
└── util/                              # Utilities
    ├── BitmapUtils.kt                # Image compression/conversion
    └── PromptTemplates.kt            # Gemini system prompts
```

---

## Data Models

```kotlin
// OutfitAnalysis.kt
data class OutfitAnalysis(
    val items: List<ClothingItem>,     // "black jeans", "white shirt"
    val overallStyle: String,           // "clean casual"
    val dominantColors: List<String>,   // "#000000", "#FFFFFF"
    val summary: String                 // "I see black jeans and a white shirt..."
)

data class ClothingItem(
    val name: String,                   // "Black Jeans"
    val category: String,               // "bottoms"
    val color: String                   // "black"
)

// ChatMessage.kt
data class ChatMessage(
    val role: MessageRole,              // USER or ASSISTANT
    val content: String,
    val recommendations: List<Recommendation>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole { USER, ASSISTANT }

// Recommendation.kt
data class Recommendation(
    val itemName: String,               // "Brown Leather Tote"
    val description: String,            // "Perfect for casual outfits"
    val imageUrl: String?,              // URL or null for placeholder
    val category: String                // "bags"
)
```

---

## Key API Contracts

### Gemini Service Interface

```kotlin
interface GeminiService {

    /** Analyze an outfit image → structured clothing detection */
    suspend fun analyzeOutfit(image: Bitmap): OutfitAnalysis

    /** Send a message in the stylist conversation (with outfit context) */
    suspend fun sendStylistMessage(
        outfitImage: Bitmap,
        chatHistory: List<ChatMessage>,
        userMessage: String
    ): StylistResponse

    /** Generate recommendation images for items */
    suspend fun generateRecommendationImage(
        itemDescription: String
    ): Bitmap?
}
```

### Repository Interface

```kotlin
interface AuraRepository {
    val outfitAnalysis: StateFlow<OutfitAnalysis?>
    val chatMessages: StateFlow<List<ChatMessage>>
    val isLoading: StateFlow<Boolean>

    suspend fun analyzeOutfit(image: Bitmap)
    suspend fun sendMessage(message: String): StylistResponse
    fun clearSession()
}
```

---

## Screen Flow

```
┌──────────┐     capture     ┌──────────────┐     analyzed    ┌──────────┐
│  Camera   │ ─────────────→ │  Analyzing   │ ─────────────→ │  Chat    │
│  Screen   │                │  (Loading)    │                │  Screen  │
│           │                │              │                │          │
│  Preview  │   ← retake ←  │  Shimmer     │                │  Msgs    │
│  Capture  │                │  Animation   │                │  Input   │
│  Button   │                └──────────────┘                │  Recs    │
└──────────┘                                                 └──────────┘
```

---

## Gemini Prompt Strategy

### System Prompt (Set Once)

```
You are Aura, a friendly and knowledgeable AI fashion stylist.
You have just analyzed the user's outfit from their camera.
Be conversational, warm, and specific. Reference actual items
you can see. Give actionable styling advice.

Rules:
- Always reference the specific items the user is wearing
- Suggest complementary items with specific colors/materials
- When recommending items, format as JSON array for parsing
- Keep responses concise (2-3 sentences max for chat)
- If asked about occasion suitability, be honest but constructive
```

### Outfit Analysis Prompt

```
Analyze this outfit image. Return a JSON object with:
{
  "items": [{"name": "...", "category": "...", "color": "..."}],
  "overallStyle": "...",
  "dominantColors": ["#hex1", "#hex2"],
  "summary": "A friendly 1-sentence description of the outfit"
}
Only return the JSON, no markdown formatting.
```

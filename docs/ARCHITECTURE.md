# Aura — System Architecture

## Overview

Aura is a **two-tier AI fashion agent** for women: a Kotlin/Compose Android app (thin client) backed by a Python/ADK agent on Google Cloud Run.

```
┌──────────────────────────────────────────────────────────────┐
│                     ANDROID APP (Kotlin)                      │
│                                                              │
│  Camera → Capture → Send to Backend                          │
│  Display: Analysis + Weather + Chat + Product Recs            │
│  Local: Outfit history (SharedPrefs), Location (GPS)          │
└────────────────────────┬─────────────────────────────────────┘
                         │  HTTPS (Retrofit)
                         ▼
┌──────────────────────────────────────────────────────────────┐
│              GOOGLE CLOUD RUN (Python Backend)                │
│                                                              │
│  FastAPI + Google ADK Agent (Gemini 2.0 Flash)                │
│                                                              │
│  Agent Tools:                                                │
│  ├── get_weather(lat, lon) → OpenWeatherMap                  │
│  ├── google_search (built-in ADK grounding)                  │
│  ├── search_fashion_products(query) → product search          │
│  └── analyze_outfit_history(json) → repetition detection      │
│                                                              │
│  Endpoints:                                                  │
│  POST /analyze  — image + location → analysis + weather       │
│  POST /chat     — message + context → response + products    │
│  GET  /health   — healthcheck                                │
└──────────────────────────────────────────────────────────────┘
         │                    │                    │
    Gemini Vision      OpenWeatherMap       Google Search
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Android UI | Kotlin + Jetpack Compose + Material 3 |
| Camera | CameraX |
| Networking | Retrofit + OkHttp + Gson |
| Location | Play Services Location (FusedLocationProvider) |
| Local Storage | SharedPreferences (outfit history) |
| Backend Framework | FastAPI + Uvicorn |
| AI Agent | Google ADK (`google-adk`) |
| AI Model | Gemini 2.0 Flash (multimodal) |
| Weather | OpenWeatherMap API (free tier) |
| Product Search | Google Search grounding (ADK built-in) |
| Image Loading | Coil |
| Deployment | Google Cloud Run (Docker) |

## Package Structure

### Android (`app/src/main/java/com/example/aura/`)
```
├── MainActivity.kt
├── data/
│   ├── model/          # Domain models (OutfitAnalysis, ChatMessage, etc.)
│   ├── remote/         # Retrofit API service + DTOs
│   ├── local/          # OutfitHistoryManager
│   └── repository/     # AuraRepository (single source of truth)
├── ui/
│   ├── camera/         # CameraScreen + CameraViewModel
│   ├── analysis/       # AnalysisScreen + AnalysisViewModel
│   ├── chat/           # ChatScreen + ChatViewModel + components/
│   ├── components/     # Shared: WeatherBadge, OutfitTagChip, etc.
│   ├── navigation/     # AuraNavGraph
│   └── theme/          # Color, Theme, Type
└── util/               # BitmapUtils, LocationHelper, PromptTemplates
```

### Backend (`backend/`)
```
├── aura_agent/
│   ├── __init__.py
│   ├── agent.py        # ADK agent with tools
│   └── .env            # API keys (not committed)
├── server.py           # FastAPI endpoints
├── requirements.txt
├── Dockerfile
└── deploy.sh
```

## Data Flow

### Outfit Analysis
1. User opens app → camera preview starts
2. User taps capture → bitmap encoded to base64
3. Android sends `POST /analyze { image_base64, lat, lon }` to Cloud Run
4. Backend ADK agent:
   - Runs Gemini Vision on the image → detects clothing items
   - Calls `get_weather(lat, lon)` → gets weather context
   - Returns structured `OutfitAnalysis + Weather + greeting`
5. Android displays analysis screen with weather badge + item chips
6. Outfit saved to local history

### Stylist Chat
1. User taps "Start Styling" → navigates to chat screen
2. User types "What bag would match this?"
3. Android sends `POST /chat { message, outfit_context, outfit_history, lat, lon }`
4. Backend ADK agent:
   - Receives outfit context + history + location
   - Detects product question → uses `google_search` grounding
   - Returns response with real product recommendations (name, price, URL)
5. Android displays message bubble + horizontal recommendation cards

## API Contract

See `docs/TEAM_SKILLS.md` for the exact JSON schemas.

## Hackathon Compliance

| Requirement | How We Meet It |
|------------|---------------|
| Google Cloud hosting | Backend on Cloud Run |
| Google AI | Gemini 2.0 Flash via Google ADK |
| Live Agent | Real-time multimodal: camera → AI analysis → conversation |
| Innovation | Weather-aware styling + Google Search product recs + outfit memory |

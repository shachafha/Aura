# Team Skills & Module Ownership — Aura v2

> **2-person hackathon team** with zero file overlap. Each person can work fully independently.

---

## Person 1: Android App (You)

### Ownership
Everything under `app/src/main/java/com/example/aura/`

### Key Files to Refine
| File | What to Polish |
|------|---------------|
| `ui/camera/CameraScreen.kt` | Overlay guide, front/back toggle, flash, capture animation |
| `ui/camera/CameraViewModel.kt` | Permission handling edge cases |
| `ui/analysis/AnalysisScreen.kt` | Outfit image layout, weather badge styling, transitions |
| `ui/chat/ChatScreen.kt` | Message animations, typing indicator, voice input |
| `ui/chat/components/MessageBubble.kt` | Bubble shapes, avatar icon, timestamp |
| `ui/chat/components/RecommendationCard.kt` | Load real product images via Coil, add "Shop Now" tap |
| `ui/chat/components/ChatInput.kt` | Quick suggestion chips, mic button |
| `ui/components/WeatherBadge.kt` | Animated weather icon, more conditions |
| `ui/theme/Color.kt` | Women-focused palette refinements |
| `data/local/OutfitHistoryManager.kt` | Migrate to Room if time permits |

### Acceptance Criteria
- [ ] Camera captures cleanly with visual feedback
- [ ] Analysis screen shows outfit + weather context
- [ ] Chat works end-to-end with backend
- [ ] Recommendation cards show product images + "Shop" tap
- [ ] Location permission requested gracefully
- [ ] App doesn't crash on permission denial

---

## Person 2: Cloud Backend (Friend)

### Ownership
Everything under `backend/`

### Key Files to Refine
| File | What to Polish |
|------|---------------|
| `aura_agent/agent.py` | Tune system prompt, refine tools, add image generation |
| `server.py` | Session management, error handling, response caching |
| `Dockerfile` | Optimize image size, health check |
| `deploy.sh` | CI/CD, secret management via Secret Manager |

### Setup Instructions
```bash
# 1. Clone and navigate
git clone https://github.com/shachafha/Aura.git
cd Aura/backend

# 2. Create virtual environment
python -m venv .venv
source .venv/bin/activate

# 3. Install dependencies
pip install -r requirements.txt

# 4. Set API keys
echo "GOOGLE_GENAI_API_KEY=your-gemini-key" > aura_agent/.env
echo "OPENWEATHER_API_KEY=your-openweather-key" >> aura_agent/.env

# 5. Run locally
uvicorn server:app --port 8080 --reload

# 6. Test
curl http://localhost:8080/health
```

### Acceptance Criteria
- [ ] `/health` returns `{"status": "ok"}`
- [ ] `/analyze` accepts base64 image + lat/lon, returns structured outfit analysis + weather
- [ ] `/chat` handles multi-turn conversation with outfit context
- [ ] Google Search grounding returns real product names + URLs
- [ ] Weather tool returns correct data for given coordinates
- [ ] Outfit history tool detects repetition patterns
- [ ] Deployed on Cloud Run and accessible via public URL
- [ ] Handles errors gracefully (bad image, API timeout, etc.)

### Deploy to Cloud Run
```bash
chmod +x deploy.sh
./deploy.sh
```

---

## API Contract (Interface Between Two Workstreams)

### POST /analyze
```
Request:  { image_base64, lat, lon }
Response: { outfit_analysis, weather, greeting }
```

### POST /chat
```
Request:  { message, outfit_context, outfit_history, lat, lon }
Response: { message, recommendations[] }
```

> ⚠️ **Don't change the API contract without telling each other.** Both the Android app and backend depend on this exact JSON structure.

---

## Git Strategy
- Both work on `main` (no conflicts — zero file overlap)
- Commit frequently with descriptive messages
- Tag `v1.0-demo` when ready for hackathon presentation

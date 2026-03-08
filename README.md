# Aura — AI Fashion Stylist for Women 👗✨

> Live AI agent that helps women style outfits using their camera, weather data, and real product recommendations.

**Built for NYC Build With AI Hackathon — "Live Agents" category**

## Quick Start

### Android App
1. Clone: `git clone https://github.com/shachafha/Aura.git`
2. Open in Android Studio
3. Add to `local.properties`:
   ```
   GEMINI_API_KEY=your-key
   ```
4. Sync Gradle → Run on physical device (camera required)

### Backend (Cloud Run)
```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# Set API keys
echo "GOOGLE_GENAI_API_KEY=your-key" > aura_agent/.env
echo "OPENWEATHER_API_KEY=your-key" >> aura_agent/.env

# Run locally
uvicorn server:app --port 8080 --reload

# Deploy to Cloud Run
chmod +x deploy.sh && ./deploy.sh
```

## Architecture

```
Android App (Camera + UI) ──Retrofit──▶ Cloud Run (ADK Agent + FastAPI)
                                              │
                                    ┌─────────┼─────────┐
                                    ▼         ▼         ▼
                              Gemini 2.0   Weather   Google Search
                              (Vision)     (OpenWx)   (Products)
```

## Features
- 📸 **Live Camera** — CameraX capture with instant analysis
- 👗 **Outfit Detection** — Gemini Vision identifies clothing items, colors, style
- 🌤️ **Weather-Aware** — Styling advice based on actual weather at your location
- 🛍️ **Real Product Recs** — Google Search grounding finds actual items you can buy
- 💬 **AI Stylist Chat** — Multi-turn conversation with outfit context
- 📚 **Outfit Memory** — Remembers past outfits to suggest variety

## Tech Stack
| Component | Technology |
|-----------|-----------|
| Android | Kotlin, Jetpack Compose, CameraX, Retrofit |
| Backend | Python, FastAPI, Google ADK |
| AI | Gemini 2.0 Flash (multimodal) |
| Search | Google Search grounding (ADK) |
| Weather | OpenWeatherMap API |
| Deploy | Google Cloud Run (Docker) |

## Docs
- [Architecture](docs/ARCHITECTURE.md) — System design and data flow
- [Team Skills](docs/TEAM_SKILLS.md) — Module ownership and API contract
- [Hackathon Brief](docs/HACKATHON_BRIEF.md) — Competition context
- [Demo Script](docs/DEMO_SCRIPT.md) — 5-minute presentation guide

## Team
<!-- Add your names here -->
- Ayush Verma
- Shachaf Rispler
- Sayra Kurtoglu
- Manuel Manalo

## API Keys Needed
| Key | Where to Get | Used For |
|-----|-------------|----------|
| `GOOGLE_GENAI_API_KEY` | [aistudio.google.com](https://aistudio.google.com) | Gemini 2.0 Flash |
| `OPENWEATHER_API_KEY` | [openweathermap.org](https://openweathermap.org/api) | Weather data |

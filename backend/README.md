# Aura Backend — AI Fashion Stylist API

Python backend powered by **Google ADK + Gemini 2.0 Flash**, deployed on **Google Cloud Run**.

## Quick Start

```bash
# 1. Install dependencies
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# 2. Set API keys
cp aura_agent/.env.example aura_agent/.env
# Edit .env with your keys:
#   GOOGLE_GENAI_API_KEY=...
#   OPENWEATHER_API_KEY=...

# 3. Run locally
uvicorn server:app --port 8080 --reload

# 4. Test
curl http://localhost:8080/health
```

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Health check |
| `POST` | `/analyze` | Analyze outfit image (+ weather) |
| `POST` | `/chat` | Stylist conversation |

## Deploy to Cloud Run

```bash
chmod +x deploy.sh
./deploy.sh
```

## Agent Tools

| Tool | Source | Purpose |
|------|--------|---------|
| `get_weather` | OpenWeatherMap API | Weather-aware styling |
| `google_search` | ADK built-in | Real product search with prices/URLs |
| `search_fashion_products` | Helper | Constructs fashion-specific search queries |
| `analyze_outfit_history` | Custom | Detects repetition in past outfits |

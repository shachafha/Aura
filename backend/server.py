"""
Aura Backend — FastAPI Server

Wraps the ADK agent as a REST API for the Android client.

Endpoints:
    POST /analyze  — Analyze outfit image + weather
    POST /chat     — Stylist conversation
    GET  /health   — Health check
"""

import base64
import json
import os
import uuid
from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from google.adk.agents import Agent
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types

# Load environment from the agent's .env
load_dotenv("aura_agent/.env")

# ─── Pydantic Models ──────────────────────────────────────────────

class ClothingItem(BaseModel):
    name: str
    category: str = ""
    color: str = ""

class OutfitAnalysisResult(BaseModel):
    items: list[ClothingItem] = []
    overall_style: str = ""
    dominant_colors: list[str] = []
    summary: str = ""

class WeatherInfo(BaseModel):
    temp_f: int = 0
    condition: str = ""
    description: str = ""
    city: str = ""
    styling_note: str = ""

class Recommendation(BaseModel):
    item_name: str
    description: str = ""
    image_url: str | None = None
    shopping_url: str | None = None
    category: str = ""

class AnalyzeRequest(BaseModel):
    image_base64: str = Field(..., description="Base64-encoded JPEG image")
    lat: float = Field(0.0, description="User latitude")
    lon: float = Field(0.0, description="User longitude")

class AnalyzeResponse(BaseModel):
    outfit_analysis: OutfitAnalysisResult
    weather: WeatherInfo | None = None
    greeting: str = ""

class OutfitHistoryItem(BaseModel):
    date: str
    items: list[ClothingItem] = []
    style: str = ""

class ChatRequest(BaseModel):
    message: str
    outfit_context: OutfitAnalysisResult | None = None
    outfit_history: list[OutfitHistoryItem] = []
    lat: float = 0.0
    lon: float = 0.0

class ChatResponse(BaseModel):
    message: str
    recommendations: list[Recommendation] = []

# ─── ADK Runner Setup ─────────────────────────────────────────────

session_service = InMemorySessionService()

# Import the agent
from aura_agent.agent import root_agent

runner = Runner(
    agent=root_agent,
    app_name="aura_stylist",
    session_service=session_service,
)

# ─── Helper: Run agent and collect response ────────────────────────

async def run_agent(user_id: str, session_id: str, message: str, image_bytes: bytes | None = None):
    """Run the ADK agent and return the concatenated text response."""

    # Build content parts
    parts = []
    if image_bytes:
        parts.append(types.Part.from_bytes(data=image_bytes, mime_type="image/jpeg"))
    parts.append(types.Part.from_text(text=message))

    content = types.Content(role="user", parts=parts)

    response_text = ""
    async for event in runner.run_async(
        user_id=user_id,
        session_id=session_id,
        new_message=content,
    ):
        if event.is_final_response():
            for part in event.content.parts:
                if part.text:
                    response_text += part.text

    return response_text

# ─── FastAPI App ───────────────────────────────────────────────────

app = FastAPI(
    title="Aura — AI Fashion Stylist API",
    description="Backend for the Aura Android app. Powered by Google ADK + Gemini.",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
async def health():
    """Health check endpoint."""
    return {"status": "ok", "agent": "aura_stylist"}


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze_outfit(request: AnalyzeRequest):
    """
    Analyze an outfit image. Optionally includes weather context.
    """
    try:
        image_bytes = base64.b64decode(request.image_base64)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid base64 image")

    user_id = f"user_{uuid.uuid4().hex[:8]}"
    session_id = f"session_{uuid.uuid4().hex[:8]}"

    # Create a session
    session_service.create_session(
        app_name="aura_stylist",
        user_id=user_id,
        session_id=session_id,
    )

    # Build the analysis prompt
    prompt_parts = [
        "Analyze this outfit image. Identify each clothing item, color, and style category.",
        "Give a warm, encouraging summary of the outfit as a women's fashion stylist.",
    ]

    if request.lat != 0 and request.lon != 0:
        prompt_parts.append(
            f"Also check the weather at coordinates ({request.lat}, {request.lon}) "
            "and mention if the outfit is appropriate for today's weather."
        )

    prompt_parts.append(
        "\nRespond in this exact JSON format (no markdown, no code fences):\n"
        '{"items": [{"name": "...", "category": "tops|bottoms|shoes|outerwear|accessories", "color": "..."}], '
        '"overall_style": "...", "dominant_colors": ["#hex1"], '
        '"summary": "A warm 1-2 sentence description", '
        '"weather": {"temp_f": 72, "condition": "Sunny", "styling_note": "..."}, '
        '"greeting": "Your personalized greeting"}'
    )

    prompt = "\n".join(prompt_parts)

    response_text = await run_agent(user_id, session_id, prompt, image_bytes)

    # Parse the response
    try:
        # Strip markdown code fences if present
        cleaned = response_text.strip()
        cleaned = cleaned.removeprefix("```json").removeprefix("```").removesuffix("```").strip()
        data = json.loads(cleaned)

        outfit = OutfitAnalysisResult(
            items=[ClothingItem(**item) for item in data.get("items", [])],
            overall_style=data.get("overall_style", ""),
            dominant_colors=data.get("dominant_colors", []),
            summary=data.get("summary", response_text[:200])
        )

        weather = None
        if "weather" in data and data["weather"]:
            w = data["weather"]
            weather = WeatherInfo(
                temp_f=w.get("temp_f", 0),
                condition=w.get("condition", ""),
                description=w.get("description", ""),
                city=w.get("city", ""),
                styling_note=w.get("styling_note", "")
            )

        return AnalyzeResponse(
            outfit_analysis=outfit,
            weather=weather,
            greeting=data.get("greeting", outfit.summary)
        )
    except (json.JSONDecodeError, Exception):
        # Fallback: return raw text as summary
        return AnalyzeResponse(
            outfit_analysis=OutfitAnalysisResult(summary=response_text[:500]),
            greeting=response_text[:200]
        )


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """
    Send a message to the Aura stylist. Maintains conversation context.
    """
    user_id = f"user_{uuid.uuid4().hex[:8]}"
    session_id = f"session_{uuid.uuid4().hex[:8]}"

    session_service.create_session(
        app_name="aura_stylist",
        user_id=user_id,
        session_id=session_id,
    )

    # Build context-rich prompt
    context_parts = []

    if request.outfit_context:
        items_str = ", ".join([f"{i.color} {i.name}" for i in request.outfit_context.items])
        context_parts.append(
            f"The user is currently wearing: {items_str}. "
            f"Overall style: {request.outfit_context.overall_style}."
        )

    if request.outfit_history:
        history_json = json.dumps([h.model_dump() for h in request.outfit_history])
        context_parts.append(
            f"Recent outfit history: {history_json}"
        )

    if request.lat != 0 and request.lon != 0:
        context_parts.append(
            f"User's location coordinates: ({request.lat}, {request.lon}). "
            "Check weather if relevant to the styling question."
        )

    context = "\n".join(context_parts)
    full_message = f"{context}\n\nUser's question: {request.message}" if context else request.message

    # Check if this is a product recommendation request
    product_keywords = ["bag", "shoe", "boot", "jacket", "accessory", "jewelry",
                        "buy", "recommend", "suggest", "where can i", "shopping",
                        "necklace", "earring", "scarf", "belt", "watch", "sunglasses"]
    is_product_query = any(kw in request.message.lower() for kw in product_keywords)

    if is_product_query:
        full_message += (
            "\n\n[SYSTEM: The user is asking about products. Use google_search to find "
            "real, purchasable women's fashion items. Include brand name, approximate "
            "price, and a shopping URL. Return 3-4 options. Format product recommendations "
            "at the end of your response in JSON within <recommendations> tags:\n"
            '<recommendations>[{"item_name":"...","description":"...","shopping_url":"...","category":"..."}]</recommendations>]'
        )

    response_text = await run_agent(user_id, session_id, full_message)

    # Parse recommendations if present
    recommendations = []
    import re
    recs_match = re.search(r"<recommendations>(.*?)</recommendations>", response_text, re.DOTALL)
    if recs_match:
        try:
            recs_data = json.loads(recs_match.group(1).strip())
            recommendations = [Recommendation(**r) for r in recs_data]
        except (json.JSONDecodeError, Exception):
            pass

    # Clean message text
    clean_message = re.sub(r"<recommendations>.*?</recommendations>", "", response_text, flags=re.DOTALL).strip()

    return ChatResponse(
        message=clean_message,
        recommendations=recommendations
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)

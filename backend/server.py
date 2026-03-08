"""
Aura Backend — FastAPI Server

Wraps the ADK agent as a REST API + WebSocket streaming for the Android client.

Endpoints:
    POST /analyze  — Analyze outfit image + weather (REST)
    POST /chat     — Stylist conversation (REST)
    GET  /health   — Health check
    WS   /ws/{session_id} — Gemini Live API bidi-streaming (WebSocket)
"""

import asyncio
import base64
import json
import logging
import os
import uuid

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from google.adk.agents import Agent
from google.adk.agents.live_request_queue import LiveRequestQueue
from google.adk.agents.run_config import RunConfig, StreamingMode
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types

# Load environment from the agent's .env
load_dotenv("aura_agent/.env")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("aura")

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

APP_NAME = "aura_stylist"
session_service = InMemorySessionService()

# Import agents
from aura_agent.agent import root_agent, live_agent

# REST runner (for /analyze and /chat endpoints)
runner = Runner(
    agent=root_agent,
    app_name=APP_NAME,
    session_service=session_service,
)

# Live streaming runner (for /ws WebSocket endpoint)
live_runner = Runner(
    agent=live_agent,
    app_name=APP_NAME,
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
    version="2.0.0"
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
    return {
        "status": "ok",
        "agent": "aura_stylist",
        "live_agent": "aura_live_stylist",
        "live_model": live_agent.model,
    }


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
        app_name=APP_NAME,
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
        app_name=APP_NAME,
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


# ─── WebSocket: Gemini Live API Bidi-Streaming ─────────────────────

@app.websocket("/ws/{session_id}")
async def websocket_endpoint(websocket: WebSocket, session_id: str) -> None:
    """
    Bidirectional streaming endpoint using ADK's Gemini Live API Toolkit.

    Follows the official ADK bidi-streaming pattern from:
    - https://google.github.io/adk-docs/streaming/dev-guide/part1/
    - https://codelabs.developers.google.com/way-back-home-level-3

    The client sends JSON messages over WebSocket with types:
      {"type": "text", "text": "..."}         — text message
      {"type": "audio", "data": "base64..."}  — PCM 16kHz audio
      {"type": "image", "data": "base64...", "mimeType": "image/jpeg"} — camera frame

    The server streams ADK events back as JSON.
    """
    await websocket.accept()
    logger.info(f"WebSocket connected: session={session_id}")

    user_id = f"user_{session_id}"

    # ========================================
    # Phase 2: Session Initialization
    # ========================================

    # Detect model capabilities for RunConfig
    model_name = live_agent.model
    is_native_audio = "native-audio" in model_name.lower() or "live" in model_name.lower()

    if is_native_audio:
        response_modalities = ["AUDIO"]
        run_config = RunConfig(
            streaming_mode=StreamingMode.BIDI,
            response_modalities=response_modalities,
            input_audio_transcription=types.AudioTranscriptionConfig(),
            output_audio_transcription=types.AudioTranscriptionConfig(),
            session_resumption=types.SessionResumptionConfig(),
        )
        logger.info(f"Model Config: {model_name} (native audio, BIDI mode)")
    else:
        response_modalities = ["TEXT"]
        run_config = RunConfig(
            streaming_mode=StreamingMode.BIDI,
            response_modalities=response_modalities,
        )
        logger.info(f"Model Config: {model_name} (text mode, BIDI)")

    # Get or create ADK session (persistent across reconnections)
    session = await session_service.get_session(
        app_name=APP_NAME,
        user_id=user_id,
        session_id=session_id,
    )
    if not session:
        await session_service.create_session(
            app_name=APP_NAME,
            user_id=user_id,
            session_id=session_id,
        )
        logger.info(f"Created new session: {session_id}")
    else:
        logger.info(f"Resuming session: {session_id}")

    # ========================================
    # Phase 3: Active Session (concurrent bidirectional communication)
    # ========================================

    live_request_queue = LiveRequestQueue()

    # Send initial greeting stimulus to wake up the model
    logger.info("Sending initial 'Hello' stimulus to model...")
    live_request_queue.send_content(
        types.Content(parts=[types.Part(text="Hello")])
    )

    async def upstream_task() -> None:
        """Receives messages from WebSocket → sends to LiveRequestQueue."""
        try:
            while True:
                message = await websocket.receive()

                # Handle binary frames (raw audio bytes)
                if "bytes" in message:
                    audio_data = message["bytes"]
                    audio_blob = types.Blob(
                        mime_type="audio/pcm;rate=16000",
                        data=audio_data,
                    )
                    live_request_queue.send_realtime(audio_blob)

                # Handle text frames (JSON messages)
                elif "text" in message:
                    text_data = message["text"]
                    json_message = json.loads(text_data)

                    msg_type = json_message.get("type", "")

                    if msg_type == "text":
                        # Text chat message
                        user_text = json_message.get("text", "")
                        logger.info(f"User text: {user_text}")
                        content = types.Content(
                            parts=[types.Part(text=user_text)]
                        )
                        live_request_queue.send_content(content)

                    elif msg_type == "audio":
                        # Base64-encoded PCM audio
                        audio_data = base64.b64decode(
                            json_message.get("data", "")
                        )
                        audio_blob = types.Blob(
                            mime_type="audio/pcm;rate=16000",
                            data=audio_data,
                        )
                        live_request_queue.send_realtime(audio_blob)

                    elif msg_type == "image":
                        # Base64-encoded image (camera frame)
                        image_data = base64.b64decode(
                            json_message.get("data", "")
                        )
                        mime_type = json_message.get("mimeType", "image/jpeg")
                        image_blob = types.Blob(
                            mime_type=mime_type,
                            data=image_data,
                        )
                        live_request_queue.send_realtime(image_blob)
                        logger.info("Received image frame")

                    else:
                        logger.warning(f"Unknown message type: {msg_type}")

        except WebSocketDisconnect:
            logger.info("Client disconnected (upstream)")
        except Exception as e:
            logger.error(f"Upstream error: {e}")

    async def downstream_task() -> None:
        """Receives Events from run_live() → sends to WebSocket."""
        logger.info("Connecting to Gemini Live API...")
        async for event in live_runner.run_live(
            user_id=user_id,
            session_id=session_id,
            live_request_queue=live_request_queue,
            run_config=run_config,
        ):
            # Log tool calls
            if hasattr(event, "tool_call") and event.tool_call:
                details = str(event.tool_call.function_calls)
                logger.info(f"[TOOL EXECUTION] {details}")

            # Log input transcription (what the user said via audio)
            input_transcription = getattr(event, "input_audio_transcription", None)
            if input_transcription and input_transcription.final_transcript:
                logger.info(f"USER (audio): {input_transcription.final_transcript}")

            # Log output transcription (what the agent said)
            output_transcription = getattr(event, "output_audio_transcription", None)
            if output_transcription and output_transcription.final_transcript:
                logger.info(f"AURA: {output_transcription.final_transcript}")

            # Send event to client as JSON
            event_json = event.model_dump_json(
                exclude_none=True, by_alias=True
            )
            await websocket.send_text(event_json)

        logger.info("Gemini Live API connection closed.")

    # Run both tasks concurrently (full-duplex)
    try:
        await asyncio.gather(upstream_task(), downstream_task())
    except WebSocketDisconnect:
        logger.info("Client disconnected")
    except Exception as e:
        logger.error(f"Session error: {e}", exc_info=False)
    finally:
        # ========================================
        # Phase 4: Session Termination
        # ========================================
        logger.info("Closing live_request_queue")
        live_request_queue.close()


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)

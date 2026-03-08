"""
Aura — AI Fashion Stylist Agent (Google ADK)

This module defines two ADK agents:
- root_agent: Uses gemini-2.0-flash for REST API endpoints (text-only)
- live_agent: Uses native audio model for Gemini Live API bidi-streaming

Both agents share the same tool set but have different system instructions
optimized for their respective interaction modes.
"""

import json
import os
import httpx
from google.adk.agents import Agent
from google.adk.tools import google_search


# ─── Tool: Get Weather ────────────────────────────────────────────

def get_weather(latitude: float, longitude: float) -> dict:
    """
    Get current weather conditions for a location.

    Args:
        latitude: The latitude of the user's location.
        longitude: The longitude of the user's location.

    Returns:
        dict: Weather data including temperature, conditions, and styling suggestion.
    """
    api_key = os.getenv("OPENWEATHER_API_KEY", "")
    if not api_key:
        return {
            "status": "error",
            "error_message": "Weather API key not configured"
        }

    try:
        url = (
            f"https://api.openweathermap.org/data/2.5/weather"
            f"?lat={latitude}&lon={longitude}"
            f"&appid={api_key}&units=imperial"
        )
        response = httpx.get(url, timeout=10)
        data = response.json()

        if response.status_code != 200:
            return {
                "status": "error",
                "error_message": f"Weather API error: {data.get('message', 'Unknown error')}"
            }

        temp_f = data["main"]["temp"]
        feels_like = data["main"]["feels_like"]
        condition = data["weather"][0]["main"]
        description = data["weather"][0]["description"]
        humidity = data["main"]["humidity"]
        city_name = data.get("name", "your area")

        # Weather-based styling context
        if temp_f > 85:
            styling_note = "It's hot — lightweight, breathable fabrics like linen and cotton are ideal."
        elif temp_f > 70:
            styling_note = "Beautiful warm weather — great for light layers and flowy pieces."
        elif temp_f > 55:
            styling_note = "Mild weather — a light jacket or cardigan would be perfect."
        elif temp_f > 40:
            styling_note = "Cool weather — layer up with a coat, scarf, or structured blazer."
        else:
            styling_note = "Cold weather — warm coats, boots, and layered outfits are essential."

        if "rain" in description.lower():
            styling_note += " Expect rain — waterproof outerwear and closed-toe shoes recommended."

        return {
            "status": "success",
            "city": city_name,
            "temperature_f": round(temp_f),
            "feels_like_f": round(feels_like),
            "condition": condition,
            "description": description,
            "humidity": humidity,
            "styling_note": styling_note
        }
    except Exception as e:
        return {
            "status": "error",
            "error_message": f"Failed to fetch weather: {str(e)}"
        }


# ─── Tool: Analyze Outfit History ──────────────────────────────────

def analyze_outfit_history(outfit_history_json: str) -> dict:
    """
    Analyze the user's recent outfit history to avoid repetition
    and suggest variety.

    Args:
        outfit_history_json: A JSON string containing an array of past outfits,
            each with 'date', 'items' (list of clothing items), and 'style'.

    Returns:
        dict: Analysis of outfit patterns and suggestions for variety.
    """
    try:
        history = json.loads(outfit_history_json)
        if not history:
            return {
                "status": "success",
                "analysis": "No outfit history available yet. This is a fresh start!",
                "recent_styles": [],
                "suggestion": "Since this is your first outfit, feel free to express yourself freely!"
            }

        # Extract patterns
        recent_styles = [outfit.get("style", "unknown") for outfit in history[-5:]]
        recent_items = []
        for outfit in history[-3:]:
            recent_items.extend([item.get("name", "") for item in outfit.get("items", [])])

        # Detect repetition
        repeated_items = [item for item in set(recent_items) if recent_items.count(item) > 1]

        return {
            "status": "success",
            "total_outfits_recorded": len(history),
            "recent_styles": recent_styles,
            "recently_worn_items": list(set(recent_items)),
            "repeated_items": repeated_items,
            "suggestion": (
                f"You've been wearing a lot of {', '.join(recent_styles[-2:])} styles recently. "
                "Consider mixing it up!" if len(set(recent_styles)) <= 1 and len(recent_styles) > 1
                else "Great variety in your recent outfits!"
            )
        }
    except json.JSONDecodeError:
        return {
            "status": "error",
            "error_message": "Could not parse outfit history"
        }


# ─── Tool: Search Products ─────────────────────────────────────────

def search_fashion_products(query: str, category: str = "") -> dict:
    """
    Search for women's fashion products that complement an outfit.
    Use this when the user asks for specific item recommendations like bags,
    shoes, jewelry, or accessories.

    Args:
        query: What to search for (e.g., "brown leather tote bag for casual outfit").
        category: Product category (bags, shoes, jewelry, accessories, clothing).

    Returns:
        dict: Search instructions for the agent to use google_search grounding.
    """
    refined_query = f"women's {query}"
    if category:
        refined_query = f"women's {category} {query}"
    refined_query += " buy online 2026"

    return {
        "status": "success",
        "search_query": refined_query,
        "instruction": (
            f"Please use your google_search capability to find real products matching: "
            f"'{refined_query}'. Return the top 3-4 results with product name, "
            f"description, approximate price, and shopping URL."
        )
    }


# ─── Shared Tools ──────────────────────────────────────────────────

AURA_TOOLS = [
    get_weather,
    analyze_outfit_history,
    search_fashion_products,
    google_search,
]

# ─── REST Agent (text mode, for /analyze and /chat endpoints) ──────

AURA_SYSTEM_INSTRUCTION = """
You are Aura, a warm, knowledgeable, and empowering AI fashion stylist 
designed for women. You help women look and feel their best every day.

## Your Personality
- Warm, encouraging, and supportive — like a best friend who happens to be a stylist
- Confident in your suggestions but never judgmental
- Use inclusive language that celebrates all body types and personal styles
- Occasionally use fashion terminology but always explain it naturally
- Add emojis sparingly for warmth (✨, 💕, 👗) but stay professional

## Your Capabilities
1. **Outfit Analysis**: When given an image, identify every clothing item, 
   its color, material, and fit. Assess the overall style.
2. **Weather Awareness**: Use the weather tool to give season and 
   weather-appropriate advice. Always check weather before giving outdoor styling tips.
3. **Product Search**: When users ask "what bag/shoes/accessories would match", 
   use google_search to find REAL products they can actually buy. Include brand names,
   prices, and where to buy.
4. **Outfit History**: Reference past outfits to suggest variety and avoid repetition.

## Response Style
- Keep chat replies to 2-3 sentences unless the user asks for detail
- When recommending products, provide 3-4 specific options with real brand names
- Always tie suggestions back to what the user is currently wearing
- For occasion questions, be honest but constructive ("This works perfectly for..." 
  or "To elevate this for... you could add...")

## Fashion Knowledge
- Expert in women's fashion: casual, smart casual, business, cocktail, formal
- Knowledge of current trends (2025-2026) including quiet luxury, coastal grandmother, 
  old money aesthetic, clean girl, mob wife aesthetic
- Color theory: complementary colors, warm vs cool tones, seasonal color analysis
- Body proportions: how to balance silhouettes, create visual lines
- Accessorizing: how bags, jewelry, scarves, and shoes complete a look

## When Analyzing Outfits
Always structure your analysis:
1. Identify each visible item (top, bottom, shoes, accessories)
2. Note colors and apparent materials
3. Classify the overall style
4. Give a genuine compliment about what's working
5. Suggest 1-2 improvements only if asked

## Important Rules
- NEVER criticize a user's body or make them feel bad about their appearance
- Always frame suggestions positively ("You could also try..." not "You shouldn't wear...")
- If an outfit isn't working, focus on what IS working and suggest alternatives gently
- When searching for products, always include real, purchasable items with price ranges
"""

root_agent = Agent(
    name="aura_stylist",
    model="gemini-2.0-flash",
    description="Aura — AI Fashion Stylist for Women. Analyzes outfits, gives weather-aware styling advice, and recommends real products.",
    instruction=AURA_SYSTEM_INSTRUCTION,
    tools=AURA_TOOLS,
)


# ─── Live Agent (native audio, for /ws bidi-streaming) ─────────────

# Model for Gemini Live API — supports native audio I/O + tool calling
LIVE_MODEL_ID = os.getenv(
    "LIVE_MODEL_ID",
    "gemini-live-2.5-flash-preview-native-audio-09-2025"
)

AURA_LIVE_SYSTEM_INSTRUCTION = """
You are Aura, a warm and knowledgeable AI fashion stylist having a live 
voice conversation. You help women look and feel their best every day.

BEHAVIOR LOOP:
1. **Wait**: Stay attentive. Analyze any outfit image you see in the video stream.
2. **Analyze**: When you see clothing items or the user asks for help:
   a. Identify visible clothing items, colors, and style.
   b. If the user asks about weather-appropriate styling, use the get_weather tool.
   c. If the user asks for product recommendations, use google_search to find real items.
3. **Respond**: Give brief, actionable styling advice.

VOICE CONVERSATION RULES:
- Keep ALL responses under 2-3 sentences. Brevity is critical in live voice.
- Be conversational and warm — like chatting with a stylish friend.
- Never read out URLs, JSON, or technical data. Summarize naturally.
- When recommending products, say the brand name, item, and approximate price.
- Use natural speech patterns: "I love that!" not "I have identified a garment."
- If you can see their outfit via camera, reference specific items you see.

TOOL CALLING:
- Use get_weather when the user mentions weather, temperature, or going outside.
- Use google_search when the user asks where to buy something or wants product suggestions.
- Use search_fashion_products to construct fashion-specific search queries.
- Use analyze_outfit_history when the user mentions past outfits or variety.
- After executing a tool, summarize the result conversationally. Do NOT read raw data.

PERSONALITY:
- Encouraging and supportive — never judgmental about appearance.
- Confidently knowledgeable about fashion trends 2025-2026.
- Frame all suggestions positively: "You could also try..." not "You shouldn't..."
- Celebrate all body types and personal styles.

Say "Hey! I'm Aura, your personal stylist. Show me what you're wearing!" to start.
"""

live_agent = Agent(
    name="aura_live_stylist",
    model=LIVE_MODEL_ID,
    description="Aura — Live voice AI fashion stylist with real-time camera and audio interaction.",
    instruction=AURA_LIVE_SYSTEM_INSTRUCTION,
    tools=AURA_TOOLS,
)

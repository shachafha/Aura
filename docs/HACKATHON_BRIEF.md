# 🏆 AURA — NYC Build With AI Hackathon Brief

## Hackathon Context

| Detail | Value |
|---|---|
| **Event** | NYC Build With AI Hackathon (Google Developer Groups) |
| **Category** | **Live Agents** — Real-time Interaction (Audio/Vision) |
| **Required Tech** | Google GenAI SDK / Agent Development Kit (ADK), Google Cloud hosting |
| **Format** | 5–8 min demo per team → top 6 finalists → grand finale |

### Judging Criteria (What Wins)

| Criterion | What They're Looking For |
|---|---|
| **Innovation & UX** | Breaks the "text box" paradigm. Natural, immersive interaction |
| **Technical Implementation** | Effective GenAI SDK/ADK usage, robust Cloud backend, grounding, error handling |
| **Demo & Presentation** | Clear problem → solution story. Memorable demo moment |

### ⛔ Strictly Prohibited

- AI Mental Health / Medical Advisors
- Basic RAG apps ("Chat with my PDF")
- **Basic Image Analyzers** ← we must go beyond this
- Standard Education Chatbots
- Anything "Text-In, Text-Out" only

> [!CAUTION]
> Aura MUST be a **Live Agent** with real-time multimodal interaction (camera + voice/chat), NOT a basic image analyzer. The conversation + visual understanding combination is what makes it eligible.

---

## What Is Aura?

**Aura is a live AI fashion stylist agent** that sees your outfit through the camera and talks to you like a personal stylist in real time.

### The Demo Moment (30 seconds that win)

```
1. User opens Aura → camera activates
2. Points at their outfit
3. Aura: "I see black jeans and a white shirt — clean casual look ✨"
4. User asks: "What bag would match this?"
5. Aura: "A brown leather tote or black crossbody would work perfectly"
6. Recommendation cards appear on screen
```

### Why This Wins Under Live Agents

- ✅ **Real-time vision** — camera analyzing outfit live
- ✅ **Conversational agent** — not a one-shot analyzer, an ongoing stylist
- ✅ **Multimodal** — vision input + text/voice output + image recommendations
- ✅ **Breaks text-box paradigm** — camera + chat + cards in one fluid experience
- ✅ **Uses Gemini** — native multimodal understanding, perfect fit for GenAI SDK

---

## Core Features (Must Build)

### 1. 📸 Live Outfit Capture
- Camera preview with capture button
- Send image to Gemini for analysis
- Display animated "analyzing..." state
- Show detected items as tags/chips

### 2. 🤖 Live AI Stylist Chat
- Chat interface overlaid on/below the camera
- Gemini maintains conversation context + captured outfit image
- Responds as a fashion stylist persona
- Voice input option (stretch)

### 3. 🛍️ Recommendation Cards
- When Aura suggests items, show 3 visual recommendation cards
- Cards with item name + image + brief description
- Can use Gemini image generation or placeholder images

---

## Stretch Features (If Time Permits)

| Feature | Description |
|---|---|
| **Outfit Memory** | Save past outfits, AI references them ("You wore this yesterday") |
| **Style Awareness** | AI labels style: "minimal casual", "smart casual", etc. |
| **Shopping Links** | Show real product suggestions with web links |
| **Voice I/O** | Full voice conversation with the stylist |
| **Occasion Mode** | "Dress me for: job interview / date / brunch" |

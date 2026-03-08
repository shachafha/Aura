# 🎬 AURA — Demo Script & Presentation Strategy

## The 5-Minute Demo Structure

> [!IMPORTANT]
> Judges look for: **Innovation & UX**, **Technical Implementation**, **Demo & Presentation**.
> The demo is THE most important part. Practice this flow.

---

## Opening (30 seconds)

**Speaker says:**

> "Every morning, millions of people stand in front of their closet and ask the same question: *Does this look good?*
> 
> What if you had a personal stylist in your pocket who could see your outfit and give you real-time advice?
> 
> That's Aura — a live AI fashion agent powered by Gemini."

---

## Live Demo (3 minutes)

### Beat 1: Camera Capture (30s)
1. Open the app → camera activates
2. Point at someone's outfit
3. Tap capture

**Say:** "Aura uses the camera to see exactly what you're wearing."

### Beat 2: AI Analysis (30s)
1. Shimmer animation plays ("Analyzing your outfit...")
2. Results appear: detected items as tags
3. "I see black jeans, a white button-up, and white sneakers. Clean casual look."

**Say:** "Gemini's multimodal vision identifies each piece — not just 'clothes,' but specific items, colors, and the overall style."

### Beat 3: Stylist Conversation (60s)
1. Tap "Start Styling" → chat opens
2. Type: "What bag would match this?"
3. Aura responds with suggestions
4. Recommendation cards appear

**Say:** "Now you're talking to a stylist who actually *understands* what you're wearing. This isn't a text chatbot — it's a visual agent."

5. Ask: "Is this outfit good for a job interview?"
6. Aura responds with honest, constructive advice

**Say:** "The conversation is contextual. Aura remembers what it saw and gives specific, actionable advice."

### Beat 4: Recommendations (30s)
1. Show recommendation cards
2. Scroll through options

**Say:** "When Aura suggests items, you see them as cards. In production, these link to real products."

---

## Technical Deep Dive (1 minute)

**Say (with slide or live view):**

> "Under the hood, Aura uses:
> - **Gemini 2.0 Flash** for multimodal analysis — it processes the image and maintains conversation context simultaneously
> - **Google GenAI SDK** for native Android integration
> - **CameraX** for real-time capture
> - **Jetpack Compose** for a modern, responsive UI
> 
> The key technical challenge was making the AI feel like a *live agent*, not a one-shot analyzer. We achieve this by maintaining a persistent chat session with the outfit image as context, so every response is grounded in what the user is actually wearing."

---

## Closing (30 seconds)

**Say:**

> "Aura breaks the text-box paradigm. It's not 'upload a photo and get a response.' It's a live, conversational stylist that sees and understands your outfit in real time.
> 
> Future features include outfit memory, voice interaction, and shopping integration. But the core insight is simple: AI should be able to see and talk, not just read and write.
> 
> Thank you."

---

## Slides (if needed)

| Slide | Content |
|-------|---------|
| 1 | App name + tagline: "Your AI Stylist, Live" |
| 2 | Problem: "Getting dressed shouldn't need a second opinion" |
| 3 | LIVE DEMO (switch to phone) |
| 4 | Architecture diagram (from ARCHITECTURE.md) |
| 5 | "Built with Gemini + Google Cloud" (hackathon logos) |
| 6 | Future: voice, memory, shopping |
| 7 | Team + thank you |

---

## Demo Backup Plan

If live demo fails:
1. Have a **screen recording** ready (record during testing)
2. Have **screenshots** of each screen in the `docs/demo/` folder
3. Can walk through the code and architecture if camera doesn't work

> [!WARNING]
> **Always test the demo on the physical device 30 minutes before presenting.** Camera apps do NOT work on emulators. Make sure the Gemini API key is valid and not rate-limited.

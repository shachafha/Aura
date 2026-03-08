# 🤖 AURA — AI Agent Workflow Guide

> How to use AI coding agents (Antigravity, Claude Code, Gemini CLI) to maximize output during the hackathon. Based on Ralph Wiggum, Ralph Orchestrator, and GSD patterns.

---

## Philosophy: Why Agent Workflows Matter at a Hackathon

You have **one day**. The bottleneck is not typing speed — it's context switching, debugging, and waiting. AI agents eliminate all three if you structure work correctly.

The core insight from all three frameworks (Ralph Wiggum, Ralph Orchestrator, GSD):

> **Give the agent a clear spec, let it iterate, verify automatically.**

---

## Strategy: Spec-Driven Parallel Agent Execution

### The Pattern

```
┌──────────────────────────────────────────────────────────┐
│                    YOU (Human Operator)                    │
│                                                          │
│  1. Write specs (this doc!)                              │
│  2. Assign each module to an agent                       │
│  3. Monitor progress                                     │
│  4. Handle integration                                   │
└────────┬──────────┬──────────┬──────────┬───────────────┘
         │          │          │          │
         ▼          ▼          ▼          ▼
    ┌─────────┐┌─────────┐┌─────────┐┌─────────┐
    │ Agent 1 ││ Agent 2 ││ Agent 3 ││ Agent 4 │
    │ Camera  ││ Gemini  ││ Chat UI ││ Theme   │
    │ Module  ││ Service ││         ││ Polish  │
    └─────────┘└─────────┘└─────────┘└─────────┘
```

### What Makes This Work

1. **Isolation** — Each module has its own files, no merge conflicts
2. **Clear contracts** — Interfaces defined upfront (see TEAM_SKILLS.md)
3. **Self-verification** — Each agent can build & check its own work
4. **Iterative loops** — Agent keeps going until acceptance criteria pass

---

## Agent Prompting Templates

### Template 1: Module Implementation (Ralph-style Loop)

Use this when assigning a module to an agent. This prompt is designed for iterative, self-correcting work:

```markdown
## Task: Implement [MODULE NAME] for Aura

### Context
You are building a module for Aura, an AI fashion stylist Android app.
Read these files for full context:
- docs/ARCHITECTURE.md — overall architecture
- docs/TEAM_SKILLS.md — your specific module spec

### Your Module: [MODULE A/B/C/D]
[Copy the module spec from TEAM_SKILLS.md]

### Files You Own
[List exact file paths]

### Rules
1. Use Kotlin + Jetpack Compose
2. Follow the package structure in ARCHITECTURE.md
3. Do NOT modify files outside your module
4. Use the exact interface contracts specified
5. All functions must have KDoc comments
6. Handle errors gracefully (try/catch, Result type)

### Acceptance Criteria
[Copy from TEAM_SKILLS.md]

### Verification
After implementing, verify:
1. Code compiles without errors
2. All acceptance criteria are met
3. No TODO or placeholder code remains
4. Error states are handled

When complete, output: COMPLETE
```

### Template 2: Integration Wiring

```markdown
## Task: Wire Aura Modules Together

### Context
All four modules (Camera, Gemini, Chat UI, Theme) are implemented.
Read docs/ARCHITECTURE.md for the full architecture.
Read docs/TEAM_SKILLS.md Module E for integration spec.

### What to Do
1. Create AuraNavGraph.kt with Camera → Analysis → Chat navigation
2. Create shared AuraViewModel that connects:
   - Camera capture → Gemini analysis
   - Gemini analysis → Analysis screen display
   - Chat input → Gemini conversation → Chat display
3. Update MainActivity.kt to use the nav graph
4. Add API key configuration
5. Test the full flow end-to-end

### Verification
- App launches to camera screen
- Capture navigates to analysis
- Analysis shows detected items
- Chat allows conversation about the outfit
- Recommendations display as cards

When complete, output: COMPLETE
```

### Template 3: Bug Fix / Polish Loop

```markdown
## Task: Fix and Polish

### Context
Aura is mostly working but has issues.

### Issues to Fix
[List specific bugs or UX issues]

### Approach  
1. Reproduce each issue
2. Identify root cause
3. Fix
4. Verify the fix
5. Move to next issue

### When to Stop
All listed issues are fixed and verified.

When complete, output: COMPLETE
```

---

## Maximizing Agent Output: Practical Tips

### 1. Front-load the Data Models

Before agents start on their modules, create the shared data model files first:

```
data/model/OutfitAnalysis.kt
data/model/ChatMessage.kt
data/model/Recommendation.kt
data/model/StylistResponse.kt
```

This takes 5 minutes and unblocks Module C from depending on Module B.

### 2. Use Feature Branches

Each agent works on its own branch. This prevents conflicts:

```bash
git checkout -b feature/camera
git checkout -b feature/gemini-service
git checkout -b feature/chat-ui
git checkout -b feature/theme
```

### 3. Define Clear "Done" Signals

From Ralph Wiggum: agents need **completion promises** — a clear, unambiguous signal that work is done. The acceptance criteria in TEAM_SKILLS.md serve this purpose.

### 4. Iterate, Don't Restart

From GSD: if an agent makes a mistake, don't restart from scratch. Feed the error back and let it fix. This is faster than re-explaining context.

### 5. Verify Continuously

After each module, run:
```bash
./gradlew assembleDebug
```
If it fails, feed the error to the agent immediately.

---

## Timeline Strategy

```
HOUR 0-1: Setup & Planning
├── Set up repo, branches
├── Create data models (shared)
├── Distribute module specs to agents
└── Start all 4 modules in parallel

HOUR 1-3: Parallel Module Development
├── Agent 1: Camera module
├── Agent 2: Gemini service + prompts
├── Agent 3: Chat UI screens
└── Agent 4: Theme + branding

HOUR 3-4: Integration
├── Merge all branches
├── Wire navigation
├── Connect ViewModel
└── First end-to-end test

HOUR 4-5: Polish & Demo Prep
├── Fix bugs
├── Smooth animations
├── Prepare demo script
└── Practice 5-min presentation

HOUR 5+: Demo Enhancements (if time)
├── Voice input
├── Outfit memory
└── Shopping links
```

---

## Google Cloud Deployment (Required by Hackathon)

The hackathon **requires** projects be hosted on Google Cloud. For a client-side Android app, this means:

### Option 1: API Key via Cloud (Minimal)
- Store Gemini API key in **Google Cloud Secret Manager**
- App calls Gemini directly with API key
- This is sufficient for the demo

### Option 2: Backend Proxy on Cloud Run (Better)
- Deploy a simple API proxy on **Cloud Run**
- Proxy holds the API key server-side
- Android app calls your Cloud Run endpoint
- Cloud Run forwards to Gemini
- Benefits: rate limiting, key security, logging

### Recommended for Hackathon: Option 1
Direct Gemini API calls from the app. Simpler. For demo purposes, hardcode the API key in `local.properties` or `BuildConfig`. Mention Cloud hosting plans in your presentation.

```properties
# local.properties (gitignored)
GEMINI_API_KEY=your-key-here
```

```kotlin
// build.gradle.kts
android {
    defaultConfig {
        buildConfigField("String", "GEMINI_API_KEY",
            "\"${project.findProperty("GEMINI_API_KEY")}\"")
    }
}
```

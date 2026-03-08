# AURA — Project Setup & Quick Reference

## Prerequisites
- Android Studio Ladybug or later
- Android SDK 36 (compileSdk)
- Min SDK 26
- Kotlin 2.0.21
- Gemini API Key (from [Google AI Studio](https://aistudio.google.com/apikey))

## First-Time Setup

```bash
# 1. Clone
git clone https://github.com/shachafha/Aura.git
cd Aura

# 2. Set your Gemini API key
echo 'GEMINI_API_KEY=your-key-here' >> local.properties

# 3. Open in Android Studio → Sync Gradle → Run on physical device
```

## Branch Workflow (Hackathon)

```bash
# Create your module branch
git checkout -b feature/<module-name>

# Work, commit often
git add . && git commit -m "feat(module): description"

# When done, push and create PR
git push -u origin feature/<module-name>
```

## Key Files

| What | Where |
|------|-------|
| Architecture | `docs/ARCHITECTURE.md` |
| Module Specs | `docs/TEAM_SKILLS.md` |
| Agent Guide | `docs/AGENT_WORKFLOW.md` |
| Demo Script | `docs/DEMO_SCRIPT.md` |
| Hackathon Rules | `docs/HACKATHON_BRIEF.md` |
| Gradle deps | `gradle/libs.versions.toml` |
| App entry | `app/src/main/java/com/example/aura/MainActivity.kt` |

## Dependencies to Add (All Modules)

Add these to `gradle/libs.versions.toml` and `app/build.gradle.kts` as you start each module:

```toml
[versions]
camerax = "1.4.0"
generativeai = "0.9.0"
gson = "2.11.0"
coil = "2.7.0"
navigationCompose = "2.7.7"
```

## Useful Commands

```bash
# Build (check for compile errors)
./gradlew assembleDebug

# Run tests
./gradlew test

# Clean
./gradlew clean
```

## Team Members
<!-- Add your names here — REQUIRED by hackathon rules -->
- 
- 
- 
- 

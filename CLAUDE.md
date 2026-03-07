# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.creole.translator.ClassName"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean build outputs
./gradlew clean
```

Install to connected device via Android Studio or `adb install app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

Single-Activity MVVM app using Jetpack Compose. No Compose Navigation library — screen routing is handled via a `Screen` enum in `MainViewModel` state, and `MainActivity` switches between `MainScreen` and `HistoryScreen` based on it.

### Layer overview

**`model/Models.kt`** — All domain types in one file:
- `TranslationDirection` enum (CREOLE_TO_ENGLISH / ENGLISH_TO_CREOLE) — carries language codes (`ht`/`en`), display labels, flag emoji, and the LLM system prompt for each direction.
- `TranslationResult`, `TranslationEntry` data classes
- `GroqError` sealed class — typed errors for API/network failures

**`data/` layer** — Four managers, all instantiated directly by `MainViewModel` (no DI framework):
- `GroqService` — OkHttp client calling the Groq API for three operations: speech-to-text (Whisper large v3), translation (Llama 3.3 70b), and TTS (PlayAI). The API key comes from `BuildConfig.GROQ_API_KEY` (set in `app/build.gradle.kts`).
- `AudioRecorder` — Wraps `MediaRecorder`, records to `.m4a` in `cacheDir`, handles API level 31 constructor change.
- `TranslationHistoryManager` — SharedPreferences-backed, max 50 entries, exposes a `StateFlow<List<TranslationEntry>>`.
- `TextToSpeechManager` — Hybrid TTS: Groq PlayAI for English, Android `TextToSpeech` (French locale as proxy) for Haitian Creole.

**`ui/` layer** — Compose screens consuming `StateFlow` from `MainViewModel`:
- `MainScreen` — record button, direction indicator/switch, result cards with speak buttons
- `HistoryScreen` — list of past translations with delete/clear
- `MainViewModel` — single `AndroidViewModel`; owns all state as `StateFlow`s, orchestrates the full record → transcribe → translate → save flow.

### Key data flow

1. User taps record → `AudioRecorder.startRecording()` → `.m4a` written to cache
2. User taps stop → `GroqService.processAudio()`:
   - `transcribeAudio()` posts to `/audio/transcriptions` (Whisper)
   - `translateText()` posts to `/chat/completions` (Llama) with direction's system prompt
3. Result saved to `TranslationHistoryManager` (SharedPreferences)
4. User taps speak → `TextToSpeechManager.speak()`:
   - English → Groq `/audio/speech` → WAV → `MediaPlayer`
   - Creole → Android TTS with French locale fallback

## API Key

The Groq API key is hardcoded as a `buildConfigField` in `app/build.gradle.kts` and accessed at runtime via `BuildConfig.GROQ_API_KEY`. To change the key, update both the `debug` default config and the `release` buildType blocks in that file.

## Tech Stack

- Kotlin + Jetpack Compose + Material3
- OkHttp 4 for HTTP, `org.json` (Android built-in) for JSON parsing, Gson for SharedPreferences serialization
- `minSdk 24`, `targetSdk 34`, Kotlin 1.9.22, AGP 8.13.2

# Feature Parity — Android ↔ iOS

Use this file as a checklist when adding features to either app so nothing drifts.
Each row describes **what** the feature does and **where** it lives in each codebase.

## Shared Features

| Feature | Android | iOS |
|---------|---------|-----|
| Voice recording → Whisper transcription | `data/AudioRecorder.kt` + `data/GroqService.kt` | `AudioRecorder.swift` + `GroqService.swift` |
| Text input mode (type to translate) | `ui/MainScreen.kt` `TextInputSection` + `MainViewModel.submitTypedText()` | `ContentView.swift` `InputMode.text` + `processTextInput()` |
| Direction switcher (Creole↔English) | `MainViewModel.switchDirection()` | `ContentView.translationDirection` |
| Translation via Groq Llama 3.3-70b | `data/GroqService.translateText()` | `GroqService.translateText()` |
| TTS — English via Groq Orpheus | removed — GROQ provider setting falls back to OpenAI proxy TTS (`TextToSpeechManager.speak()`) | via api-proxy `/v1/tts-groq` (`{text, voice}` → WAV) in `TextToSpeechManager.speak(language:en)` |
| TTS — Creole via OpenAI tts-1 | `data/TextToSpeechManager.speakWithOpenAI()` | `TextToSpeechManager.speak(language:ht)` |
| TTS — device synthesizer fallback | `TextToSpeechManager.speakWithAndroid()` | `TextToSpeechManager.speakNatively()` |
| Per-language TTS provider selection | `data/VoiceSettings` `englishProvider`/`creoleProvider` | `VoiceSettings` `englishProvider`/`creoleProvider` |
| Playback speed control | `VoiceSettings` `englishPlaybackSpeed`/`creolePlaybackSpeed` (MediaPlayer.PlaybackParams + OpenAI speed param) | `VoiceSettings` `englishPlaybackSpeed`/`creolePlaybackSpeed` |
| Voice selection settings | `ui/SettingsScreen.kt` + `data/VoiceSettings.kt` | `SettingsView.swift` + `VoiceSettings.swift` |
| Persistent voice preferences | `VoiceSettings` (SharedPreferences) | `VoiceSettings` (@AppStorage / UserDefaults) |
| Interstitial ads — every 4 translations, max 6/session, ≥120s apart, session counters reset after 30+ min backgrounded, impression counted only on actual present (Android still every 25 — port pending) | `ui/InterstitialAdManager.kt` + `MainViewModel.interstitialEvent` | `InterstitialAdManager.swift` |
| Rewarded ad — unlock extra voices 24h (free: `diana`, `alloy`; gate at selection; pre-ad "Unlock Extra Voices" confirm + post-ad "Voices Unlocked" dialogs, bilingual EN + Haitian Creole copy, footer shows hours left; waits ≤3s for ad load, then no-fill/present-failure grants unlock anyway; selected voice stays usable ("Your current voice — always available"); iOS unit `CreoleTranslatorRewarded` ca-app-pub-7871017136061682/5611090338, DEBUG builds use Google test unit; Android port pending) | — | `RewardedAdManager.swift` + `VoiceSettings.swift` + `SettingsView.swift` |
| In-app review prompt — SKStoreReviewController / Play In-App Review at 3rd lifetime successful translation, once per app version, fires one translation before the first interstitial so it never overlaps an ad (Android port pending) | — | `ContentView.swift` (`maybeRequestReview`) |
| Translation history (max 50) | `data/TranslationHistoryManager.kt` + `ui/HistoryScreen.kt` | `TranslationHistory.swift` + `HistoryView.swift` |
| Banner ads | `ui/BannerAd.kt` | `BannerAdView.swift` |
| Result cards with speak buttons | `MainScreen.ResultCard` | `ContentView.ResultCard` |

## Key Constants (keep in sync)

| Constant | Android (`BuildConfig`) | iOS (`Secrets`) |
|----------|------------------------|-----------------|
| Groq API key | none in app — transcribe/translate go through api-proxy: `/v1/transcribe` (raw m4a body + `x-language` header → `{text}`), `/v1/translate` (`{text, direction: "ht-en"\|"en-ht"}` → `{translation}`) | same proxy routes (`GroqService.swift`) |
| OpenAI TTS | via api-proxy Cloud Function (no key in app): `https://us-central1-jbaker-api-proxy.cloudfunctions.net/api/v1/tts`, `x-device-id` header, payload `{text, voice, speed}` | same proxy URL/protocol |
| Whisper model | `whisper-large-v3` | `whisper-large-v3` |
| LLM model | `llama-3.3-70b-versatile` | `llama-3.3-70b-versatile` |
| Groq TTS model | `canopylabs/orpheus-v1-english` | `canopylabs/orpheus-v1-english` |
| OpenAI TTS model | `tts-1` (pinned server-side in api-proxy) | `tts-1` (pinned server-side in api-proxy) |

## Voice Options (keep in sync)

### Creole / OpenAI voices
`alloy`, `echo`, `fable`, `onyx`, `nova`, `shimmer`, `computer` (device fallback)

### English / Groq voices
`autumn`, `diana`, `hannah`, `austin`, `daniel`, `troy`

## Adding a New Feature — Checklist

1. Implement in **one** platform first and get it working end-to-end.
2. Open this file and add a row to the table above.
3. Port to the other platform, referencing the file path from step 2.
4. If a new API key or model is needed, add it to the **Key Constants** table.
5. Commit both changes together (or in back-to-back commits) so git history stays linked.

## Platform-Only Items (intentionally not shared)

| Item | Android only | iOS only |
|------|-------------|----------|
| Privacy / ATT consent | Google UMP (AdMob) | `DataPrivacyConsent.swift` + `ATTAuthorization.swift` |
| Firebase Analytics | — | `FirebaseAnalytics` TTS fallback logging |

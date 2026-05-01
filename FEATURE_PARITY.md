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
| TTS — English via Groq Orpheus | `data/TextToSpeechManager.speakWithGroq()` | `TextToSpeechManager.speak(language:en)` |
| TTS — Creole via OpenAI tts-1 | `data/TextToSpeechManager.speakWithOpenAI()` | `TextToSpeechManager.speak(language:ht)` |
| TTS — device synthesizer fallback | `TextToSpeechManager.speakWithAndroid()` | `TextToSpeechManager.speakNatively()` |
| Per-language TTS provider selection | `data/VoiceSettings` `englishProvider`/`creoleProvider` | `VoiceSettings` `englishProvider`/`creoleProvider` |
| Playback speed control | `VoiceSettings` `englishPlaybackSpeed`/`creolePlaybackSpeed` (MediaPlayer.PlaybackParams + OpenAI speed param) | `VoiceSettings` `englishPlaybackSpeed`/`creolePlaybackSpeed` |
| Voice selection settings | `ui/SettingsScreen.kt` + `data/VoiceSettings.kt` | `SettingsView.swift` + `VoiceSettings.swift` |
| Persistent voice preferences | `VoiceSettings` (SharedPreferences) | `VoiceSettings` (@AppStorage / UserDefaults) |
| Interstitial ads (every 25 translations) | `ui/InterstitialAdManager.kt` + `MainViewModel.interstitialEvent` | `InterstitialAdManager.swift` |
| Translation history (max 50) | `data/TranslationHistoryManager.kt` + `ui/HistoryScreen.kt` | `TranslationHistory.swift` + `HistoryView.swift` |
| Banner ads | `ui/BannerAd.kt` | `BannerAdView.swift` |
| Result cards with speak buttons | `MainScreen.ResultCard` | `ContentView.ResultCard` |

## Key Constants (keep in sync)

| Constant | Android (`BuildConfig`) | iOS (`Secrets`) |
|----------|------------------------|-----------------|
| Groq API key | `GROQ_API_KEY` in `local.properties` | `GROQ_API_KEY` in `Secrets.plist` |
| OpenAI API key | `OPENAI_API_KEY` in `local.properties` | `OPENAI_API_KEY` in `Secrets.plist` |
| Whisper model | `whisper-large-v3` | `whisper-large-v3` |
| LLM model | `llama-3.3-70b-versatile` | `llama-3.3-70b-versatile` |
| Groq TTS model | `canopylabs/orpheus-v1-english` | `canopylabs/orpheus-v1-english` |
| OpenAI TTS model | `tts-1` | `tts-1` |

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

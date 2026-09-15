# Feature Parity — Android ↔ iOS

Use this file as a checklist when adding features to either app so nothing drifts.
Each row describes **what** the feature does and **where** it lives in each codebase.

## Shared Features

| Feature | Android | iOS |
|---------|---------|-----|
| Voice recording → transcription (OpenAI gpt-transcribe for Creole via `x-stt-engine`, Groq Whisper for English/backup; blank transcript → `NothingHeard` status) | `data/AudioRecorder.kt` + `data/GroqService.kt` | `AudioRecorder.swift` + `GroqService.swift` |
| Text input mode (type to translate) | `ui/MainScreen.kt` `TextInputSection` + `MainViewModel.submitTypedText()` | `ContentView.swift` `InputMode.text` + `processTextInput()` |
| Direction switcher (Creole↔English) | `MainViewModel.switchDirection()` | `ContentView.translationDirection` |
| Translation via Groq (openai/gpt-oss-120b) | `data/GroqService.translateText()` | `GroqService.translateText()` |
| TTS — English via Groq Orpheus | removed — GROQ provider setting falls back to OpenAI proxy TTS (`TextToSpeechManager.speak()`) | via api-proxy `/v1/tts-groq` (`{text, voice}` → WAV) in `TextToSpeechManager.speak(language:en)` |
| TTS — Creole via OpenAI tts-1 | `data/TextToSpeechManager.speakWithOpenAI()` | `TextToSpeechManager.speak(language:ht)` |
| TTS — device synthesizer fallback | `TextToSpeechManager.speakWithAndroid()` | `TextToSpeechManager.speakNatively()` |
| Per-language TTS provider selection | `data/VoiceSettings` `englishProvider`/`creoleProvider` | `VoiceSettings` `englishProvider`/`creoleProvider` |
| Playback speed control | `VoiceSettings` `englishPlaybackSpeed`/`creolePlaybackSpeed` (MediaPlayer.PlaybackParams + OpenAI speed param) | `VoiceSettings` `englishPlaybackSpeed`/`creolePlaybackSpeed` |
| Voice selection settings | `ui/SettingsScreen.kt` + `data/VoiceSettings.kt` | `SettingsView.swift` + `VoiceSettings.swift` |
| Persistent voice preferences | `VoiceSettings` (SharedPreferences) | `VoiceSettings` (@AppStorage / UserDefaults) |
| Interstitial ads — every 4 translations, max 6/session, ≥120s apart, session counters reset after 30+ min backgrounded, impression counted only on actual present | `ui/InterstitialAdManager.kt` + `MainViewModel.interstitialEvent` | `InterstitialAdManager.swift` |
| Rewarded ad — unlock extra voices 24h (free: `diana`, `alloy`; gate at selection; pre-ad "Unlock Extra Voices" confirm + post-ad "Voices Unlocked" dialogs, bilingual EN + Haitian Creole copy, footer shows hours left; waits ≤3s for ad load, then no-fill/present-failure grants unlock anyway; selected voice stays usable ("Your current voice — always available"); iOS unit `CreoleTranslatorRewarded` ca-app-pub-7871017136061682/5611090338, DEBUG builds use Google test unit | `ui/RewardedAdManager.kt` + `data/VoiceSettings.kt` + `ui/SettingsScreen.kt` | `RewardedAdManager.swift` + `VoiceSettings.swift` + `SettingsView.swift` |
| In-app review prompt — SKStoreReviewController / Play In-App Review at 3rd lifetime successful translation, once per app version, fires one translation before the first interstitial so it never overlaps an ad | `ui/MainViewModel.kt` (`maybeRequestReview`) + `MainActivity.kt` (`ReviewManagerFactory`) | `ContentView.swift` (`maybeRequestReview`) |
| Translation history (max 50) | `data/TranslationHistoryManager.kt` + `ui/HistoryScreen.kt` | `TranslationHistory.swift` + `HistoryView.swift` |
| Phrasebook — offline 52 phrases, 6 categories (Greetings/Basics/Directions/Emergency/Medical/Travel), reversible EN↔HT direction, speaker button per phrase | `data/Phrasebook.kt` + `ui/PhrasebookScreen.kt` + `MainViewModel.showPhrasebook()` | `Phrasebook.swift` + `PhrasebookView.swift` |
| Banner ads | `ui/BannerAd.kt` | `BannerAdView.swift` |
| Result cards with speak buttons | `MainScreen.ResultCard` | `ContentView.ResultCard` |
| Language auto-detect — offline Creole/English heuristic on typed text and on the transcript; when ON and confident it disagrees with the selected direction, translates the detected way, flips the direction indicator, shows an "Auto-detected X → Y · Undo" chip; Undo re-translates in the manual direction. Settings → Translation → "Auto-detect language" toggle (default ON). Analytics `auto_detect_flip` | `data/LanguageDetector.kt` (unit-tested in `app/src/test/.../LanguageDetectorTest.kt`) + `MainViewModel.effectiveDirection()/undoAutoDetect()` + `MainScreen.AutoDetectChip` + `VoiceSettings.autoDetectLanguage` + `SettingsScreen` | `ContentView.swift` + `TextToSpeechManager.swift` / `VoiceSettings.swift` (iOS fork) |
| TTS payload carries `language: "ht"\|"en"` on `/v1/tts` (and `/v1/tts-groq` on iOS) so the proxy can apply Creole pronunciation respellings | `data/TextToSpeechManager.synthesizeWithOpenAI()` | `TextToSpeechManager.swift` (iOS fork) |
| Translation feedback — 👍/👎 on the translated-text card, shown only when the proxy returned a `sampleId`; 👎 opens an optional ≤200-char comment dialog; one tap then locked; POST `/v1/feedback` `{sampleId, rating:"up"\|"down", comment?}`, silent on failure. `/v1/translate` now sends `source: "voice"\|"typed"` and reads back `sampleId` + `confidence` (both optional) | `MainScreen.ResultCard` (`FeedbackState`, `FeedbackCommentDialog`) + `MainViewModel.rateTranslation()` + `GroqService.sendFeedback()` / `TranslationSource` | `ContentView.ResultCard` + `GroqService.sendFeedback()` |
| Transcription feedback — 👍/👎 on the source-text card for **voice results only** (typed text has no transcription step; also gated on `sampleId`); 👎 reuses the comment dialog; one tap locked; POST `/v1/feedback` `{…, target:"stt"}` → proxy stores `sttRating`/`sttComment` and keeps the audio clip on 👎; QA flags `sttRating=="down"` into the review queue with a 🎤 STT 👎 badge + stats | `MainScreen` source `ResultCard` + `MainViewModel.rateTranscription()`/`isVoiceResult` + `GroqService.sendFeedback(target)` / `FeedbackTarget` | `ContentView` source `ResultCard` (`feedbackPrompt:"Heard right?"`, `feedbackNoun:"Transcription"`, `stt_feedback` analytics) + `GroqService.sendFeedback(target:)` |

## Key Constants (keep in sync)

| Constant | Android (`BuildConfig`) | iOS (`Secrets`) |
|----------|------------------------|-----------------|
| Groq API key | none in app — transcribe/translate go through api-proxy: `/v1/transcribe` (raw m4a body + `x-language` header → `{text}`), `/v1/translate` (`{text, direction: "ht-en"\|"en-ht"}` → `{translation}`) | same proxy routes (`GroqService.swift`) |
| OpenAI TTS | via api-proxy Cloud Function (no key in app): `https://us-central1-jbaker-api-proxy.cloudfunctions.net/api/v1/tts`, `x-device-id` header, payload `{text, voice, speed}` | same proxy URL/protocol |
| STT engine | Creole: `gpt-transcribe` (`GroqService.CREOLE_STT_ENGINE`), English: `whisper-large-v3` | same (`creoleSttEngine`) |
| LLM model | `openai/gpt-oss-120b` | `openai/gpt-oss-120b` |
| Groq TTS model | `canopylabs/orpheus-v1-english` | `canopylabs/orpheus-v1-english` |

## Store Listings (Play Store via fastlane)

| Listing | Files |
|---------|-------|
| en-US (default) | `fastlane/metadata/android/en-US/{title,short_description,full_description}.txt` |
| fr-FR (France) | `fastlane/metadata/android/fr-FR/{title,short_description,full_description}.txt` — `Traducteur Créole Haïtien`, includes phrasebook + offline note |
| fr-CA (Canada) | `fastlane/metadata/android/fr-CA/{title,short_description,full_description}.txt` — Canada-tuned copy (Québec wording) |
| Haiti | No Play Store `ht` locale — served by `fr-FR` (French is Haiti's other official store language); `ht` TTS already works in-app |
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
| Privacy / ATT consent | Google UMP (AdMob) `ui/ConsentManager.kt` + adaptive `BannerAd.kt` | `DataPrivacyConsent.swift` + `ATTAuthorization.swift` |
| Firebase Analytics | — | `FirebaseAnalytics` TTS fallback logging |
| Remote kill switch | `data/AppAvailabilityManager.kt` + `ui/AppDisabledScreen.kt` — Firebase Remote Config keys `android_app_disabled` (bool) / `android_disabled_message` (string), fetched in `MainActivity.onCreate`. Deliberately **not** ported to iOS: flipping these keys in the Firebase console pulls the Android app's functionality without touching iOS, since the iOS client never reads them (even though both apps share the `globalvibes-1a6aa` Firebase project). Does not affect the `api-proxy` Cloud Function that both platforms call for transcription/translation/TTS. | — |
| Kill-switch adoption heartbeat | `AppAvailabilityManager.init()` fires a fire-and-forget POST to api-proxy's `/v1/heartbeat` on every launch (`x-device-id`, `x-app-version` = `BuildConfig.VERSION_CODE`, `x-platform: android`), regardless of the disable check. Lets the pi5/FirebaseViewer dashboards show how many devices are still on a pre-kill-switch build vs already updated, even though a killed device can never again reach translate/transcribe. iOS never sends this header/route. | — |

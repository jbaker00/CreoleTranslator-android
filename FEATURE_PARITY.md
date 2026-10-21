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
| HD voice plays (rewarded ad) — replaces the 24h voice unlock (2026-10-16). Every AI (Groq/OpenAI) TTS clip uses 1 HD play: **5 free/day** (reset local midnight, used first) + **10 per rewarded ad** (carry over). All voices are freely selectable. Replaying the clip just heard is free (last-clip cache, no request). System voice never uses plays. Chip beside each result card's speaker button: `✨ HD n` (tap = info + "Watch Video") / orange `▶ +10 HD` at 0 (tap = rewarded ad). Tapping the speaker at 0 → dialog "Out of HD voice plays": Watch Video — 10 HD Plays / Play Basic Voice / Not Now; after the ad the tapped card auto-plays. History/Phrasebook don't prompt — at 0 they fall back to the system voice. Ad policy: waits ≤3s for load; a no-fill/present-failure grants the 10 plays only **once per day** (`noAdGrantsPerDay = 1`, keys `hdNoAdGrantDay`/`hdNoAdGrantsUsed`) — after that the tapped card plays with the system voice, a "No Video Available" alert shows, and `hd_grant_blocked{via,total}` is logged (blocks the airplane-mode/ad-blocker bypass; iOS 3.5 a2ec9a0). Bilingual EN + Kreyòl copy. Settings voice footer shows plays left. Analytics: `hd_voice_out_prompt`, `hd_voice_basic_chosen`, `hd_plays_granted{via}`, `hd_voice_out_basic_fallback`. Count is device-local; api-proxy per-device (25k chars) + global (250k chars) OpenAI TTS caps remain the spend ceiling. iOS unit `CreoleTranslatorRewarded` ca-app-pub-7871017136061682/5611090338, DEBUG builds use Google test unit | `ui/RewardedAdManager.kt` + `data/VoiceSettings.kt` + `ui/SettingsScreen.kt` (still the old 24h voice unlock — port pending) | `VoiceSettings.swift` (`HDVoiceCredits`) + `TextToSpeechManager.swift` (metering + replay cache) + `ContentView.swift` (`HDPlaysChip`, `playCard`, `watchAdForHDPlays`) + `SettingsView.swift` + `RewardedAdManager.swift` |
| In-app review prompt — SKStoreReviewController / Play In-App Review at 3rd lifetime successful translation, once per app version, fires one translation before the first interstitial so it never overlaps an ad | `ui/MainViewModel.kt` (`maybeRequestReview`) + `MainActivity.kt` (`ReviewManagerFactory`) | `ContentView.swift` (`maybeRequestReview`) |
| Translation history (max 50) | `data/TranslationHistoryManager.kt` + `ui/HistoryScreen.kt` | `TranslationHistory.swift` + `HistoryView.swift` |
| Phrasebook — offline 52 phrases, 6 categories (Greetings/Basics/Directions/Emergency/Medical/Travel), reversible EN↔HT direction, speaker button per phrase. **Phrase text must be identical on both platforms** (Kreyòl corrections 2026-10-06: "Bonjou", "Souple" not "Sil vous plè", "Kote lopital la ye?", "Tèt mwen fè m mal", "Mwen ta renmen yon taksi, souple") | `data/Phrasebook.kt` + `ui/PhrasebookScreen.kt` + `MainViewModel.showPhrasebook()` | `Phrasebook.swift` + `PhrasebookView.swift` |
| Banner ads — anchored adaptive size (full width, Google-chosen height), re-requested only when the width actually changes | `ui/BannerAd.kt` | `BannerAdView.swift` (tracks requested size in a Coordinator; reading `BannerView.adSize` back after init gives 0×0 and re-loading then served a 2×-wide clipped ad) |
| Debug builds use Google's test ad units for every format (banner, interstitial, rewarded), production units only in release — avoids AdMob invalid-traffic flags from self-testing | `ui/BannerAd.kt`, `ui/InterstitialAdManager.kt`, `ui/RewardedAdManager.kt` (`BuildConfig.DEBUG`) | `BannerAdView.swift`, `InterstitialAdManager.swift`, `RewardedAdManager.swift` (`#if DEBUG`) |
| Recording limit — 30s max (auto-stop + translate, analytics `recording_auto_stopped`), progress bar with "Ns left" turning orange after 20s ("Shorter phrases translate best"), clips <0.6s discarded ("Too short — tap Start, speak, then tap Stop."), interruption (call/Siri/audio-focus loss) discards the clip and shows "Recording stopped by an interruption. Please try again." Rationale: 3,632 voice samples, confidence ≤3 for 21% of <10s clips vs 57%+ past 20s | `data/AudioRecorder.kt` + `ui/MainScreen.kt` + `MainViewModel` | `AudioRecorder.swift` (`maxDuration`/`warnAfter`/`minDuration`) + `ContentView.swift` `RecordingProgressView` |
| Deep link `creoletranslator://phrasebook` opens the phrasebook (analytics `deep_link_open`); used by App Store In-App Events, available for Play promotional content / shared links | `AndroidManifest.xml` intent-filter (scheme `creoletranslator`) + `MainActivity` intent handling | `Info.plist` `CFBundleURLTypes` + `ContentView.onOpenURL` |
| AI data disclosure — "Your speech is sent to OpenAI (Haitian Creole) or Groq (English) for transcription, with the other as a backup; text is translated by Groq, and translated text is sent to OpenAI or Groq to generate spoken audio." + retention/review sentences; text identical on both | `ui/SettingsScreen.kt` Privacy section (always-visible text, no consent gate) | `DataPrivacyConsent.swift` consent sheet (`currentVersion` = 2 re-asks once when a new recipient is named) |
| Result cards with speak buttons | `MainScreen.ResultCard` | `ContentView.ResultCard` |
| Readable colors (WCAG contrast) — every text/background pair ≥ **4.5:1**, icons/large text/progress bars ≥ **3:1**, in light AND dark mode. Don't trust how it looks on a screenshot: low-contrast dark-on-dark (e.g. card buttons on a card) is invisible to people but easy to miss in review. Palette (light / dark): accent `#0060DF`/`#64B0FF`, filled-button bg with white text `#0060DF`/`#0A60D0`, secondary text `#636366`/`#AEAEB2`, error `#D70015`/`#FF6961`, warning `#B34700`/`#FFB340`, favorite star `#856200`/`#FFD60A`; header text on the gradient (title, subtitle, status) is full white in both modes — no gray or reduced-opacity subtitle; the gray one passed WCAG but people found it hard to read (gradient darkened to `#5C2EB8`→`#B23D70` in light mode); "+N HD" orange chip uses black text; button backgrounds inside cards are an accent tint (15%), never a darker gray than the card. Re-run the audit after any color change | `ui/theme/Color.kt` + `Theme.kt` (not yet audited — see Pending ports) | `Color` palette extension in `ContentView.swift` (`appAccent`, `secondaryText`, …; root `.tint(.appAccent)` because iOS 26 ignores the AccentColor asset), `SegmentedBacking`, `scripts/contrast_audit.py` |
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
| OpenAI TTS model | `tts-1` (pinned server-side in api-proxy; OpenAI shuts it down 2027-01-06 — named replacement `gpt-realtime-2.1-mini` is Realtime-API, not yet usable on `/v1/audio/speech`) | `tts-1` (pinned server-side in api-proxy; same 2027-01-06 shutdown) |

## Voice Options (keep in sync)

### Creole / OpenAI voices
`alloy`, `echo`, `fable`, `onyx`, `nova`, `shimmer`, `computer` (device fallback)

### English / Groq voices
`autumn`, `diana`, `hannah`, `austin`, `daniel`, `troy`

## Parity Sync Status

Read by the `parity-sync` skill. "Pending ports" is the to-do list for each
platform; "Last reviewed" is the newest commit on the other platform's `main`
that has been checked for parity, so the next sync only looks at newer commits.

### Pending ports
| Feature (see rows above) | From | To | Source commit(s) | Status |
|---|---|---|---|---|
| Recording limit + progress bar + short-clip discard + interruption discard | iOS | Android | a8b5b10 | done (23105a7) |
| Debug builds use test ad units (banner, interstitial) | iOS | Android | a8b5b10 | done (23105a7) |
| Phrasebook Kreyòl corrections | iOS | Android | 5ebe5ad | done (eba86da) |
| Deep link `creoletranslator://phrasebook` | iOS | Android | 4d2f398 | done (72f5967) |
| Consent text names the real AI recipients — "Your speech is sent to OpenAI (Haitian Creole) or Groq (English) for transcription, with the other as a backup; text is translated by Groq, and translated text is sent to OpenAI or Groq to generate spoken audio." — plus consent version 2 (people who agreed to the old Groq-only text see it once) | iOS | Android | 60d4eb8, a361ef6 | done (72f5967) — text only; Android has no AI-consent gate to re-ask (disclosure lives in Settings), so the version-2 re-ask is N/A |
| HD voice plays (rewarded ad) — replaces 24h voice unlock, 1 no-video grant/day | iOS | Android | b85a706, a2ec9a0 | pending |
| Readable colors (WCAG contrast audit) — audit every Compose text/icon color against its background in light + dark (Material `onSurfaceVariant`, disabled/alpha text, tonal buttons on cards, gradient headers), adopt the shared palette, add an equivalent audit script | iOS | Android | 6e18df9, 7d1920b, 4463025 (branch `result-card-contrast`) | pending |

### Last reviewed
| Repo | Last commit reviewed for parity | Date |
|---|---|---|
| iOS (`CreoleTranslator-iOS` main) | cda4cd2 | 2026-10-10 |
| Android (`CreoleTranslator-android` main) | 1ceb6fd | 2026-10-06 |

## Adding a New Feature — Checklist

1. Implement in **one** platform first and get it working end-to-end.
2. Open this file and add a row to the table above.
3. Port to the other platform, referencing the file path from step 2. If you
   can't port it in the same session, add a **Pending ports** row (Parity Sync
   Status) instead — then `/parity-sync` in the other repo picks it up. Items
   that are intentionally one-platform go under **Platform-Only Items**.
4. If a new API key or model is needed, add it to the **Key Constants** table.
5. Commit both changes together (or in back-to-back commits) so git history stays linked.

## Platform-Only Items (intentionally not shared)

| Item | Android only | iOS only |
|------|-------------|----------|
| Header subtitle | — (no subtitle line) | "AI voice translation" (was "Powered by Groq AI") |
| Audio session threading | — | `AVAudioSession.workQueue` (AudioRecorder.swift): setActive/setCategory off the main thread, shared by recording + TTS |
| Store-screenshot capture mode | — | DEBUG `ScreenshotMode.swift` (`-shotScene …`) + `marketing/` frame script |
| Privacy manifest | — | `PrivacyInfo.xcprivacy` (ITMS-91053/91064) |
| Privacy / ATT consent | Google UMP (AdMob) `ui/ConsentManager.kt` | `DataPrivacyConsent.swift` + `ATTAuthorization.swift` |
| Firebase Analytics | — | `FirebaseAnalytics` TTS fallback logging |
| Remote kill switch | `data/AppAvailabilityManager.kt` + `ui/AppDisabledScreen.kt` — Firebase Remote Config keys `android_app_disabled` (bool) / `android_disabled_message` (string), fetched in `MainActivity.onCreate`. Deliberately **not** ported to iOS: flipping these keys in the Firebase console pulls the Android app's functionality without touching iOS, since the iOS client never reads them (even though both apps share the `globalvibes-1a6aa` Firebase project). Does not affect the `api-proxy` Cloud Function that both platforms call for transcription/translation/TTS. | — |
| Kill-switch adoption heartbeat | `AppAvailabilityManager.init()` fires a fire-and-forget POST to api-proxy's `/v1/heartbeat` on every launch (`x-device-id`, `x-app-version` = `BuildConfig.VERSION_CODE`, `x-platform: android`), regardless of the disable check. Lets the pi5/FirebaseViewer dashboards show how many devices are still on a pre-kill-switch build vs already updated, even though a killed device can never again reach translate/transcribe. iOS never sends this header/route. | — |

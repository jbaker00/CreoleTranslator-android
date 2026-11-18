package com.creole.translator.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.creole.translator.BuildConfig
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.creole.translator.data.AppAvailabilityManager
import com.creole.translator.data.AudioRecorder
import com.creole.translator.data.GroqService
import com.creole.translator.data.HDVoiceCredits
import com.creole.translator.data.LanguageDetector
import com.creole.translator.data.TextToSpeechManager
import com.creole.translator.data.TranslationHistoryManager
import com.creole.translator.data.AnalyticsManager
import com.creole.translator.data.VoiceSettings
import com.creole.translator.model.FeedbackRating
import com.creole.translator.model.GroqError
import com.creole.translator.model.Language
import com.creole.translator.model.TranslationDirection
import com.creole.translator.model.TranslationEntry
import com.creole.translator.model.TranslationResult
import com.creole.translator.model.TranslationSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class Screen { MAIN, HISTORY, SETTINGS, PHRASEBOOK }

enum class InputMode { VOICE, TEXT }

/** One spoken or typed sentence and its translation, shown as a bubble. Mirrors iOS `ConversationTurn`. */
data class ConversationTurn(
    val id: String = java.util.UUID.randomUUID().toString(),
    val source: String,
    val translation: String,
    val direction: TranslationDirection,
    val sampleId: String?,
    val input: TranslationSource
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val groqService = GroqService(application)
    private val audioRecorder = AudioRecorder(application)
    private val historyManager = TranslationHistoryManager(application)
    val voiceSettings = VoiceSettings(application)
    val hdCredits = HDVoiceCredits(application)
    val ttsManager = TextToSpeechManager(
        context = application,
        scope = viewModelScope,
        voiceSettings = voiceSettings,
        hdCredits = hdCredits
    )

    // Navigation
    private val _currentScreen = MutableStateFlow(Screen.MAIN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Input mode (Voice or Text)
    private val _inputMode = MutableStateFlow(InputMode.VOICE)
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    // Typed input for text mode
    private val _typedInput = MutableStateFlow("")
    val typedInput: StateFlow<String> = _typedInput.asStateFlow()

    // Recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // Milliseconds into the current recording, for the progress bar.
    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()
    private var recordingTicker: Job? = null

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val appPrefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // Just talk: there is no direction setting. This is the direction last
    // used — the pair (Kreyòl + English / Español) and the fallback for words
    // the detector can't place. Remembered between launches (iOS
    // `translationDirection`); a fresh install on a Spanish-language phone or
    // in a Spanish-speaking country starts on Kreyòl + Español.
    private val _direction = MutableStateFlow(
        appPrefs.getString(KEY_DIRECTION, null)
            ?.let { runCatching { TranslationDirection.valueOf(it) }.getOrNull() }
            ?: if (Language.phoneSuggestsSpanish) TranslationDirection.SPANISH_TO_CREOLE
               else TranslationDirection.CREOLE_TO_ENGLISH
    )
    val direction: StateFlow<TranslationDirection> = _direction.asStateFlow()

    private fun setDirection(direction: TranslationDirection) {
        _direction.value = direction
        appPrefs.edit().putString(KEY_DIRECTION, direction.name).apply()
    }

    /** The conversation on screen, oldest first. This session only; History keeps the lasting copy. */
    private val _turns = MutableStateFlow<List<ConversationTurn>>(emptyList())
    val turns: StateFlow<List<ConversationTurn>> = _turns.asStateFlow()

    // QA feedback for the newest bubble. sampleId is null when the proxy
    // didn't capture it (override hit, older proxy) — then no thumbs are shown.
    private val _currentSampleId = MutableStateFlow<String?>(null)
    val currentSampleId: StateFlow<String?> = _currentSampleId.asStateFlow()

    private val _feedbackGiven = MutableStateFlow<FeedbackRating?>(null)
    val feedbackGiven: StateFlow<FeedbackRating?> = _feedbackGiven.asStateFlow()

    /** Other language the last input looked like (English typed into the
     * Spanish pair, or the reverse). Offered as a chip; never applied silently. */
    private val _suggestedPartner = MutableStateFlow<Language?>(null)
    val suggestedPartner: StateFlow<Language?> = _suggestedPartner.asStateFlow()

    /** One-time "New · Nouvo" card on the first screen; gone on "Got it" or the first translation. */
    private val _showWhatsNew = MutableStateFlow(appPrefs.getString(KEY_WHATS_NEW, null) != WHATS_NEW_VERSION)
    val showWhatsNew: StateFlow<Boolean> = _showWhatsNew.asStateFlow()

    fun dismissWhatsNew() {
        _showWhatsNew.value = false
        appPrefs.edit().putString(KEY_WHATS_NEW, WHATS_NEW_VERSION).apply()
    }

    /** What produced the newest bubble, so it can be translated again. */
    private var lastInput: Pair<String, TranslationSource>? = null

    // Status / error messages
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // History
    val historyEntries = historyManager.entries

    // TTS state forwarded
    val isSpeaking = ttsManager.isSpeaking
    val ttsError = ttsManager.lastError

    // Remote kill switch (Android-only; iOS never reads this flag)
    val isAppDisabled = AppAvailabilityManager.isDisabled
    val disabledMessage = AppAvailabilityManager.disabledMessage

    private var currentRecordingFile: File? = null

    init {
        audioRecorder.onInterrupted = {
            stopRecordingTicker()
            _isRecording.value = false
            currentRecordingFile = null
            _statusMessage.value = null
            _errorMessage.value = "Recording stopped by an interruption. Please try again."
        }
    }

    // Emitted after every successful translation; InterstitialAdManager decides
    // whether an ad is actually due (interval, session cap, spacing).
    private val _interstitialEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val interstitialEvent: SharedFlow<Unit> = _interstitialEvent.asSharedFlow()

    private val _reviewEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reviewEvent: SharedFlow<Unit> = _reviewEvent.asSharedFlow()

    /** "en-ht" or "es-ht", whichever pair is on screen (iOS `pairCode`). */
    private val pairCode: String get() = "${_direction.value.partner.code}-ht"

    private fun onTranslationSuccess(direction: TranslationDirection, charLength: Int, isVoice: Boolean) {
        // Analytics: core metric for Android vs iOS comparison
        AnalyticsManager.logTranslation(direction.proxyDirection, pairCode, charLength, isVoice, true)
        maybeRequestReview()
        _interstitialEvent.tryEmit(Unit)
    }

    // Fires at the 3rd lifetime success, once per app version — one translation
    // before the first interstitial (4th), so the rating dialog never lands on
    // top of a full-screen ad.
    private fun maybeRequestReview() {
        val count = appPrefs.getInt("successfulTranslationCount", 0) + 1
        appPrefs.edit().putInt("successfulTranslationCount", count).apply()
        val version = BuildConfig.VERSION_NAME
        if (count < 3 || appPrefs.getString("lastReviewPromptVersion", null) == version) return
        appPrefs.edit().putString("lastReviewPromptVersion", version).apply()
        AnalyticsManager.logReviewRequested(version)
        _reviewEvent.tryEmit(Unit)
    }

    // ── Just talk: detection ─────────────────────────────────────────────────

    /** The direction to translate [text] in: the detector's answer within the
     * pair of [fallback], else [fallback] itself. */
    private fun detectedDirection(text: String, fallback: TranslationDirection): TranslationDirection {
        val partner = fallback.partner
        return when (LanguageDetector.detect(text, partner)) {
            LanguageDetector.Detected.CREOLE -> TranslationDirection.of(Language.CREOLE, partner) ?: fallback
            LanguageDetector.Detected.ENGLISH, LanguageDetector.Detected.SPANISH ->
                TranslationDirection.of(partner, Language.CREOLE) ?: fallback
            LanguageDetector.Detected.UNKNOWN -> fallback
        }
    }

    /** The other non-Creole language, when the text clearly reads as that one
     * and not as anything in the selected pair. */
    private fun otherPartner(text: String, selected: TranslationDirection): Language? {
        val other = if (selected.partner == Language.ENGLISH) Language.SPANISH else Language.ENGLISH
        if (LanguageDetector.detect(text, selected.partner) != LanguageDetector.Detected.UNKNOWN) return null
        val guess = LanguageDetector.detect(text, other)
        return if (guess == LanguageDetector.Detected.ENGLISH || guess == LanguageDetector.Detected.SPANISH) other else null
    }

    /** Pair chip / Settings: Kreyòl is always one side, so the only choice is the other language. */
    fun choosePartner(partner: Language) {
        if (partner == _direction.value.partner) return
        // Earlier bubbles keep the languages they were translated in.
        setDirection(_direction.value.withPartner(partner))
        _suggestedPartner.value = null
        AnalyticsManager.logPairSelected(pairCode)
    }

    fun acceptSuggestedPartner() {
        val partner = _suggestedPartner.value ?: return
        val input = lastInput ?: return
        _suggestedPartner.value = null
        TranslationDirection.of(partner, Language.CREOLE)?.let { setDirection(it) }
        AnalyticsManager.logPairSuggestionAccepted(pairCode)
        processTextInput(input.first, input.second, replaceLast = true)
    }

    /** The way out when the app read the language wrong: the newest bubble is
     * translated again in the opposite direction, with detection off. */
    fun translateLastOtherWay() {
        val last = _turns.value.lastOrNull() ?: return
        if (_isProcessing.value) return
        AnalyticsManager.logAutoDetectUndo(last.direction.proxyDirection)
        processTextInput(last.source, last.input, forced = last.direction.reversed, replaceLast = true)
    }

    /** Adds a finished translation to the conversation. The direction it used
     * is remembered as the fallback for words the detector can't place. */
    private fun show(result: TranslationResult, input: TranslationSource, replaceLast: Boolean) {
        val turn = ConversationTurn(
            source = result.transcription,
            translation = result.translation,
            direction = result.direction,
            sampleId = result.sampleId,
            input = input
        )
        _turns.value = (if (replaceLast) _turns.value.dropLast(1) else _turns.value) + turn
        setDirection(result.direction)
        if (_showWhatsNew.value) dismissWhatsNew()
        _currentSampleId.value = result.sampleId
        _feedbackGiven.value = null
        lastInput = result.transcription to input
        _suggestedPartner.value = otherPartner(result.transcription, result.direction)
        _statusMessage.value = null
        historyManager.addEntry(
            sourceText = result.transcription,
            translatedText = result.translation,
            direction = result.direction
        )
        onTranslationSuccess(result.direction, result.translation.length, input == TranslationSource.VOICE)
    }

    private fun showFailure(e: Exception, isVoice: Boolean) {
        AnalyticsManager.logTranslationFailed(_direction.value.proxyDirection, isVoice, e.javaClass.simpleName + ": " + (e.message ?: ""))
        _errorMessage.value = when (e) {
            is GroqError.InvalidApiKey -> "Service authorization failed. Please try again later."
            is GroqError.TranscriptionFailed -> "Transcription failed: ${e.message}"
            is GroqError.TranslationFailed -> "Translation failed: ${e.message}"
            is GroqError.NetworkError -> "Network error: ${e.message}"
            else -> "Error: ${e.message}"
        }
    }

    /** [forced] skips detection (used by "translate the other way"). */
    private fun processTextInput(
        text: String,
        source: TranslationSource,
        forced: TranslationDirection? = null,
        replaceLast: Boolean = false
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            _statusMessage.value = null
            try {
                val direction = forced ?: detectedDirection(text, _direction.value)
                val result = groqService.translate(text, direction, source, autoDirection = forced == null)
                show(result, source, replaceLast)
                if (source == TranslationSource.TYPED && !replaceLast) _typedInput.value = ""
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                showFailure(e, source == TranslationSource.VOICE)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // ── Input mode ──────────────────────────────────────────────────────────

    fun setInputMode(mode: InputMode) {
        _inputMode.value = mode
    }

    fun setTypedInput(text: String) {
        _typedInput.value = text
    }

    /** Translate the typed text; the words decide the direction, as with speech. */
    fun submitTypedText() {
        val text = _typedInput.value.trim()
        if (text.isEmpty() || _isProcessing.value) return
        processTextInput(text, TranslationSource.TYPED)
    }

    // ── Recording ───────────────────────────────────────────────────────────

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecordingAndProcess()
        } else {
            AnalyticsManager.logMicTapped(pairCode)
            ttsManager.stopSpeaking()
            startRecording()
        }
    }

    private fun startRecording() {
        _errorMessage.value = null
        _statusMessage.value = null
        try {
            currentRecordingFile = audioRecorder.startRecording()
            _isRecording.value = true
            startRecordingTicker()
        } catch (e: Exception) {
            _errorMessage.value = "Failed to start recording: ${e.message}"
        }
    }

    // Drives the progress bar and stops the recording at MAX_DURATION_MS.
    private fun startRecordingTicker() {
        recordingTicker?.cancel()
        _recordingElapsedMs.value = 0L
        recordingTicker = viewModelScope.launch {
            while (isActive && audioRecorder.isRecording) {
                val elapsed = audioRecorder.elapsedMs
                _recordingElapsedMs.value = elapsed.coerceAtMost(AudioRecorder.MAX_DURATION_MS)
                if (elapsed >= AudioRecorder.MAX_DURATION_MS) {
                    AnalyticsManager.logRecordingAutoStopped()
                    stopRecordingAndProcess(autoStopped = true)
                    return@launch
                }
                delay(100)
            }
        }
    }

    private fun stopRecordingTicker() {
        recordingTicker?.cancel()
        recordingTicker = null
        _recordingElapsedMs.value = 0L
    }

    private fun stopRecordingAndProcess(autoStopped: Boolean = false) {
        stopRecordingTicker()
        _isRecording.value = false
        _statusMessage.value = null
        currentRecordingFile = null

        // Null almost always means a too-short tap (the recorder discards those).
        val audioFile = audioRecorder.stopRecording()
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            audioFile?.let { audioRecorder.deleteRecording(it) }
            _statusMessage.value = "🎤 Too short — tap the mic button, speak, then tap Stop."
            return
        }

        if (autoStopped) _statusMessage.value = "⏳ Time's up — processing..."
        processAudio(audioFile)
    }

    private fun processAudio(audioFile: File) {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null

            try {
                val fallback = _direction.value
                // Always transcribed by the Creole engine: it writes down
                // whichever language was actually spoken (checked with English,
                // Spanish and Kreyòl clips), so the words can decide the direction.
                val transcription = groqService.transcribe(audioFile, Language.CREOLE.code)
                // gpt-transcribe declines rather than guesses: blank means "heard nothing usable".
                if (transcription.isBlank()) throw GroqError.NothingHeard
                val result = groqService.translate(
                    transcription, detectedDirection(transcription, fallback),
                    TranslationSource.VOICE, autoDirection = true
                )
                _statusMessage.value = null
                show(result, TranslationSource.VOICE, replaceLast = false)
            } catch (e: GroqError.NothingHeard) {
                // Not a failure: the engine heard nothing it could transcribe.
                AnalyticsManager.logSttNothingHeard()
                _errorMessage.value = null
                _statusMessage.value = "🎤 ${e.message}"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = null
                showFailure(e, isVoice = true)
            } finally {
                _isProcessing.value = false
                audioRecorder.deleteRecording(audioFile)
            }
        }
    }

    /** 👍/👎 on the newest bubble's translation. Fire-and-forget; the UI locks after one tap. */
    fun rateTranslation(rating: FeedbackRating, comment: String? = null) {
        val sampleId = _currentSampleId.value ?: return
        if (_feedbackGiven.value != null) return
        _feedbackGiven.value = rating
        AnalyticsManager.logTranslationFeedback(rating.wire)
        viewModelScope.launch { groqService.sendFeedback(sampleId, rating, comment) }
    }

    companion object {
        private const val KEY_DIRECTION = "translationDirection"
        private const val KEY_WHATS_NEW = "whatsNewSeenVersion"
        /** Release that introduced Just talk + Spanish on Android. */
        private const val WHATS_NEW_VERSION = "just-talk"
    }

    // ── TTS ─────────────────────────────────────────────────────────────────

    fun speakText(text: String, language: String, basicVoice: Boolean = false) {
        ttsManager.speak(text, language, basicVoice)
    }

    /** True when playing [text] now would use one of the user's HD plays. */
    fun usesHDPlay(text: String, language: String): Boolean = ttsManager.usesHDPlay(text, language)

    fun stopSpeaking() {
        ttsManager.stopSpeaking()
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    fun clearError() {
        _errorMessage.value = null
        ttsManager.clearError()
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    fun showHistory() { _currentScreen.value = Screen.HISTORY; AnalyticsManager.logScreenView("history") }
    fun showSettings() { _currentScreen.value = Screen.SETTINGS; AnalyticsManager.logScreenView("settings") }
    fun showPhrasebook() { _currentScreen.value = Screen.PHRASEBOOK; AnalyticsManager.logScreenView("phrasebook") }
    fun showMain() { _currentScreen.value = Screen.MAIN; AnalyticsManager.logScreenView("main") }

    // ── History ──────────────────────────────────────────────────────────────

    fun deleteHistoryEntry(entry: TranslationEntry) {
        historyManager.deleteEntry(entry)
    }

    fun clearHistory() {
        historyManager.clearAll()
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.onInterrupted = null
        if (audioRecorder.isRecording) {
            audioRecorder.cancelRecording()
        }
        ttsManager.shutdown()
    }
}

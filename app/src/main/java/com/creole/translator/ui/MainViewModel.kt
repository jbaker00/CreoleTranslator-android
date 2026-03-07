package com.creole.translator.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.creole.translator.BuildConfig
import com.creole.translator.data.AudioRecorder
import com.creole.translator.data.GroqService
import com.creole.translator.data.TextToSpeechManager
import com.creole.translator.data.TranslationHistoryManager
import com.creole.translator.model.GroqError
import com.creole.translator.model.TranslationDirection
import com.creole.translator.model.TranslationEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class Screen { MAIN, HISTORY }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val groqService = GroqService()
    private val audioRecorder = AudioRecorder(application)
    private val historyManager = TranslationHistoryManager(application)
    val ttsManager = TextToSpeechManager(application, groqService, viewModelScope, BuildConfig.OPENAI_API_KEY)

    // Navigation
    private val _currentScreen = MutableStateFlow(Screen.MAIN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Translation direction
    private val _direction = MutableStateFlow(TranslationDirection.CREOLE_TO_ENGLISH)
    val direction: StateFlow<TranslationDirection> = _direction.asStateFlow()

    // Results
    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _translation = MutableStateFlow("")
    val translation: StateFlow<String> = _translation.asStateFlow()

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

    private var currentRecordingFile: File? = null

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
            startRecording()
        }
    }

    private fun startRecording() {
        _errorMessage.value = null
        _statusMessage.value = null
        try {
            currentRecordingFile = audioRecorder.startRecording()
            _isRecording.value = true
            _statusMessage.value = "Recording... tap again to stop"
        } catch (e: Exception) {
            _errorMessage.value = "Failed to start recording: ${e.message}"
        }
    }

    private fun stopRecordingAndProcess() {
        _isRecording.value = false
        _statusMessage.value = null

        val audioFile = audioRecorder.stopRecording()
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            _errorMessage.value = "Recording failed or was too short"
            return
        }

        processAudio(audioFile)
    }

    private fun processAudio(audioFile: File) {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            _transcription.value = ""
            _translation.value = ""

            try {
                val result = groqService.processAudio(audioFile, _direction.value)
                _transcription.value = result.transcription
                _translation.value = result.translation

                historyManager.addEntry(
                    sourceText = result.transcription,
                    translatedText = result.translation,
                    direction = result.direction
                )

                _statusMessage.value = "Translation complete"
            } catch (e: GroqError.InvalidApiKey) {
                _errorMessage.value = "Invalid Groq API key. Please check your configuration."
            } catch (e: GroqError.TranscriptionFailed) {
                _errorMessage.value = "Transcription failed: ${e.message}"
            } catch (e: GroqError.TranslationFailed) {
                _errorMessage.value = "Translation failed: ${e.message}"
            } catch (e: GroqError.NetworkError) {
                _errorMessage.value = "Network error: ${e.message}"
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isProcessing.value = false
                audioRecorder.deleteRecording(audioFile)
            }
        }
    }

    fun switchDirection() {
        _direction.value = when (_direction.value) {
            TranslationDirection.CREOLE_TO_ENGLISH -> TranslationDirection.ENGLISH_TO_CREOLE
            TranslationDirection.ENGLISH_TO_CREOLE -> TranslationDirection.CREOLE_TO_ENGLISH
        }
        // Swap displayed results when switching
        val temp = _transcription.value
        _transcription.value = _translation.value
        _translation.value = temp
    }

    fun speakText(text: String, language: String) {
        ttsManager.speak(text, language)
    }

    fun stopSpeaking() {
        ttsManager.stopSpeaking()
    }

    fun clearError() {
        _errorMessage.value = null
        ttsManager.clearError()
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun showHistory() {
        _currentScreen.value = Screen.HISTORY
    }

    fun showMain() {
        _currentScreen.value = Screen.MAIN
    }

    fun deleteHistoryEntry(entry: TranslationEntry) {
        historyManager.deleteEntry(entry)
    }

    fun clearHistory() {
        historyManager.clearAll()
    }

    override fun onCleared() {
        super.onCleared()
        if (audioRecorder.isRecording) {
            audioRecorder.cancelRecording()
        }
        ttsManager.shutdown()
    }
}

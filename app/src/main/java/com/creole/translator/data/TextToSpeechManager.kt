package com.creole.translator.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class TextToSpeechManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val openAiApiKey: String,
    private val voiceSettings: VoiceSettings
) {

    companion object {
        private const val TAG = "CreoleTTS"
    }

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var androidTts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var ttsReady = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        initAndroidTts()
    }

    private fun initAndroidTts() {
        androidTts = TextToSpeech(context) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
        }
    }

    fun speak(text: String, language: String) {
        if (text.isBlank()) return
        stopSpeaking()
        _lastError.value = null

        val isCreole = language == "ht"
        val provider = if (isCreole) voiceSettings.creoleProvider.value else voiceSettings.englishProvider.value
        val speed = if (isCreole) voiceSettings.creolePlaybackSpeed.value else voiceSettings.englishPlaybackSpeed.value

        when (provider) {
            TTSProvider.OPENAI, TTSProvider.GROQ -> {
                // GROQ treated as OPENAI (migration compat — Groq TTS removed)
                val voice = if (isCreole) voiceSettings.openAIVoice.value else voiceSettings.englishOpenAIVoice.value
                if (openAiApiKey.isNotBlank()) {
                    speakWithOpenAI(text, voice, speed)
                } else {
                    speakWithAndroid(text, language, speed)
                }
            }
            TTSProvider.SYSTEM -> speakWithAndroid(text, language, speed)
        }
    }

    private fun speakWithOpenAI(text: String, voice: String, speed: Double) {
        scope.launch {
            _isSpeaking.value = true
            try {
                // Speed is sent to the OpenAI API directly, so playback rate stays at 1.0
                val audioData = synthesizeWithOpenAI(text, voice, speed)
                playAudioData(audioData, "tts_output.mp3", 1.0)
            } catch (e: Exception) {
                _lastError.value = "OpenAI TTS failed: ${e.message}"
                speakWithAndroid(text, "ht", speed)
            }
        }
    }

    private suspend fun synthesizeWithOpenAI(text: String, voice: String, speed: Double): ByteArray = withContext(Dispatchers.IO) {
        val clampedSpeed = speed.coerceIn(0.25, 4.0)
        val body = JSONObject().apply {
            put("model", "tts-1")
            put("input", text)
            put("voice", voice)
            put("response_format", "mp3")
            put("speed", clampedSpeed)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/speech")
            .addHeader("Authorization", "Bearer $openAiApiKey")
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("HTTP ${response.code}: $errorBody")
        }
        response.body?.bytes() ?: throw Exception("Empty response body")
    }

    private suspend fun playAudioData(data: ByteArray, filename: String, speed: Double) = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, filename)
        FileOutputStream(tempFile).use { it.write(data) }

        withContext(Dispatchers.Main) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(tempFile.absolutePath)
                    prepare()
                    // PlaybackParams speed: 0.5–2.0 is the reliable range on Android
                    playbackParams = PlaybackParams().setSpeed(speed.toFloat().coerceIn(0.5f, 2.0f))
                    setOnCompletionListener {
                        _isSpeaking.value = false
                        it.release()
                        mediaPlayer = null
                        tempFile.delete()
                    }
                    setOnErrorListener { _, _, _ ->
                        _isSpeaking.value = false
                        true
                    }
                    start()
                }
            } catch (e: Exception) {
                _isSpeaking.value = false
                _lastError.value = "Playback error: ${e.message}"
            }
        }
    }

    private fun speakWithAndroid(text: String, language: String, speed: Double) {
        Log.d(TAG, "speakWithAndroid: language=$language speed=$speed")
        if (!ttsReady) {
            _lastError.value = "Text-to-speech not available"
            return
        }

        val locale = when (language) {
            "ht" -> Locale.FRENCH  // Closest available locale for Haitian Creole
            else -> Locale.US
        }

        androidTts?.language = locale
        androidTts?.setSpeechRate(speed.toFloat())
        androidTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
            override fun onDone(utteranceId: String?) { _isSpeaking.value = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _lastError.value = "Speech synthesis error"
            }
        })

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "tts_utterance")
        }
        androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "tts_utterance")
        _isSpeaking.value = true
    }

    fun stopSpeaking() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        androidTts?.stop()
        _isSpeaking.value = false
    }

    fun clearError() {
        _lastError.value = null
    }

    fun shutdown() {
        stopSpeaking()
        androidTts?.shutdown()
        androidTts = null
    }
}

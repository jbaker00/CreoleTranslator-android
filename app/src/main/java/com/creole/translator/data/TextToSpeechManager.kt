package com.creole.translator.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.creole.translator.data.AnalyticsManager
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
    private val voiceSettings: VoiceSettings,
    private val hdCredits: HDVoiceCredits
) {

    companion object {
        private const val TAG = "CreoleTTS"
        private const val TTS_PROXY_URL = "https://us-central1-jbaker-api-proxy.cloudfunctions.net/api/v1/tts"
    }

    private val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("proxy_prefs", Context.MODE_PRIVATE)
        prefs.getString("proxyDeviceId", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("proxyDeviceId", it).apply()
        }
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

    // Last HD clip, so replaying it costs neither a request nor an HD play.
    private var lastHDAudio: Pair<String, ByteArray>? = null
    private var requestId = 0L

    private data class HDRequest(val voice: String, val speed: Double, val key: String)

    /** [basicVoice] forces the on-device voice (chosen when out of HD plays). */
    fun speak(text: String, language: String, basicVoice: Boolean = false) {
        if (text.isBlank()) return
        stopSpeaking()
        _lastError.value = null

        val speed = playbackSpeed(language)
        val hd = if (basicVoice) null else hdRequest(text, language)
        if (hd == null) {
            speakWithAndroid(text, language, speed)
            return
        }

        // Replaying the clip just heard is free: no request, no HD play used.
        lastHDAudio?.let { (key, data) ->
            if (key == hd.key) {
                _isSpeaking.value = true
                scope.launch { playAudioData(data, "tts_output.mp3", 1.0) }
                return
            }
        }

        // Callers that don't prompt first (History, Phrasebook, Settings test)
        // land here when the user is out of HD plays.
        if (hdCredits.playsLeft.value <= 0) {
            AnalyticsManager.logHdVoiceOutBasicFallback(language)
            speakWithAndroid(text, language, speed)
            return
        }

        val id = ++requestId
        _isSpeaking.value = true
        scope.launch {
            try {
                // Speed is applied by the TTS API itself, so playback rate stays at 1.0
                val audioData = synthesizeWithOpenAI(text, hd.voice, hd.speed, language)
                // stopSpeaking() or a newer speak() superseded this request.
                if (requestId != id || !_isSpeaking.value) return@launch
                lastHDAudio = hd.key to audioData
                hdCredits.consume()
                playAudioData(audioData, "tts_output.mp3", 1.0)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId != id || !_isSpeaking.value) return@launch
                _lastError.value = "OpenAI TTS failed: ${e.message}"
                val message = e.message ?: "unknown"
                val lowerMessage = message.lowercase()
                if (lowerMessage.contains("insufficient_quota") || lowerMessage.contains("exceeded your current quota")) {
                    AnalyticsManager.logTtsQuotaExceeded(hd.voice, text.length)
                }
                AnalyticsManager.logTtsFallback("openai", language, message)
                speakWithAndroid(text, language, speed)
            }
        }
    }

    /** True when playing [text] now would use one of the user's HD plays. */
    fun usesHDPlay(text: String, language: String): Boolean {
        val hd = hdRequest(text, language) ?: return false
        return lastHDAudio?.first != hd.key
    }

    private fun playbackSpeed(language: String): Double =
        if (language == "ht") voiceSettings.creolePlaybackSpeed.value else voiceSettings.englishPlaybackSpeed.value

    /** The AI voice request for [text], or null when the system voice is selected. */
    private fun hdRequest(text: String, language: String): HDRequest? {
        val isCreole = language == "ht"
        val provider = if (isCreole) voiceSettings.creoleProvider.value else voiceSettings.englishProvider.value
        // GROQ treated as OPENAI (migration compat — Groq TTS removed)
        if (provider == TTSProvider.SYSTEM) return null
        val voice = if (isCreole) voiceSettings.openAIVoice.value else voiceSettings.englishOpenAIVoice.value
        val speed = playbackSpeed(language)
        return HDRequest(voice, speed, "openai|$voice|$speed|$language|$text")
    }

    private suspend fun synthesizeWithOpenAI(text: String, voice: String, speed: Double, language: String): ByteArray = withContext(Dispatchers.IO) {
        val clampedSpeed = speed.coerceIn(0.25, 2.0)
        val body = JSONObject().apply {
            put("text", text)
            put("voice", voice)
            put("speed", clampedSpeed)
            // "ht"|"en" — lets the proxy apply Creole pronunciation respellings before synthesis.
            put("language", language)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(TTS_PROXY_URL)
            .addHeader("x-device-id", deviceId)
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

package com.creole.translator.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.TimeUnit

class TextToSpeechManager(
    private val context: Context,
    private val groqService: GroqService,
    private val scope: CoroutineScope,
    private val openAiApiKey: String
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
    @Volatile private var audioTrack: AudioTrack? = null
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

        when {
            language == "en" && groqService.isApiKeyValid() -> speakWithGroq(text)
            language == "ht" && openAiApiKey.isNotBlank() -> speakWithOpenAI(text)
            else -> speakWithAndroid(text, language)
        }
    }

    private fun speakWithGroq(text: String) {
        scope.launch {
            _isSpeaking.value = true
            _lastError.value = null
            try {
                Log.d(TAG, "speakWithGroq: requesting Groq TTS for '${text.take(40)}'")
                val audioData = groqService.synthesizeSpeech(text)
                Log.d(TAG, "speakWithGroq: received ${audioData.size} bytes, playing audio")
                playAudioData(audioData, "tts_output.wav")
            } catch (e: Exception) {
                Log.e(TAG, "speakWithGroq: failed, falling back to Android TTS", e)
                speakWithAndroid(text, "en")
            }
        }
    }

    private fun speakWithOpenAI(text: String) {
        scope.launch {
            _isSpeaking.value = true
            _lastError.value = null
            try {
                val audioData = synthesizeWithOpenAI(text)
                playAudioData(audioData, "tts_output.mp3")
            } catch (e: Exception) {
                _lastError.value = "OpenAI TTS failed: ${e.message}"
                speakWithAndroid(text, "ht")
            }
        }
    }

    private suspend fun synthesizeWithOpenAI(text: String): ByteArray = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", "tts-1")
            put("input", text)
            put("voice", "alloy")
            put("response_format", "mp3")
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

    private suspend fun playAudioData(data: ByteArray, filename: String) {
        if (filename.endsWith(".wav")) {
            playWavWithAudioTrack(data)
        } else {
            playMp3WithMediaPlayer(data, filename)
        }
    }

    private suspend fun playWavWithAudioTrack(data: ByteArray) = withContext(Dispatchers.IO) {
        // Walk WAV chunks to find fmt and data
        var offset = 12
        var sampleRate = 44100
        var numChannels = 1
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= data.size) {
            val chunkId = String(data, offset, 4)
            val chunkSize = ByteBuffer.wrap(data, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            when (chunkId) {
                "fmt " -> {
                    numChannels  = ByteBuffer.wrap(data, offset + 10, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                    sampleRate   = ByteBuffer.wrap(data, offset + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    bitsPerSample = ByteBuffer.wrap(data, offset + 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                }
                "data" -> {
                    dataOffset = offset + 8
                    // chunkSize == -1 means 0xFFFFFFFF (unknown/streaming WAV)
                    dataSize = if (chunkSize < 0) data.size - dataOffset else chunkSize
                    break
                }
            }
            offset += 8 + chunkSize
        }

        if (dataOffset < 0) throw Exception("WAV has no data chunk")
        Log.d(TAG, "WAV: ${sampleRate}Hz ${numChannels}ch ${bitsPerSample}bit, ${dataSize} PCM bytes")

        val channelConfig = if (numChannels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val encoding = if (bitsPerSample == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)

        val track = AudioTrack(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(encoding)
                .setChannelMask(channelConfig)
                .build(),
            minBuffer,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack = track
        _isSpeaking.value = true
        track.play()

        val startMs = System.currentTimeMillis()
        var written = 0
        while (written < dataSize && audioTrack === track) {
            val toWrite = minOf(minBuffer, dataSize - written)
            val result = track.write(data, dataOffset + written, toWrite)
            if (result < 0) break
            written += result
        }

        // Wait for the remaining buffered audio to finish playing
        val totalDurationMs = dataSize * 1000L / (sampleRate * numChannels * (bitsPerSample / 8))
        val elapsed = System.currentTimeMillis() - startMs
        val remaining = totalDurationMs - elapsed + 300L
        if (remaining > 0) delay(remaining)

        if (audioTrack === track) {
            track.stop()
            track.release()
            audioTrack = null
            _isSpeaking.value = false
        }
    }

    private suspend fun playMp3WithMediaPlayer(data: ByteArray, filename: String) = withContext(Dispatchers.IO) {
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

    private fun speakWithAndroid(text: String, language: String) {
        Log.d(TAG, "speakWithAndroid: using Android TTS, language=$language")
        if (!ttsReady) {
            _lastError.value = "Text-to-speech not available"
            return
        }

        val locale = when (language) {
            "ht" -> Locale.FRENCH  // Closest available locale for Haitian Creole
            "en" -> Locale.US
            else -> Locale.US
        }

        androidTts?.language = locale
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
        audioTrack?.let {
            audioTrack = null  // signal the write loop to stop
            try { it.stop(); it.release() } catch (_: Exception) {}
        }
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

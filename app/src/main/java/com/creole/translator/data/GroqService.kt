package com.creole.translator.data

import android.content.Context
import com.creole.translator.model.GroqError
import com.creole.translator.model.TranslationDirection
import com.creole.translator.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

// Speech/translation via the api-proxy Cloud Function (Groq upstream).
// The Groq key lives in Firebase Secret Manager; the app ships no credentials.
class GroqService(context: Context) {

    companion object {
        private const val PROXY_BASE = "https://us-central1-jbaker-api-proxy.cloudfunctions.net/api"
    }

    private val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("proxy_prefs", Context.MODE_PRIVATE)
        prefs.getString("proxyDeviceId", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("proxyDeviceId", it).apply()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun proxyRequest(path: String): Request.Builder =
        Request.Builder()
            .url("$PROXY_BASE$path")
            .addHeader("x-device-id", deviceId)

    suspend fun processText(
        text: String,
        direction: TranslationDirection
    ): TranslationResult = withContext(Dispatchers.IO) {
        val translation = translateText(text, direction)

        TranslationResult(
            transcription = text,
            translation = translation,
            direction = direction
        )
    }

    suspend fun processAudio(
        audioFile: File,
        direction: TranslationDirection
    ): TranslationResult = withContext(Dispatchers.IO) {
        val transcription = transcribeAudio(audioFile, direction.sourceLanguage)
        val translation = translateText(transcription, direction)

        TranslationResult(
            transcription = transcription,
            translation = translation,
            direction = direction
        )
    }

    private fun transcribeAudio(audioFile: File, language: String): String {
        val request = proxyRequest("/v1/transcribe")
            .addHeader("x-language", language)
            .post(audioFile.asRequestBody("application/octet-stream".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw GroqError.TranscriptionFailed("Empty response")

        if (!response.isSuccessful) {
            if (response.code == 401) throw GroqError.InvalidApiKey
            throw GroqError.TranscriptionFailed("HTTP ${response.code}: $body")
        }

        return try {
            JSONObject(body).getString("text").trim()
        } catch (e: Exception) {
            throw GroqError.TranscriptionFailed("Failed to parse response: ${e.message}")
        }
    }

    private fun translateText(text: String, direction: TranslationDirection): String {
        val proxyDirection = when (direction) {
            TranslationDirection.CREOLE_TO_ENGLISH -> "ht-en"
            TranslationDirection.ENGLISH_TO_CREOLE -> "en-ht"
        }

        val requestJson = JSONObject().apply {
            put("text", text)
            put("direction", proxyDirection)
        }

        val request = proxyRequest("/v1/translate")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw GroqError.TranslationFailed("Empty response")

        if (!response.isSuccessful) {
            if (response.code == 401) throw GroqError.InvalidApiKey
            throw GroqError.TranslationFailed("HTTP ${response.code}: $body")
        }

        return try {
            JSONObject(body).getString("translation").trim()
        } catch (e: Exception) {
            throw GroqError.TranslationFailed("Failed to parse response: ${e.message}")
        }
    }
}

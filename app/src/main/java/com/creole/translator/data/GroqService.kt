package com.creole.translator.data

import com.creole.translator.BuildConfig
import com.creole.translator.model.GroqError
import com.creole.translator.model.TranslationDirection
import com.creole.translator.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class GroqService {

    private val apiKey: String = BuildConfig.GROQ_API_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://api.groq.com/openai/v1"

    fun isApiKeyValid(): Boolean = apiKey.isNotBlank() && apiKey != "YOUR_API_KEY_HERE"

    suspend fun processAudio(
        audioFile: File,
        direction: TranslationDirection
    ): TranslationResult = withContext(Dispatchers.IO) {
        if (!isApiKeyValid()) throw GroqError.InvalidApiKey

        val transcription = transcribeAudio(audioFile, direction.sourceLanguage)
        val translation = translateText(transcription, direction)

        TranslationResult(
            transcription = transcription,
            translation = translation,
            direction = direction
        )
    }

    private fun transcribeAudio(audioFile: File, language: String): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
            )
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("language", language)
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url("$baseUrl/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
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
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", direction.systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", text)
            })
        }

        val requestJson = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", messages)
            put("temperature", 0.3)
            put("max_tokens", 1024)
        }

        val requestBody = requestJson.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw GroqError.TranslationFailed("Empty response")

        if (!response.isSuccessful) {
            if (response.code == 401) throw GroqError.InvalidApiKey
            throw GroqError.TranslationFailed("HTTP ${response.code}: $body")
        }

        return try {
            JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) {
            throw GroqError.TranslationFailed("Failed to parse response: ${e.message}")
        }
    }

    suspend fun synthesizeSpeech(text: String): ByteArray = withContext(Dispatchers.IO) {
        if (!isApiKeyValid()) throw GroqError.InvalidApiKey

        val requestJson = JSONObject().apply {
            put("model", "canopylabs/orpheus-v1-english")
            put("input", text.take(200))  // API limit is 200 characters
            put("voice", "diana")
            put("response_format", "wav")
        }

        val requestBody = requestJson.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/audio/speech")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            if (response.code == 401) throw GroqError.InvalidApiKey
            throw GroqError.SpeechFailed("HTTP ${response.code}: $errorBody")
        }

        val contentType = response.body?.contentType()?.toString() ?: ""
        if (!contentType.contains("audio") && !contentType.contains("octet-stream")) {
            val body = response.body?.string() ?: ""
            throw GroqError.SpeechFailed("Unexpected response ($contentType): $body")
        }

        response.body?.bytes() ?: throw GroqError.SpeechFailed("Empty audio response")
    }
}

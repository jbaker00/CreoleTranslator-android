package com.creole.translator.model

import java.util.Date
import java.util.UUID

enum class TranslationDirection {
    CREOLE_TO_ENGLISH,
    ENGLISH_TO_CREOLE;

    val sourceLanguage: String get() = when (this) {
        CREOLE_TO_ENGLISH -> "ht"
        ENGLISH_TO_CREOLE -> "en"
    }

    val targetLanguage: String get() = when (this) {
        CREOLE_TO_ENGLISH -> "en"
        ENGLISH_TO_CREOLE -> "ht"
    }

    val sourceLabel: String get() = when (this) {
        CREOLE_TO_ENGLISH -> "Haitian Creole"
        ENGLISH_TO_CREOLE -> "English"
    }

    val targetLabel: String get() = when (this) {
        CREOLE_TO_ENGLISH -> "English"
        ENGLISH_TO_CREOLE -> "Haitian Creole"
    }

    val sourceFlag: String get() = when (this) {
        CREOLE_TO_ENGLISH -> "\uD83C\uDDED\uD83C\uDDF9"  // 🇭🇹
        ENGLISH_TO_CREOLE -> "\uD83C\uDDFA\uD83C\uDDF8"  // 🇺🇸
    }

    val targetFlag: String get() = when (this) {
        CREOLE_TO_ENGLISH -> "\uD83C\uDDFA\uD83C\uDDF8"  // 🇺🇸
        ENGLISH_TO_CREOLE -> "\uD83C\uDDED\uD83C\uDDF9"  // 🇭🇹
    }

    val systemPrompt: String get() = when (this) {
        CREOLE_TO_ENGLISH ->
            "You are a professional translator specializing in Haitian Creole and English. " +
            "Translate the following Haitian Creole text to English. " +
            "Provide only the translation, no explanations or additional text."
        ENGLISH_TO_CREOLE ->
            "You are a professional translator specializing in Haitian Creole and English. " +
            "Translate the following English text to Haitian Creole. " +
            "Provide only the translation, no explanations or additional text."
    }
}

data class TranslationResult(
    val transcription: String,
    val translation: String,
    val direction: TranslationDirection
)

data class TranslationEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val sourceText: String,
    val translatedText: String,
    val direction: TranslationDirection
)

sealed class GroqError : Exception() {
    object InvalidApiKey : GroqError() {
        override val message = "Invalid or missing Groq API key"
    }
    data class NetworkError(override val message: String) : GroqError()
    data class TranscriptionFailed(override val message: String) : GroqError()
    data class TranslationFailed(override val message: String) : GroqError()
    data class SpeechFailed(override val message: String) : GroqError()
    object InvalidResponse : GroqError() {
        override val message = "Invalid response from server"
    }
}

package com.creole.translator.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TTSProvider(val id: String) {
    GROQ("groq"),
    OPENAI("openai"),
    SYSTEM("system");

    companion object {
        fun from(id: String?) = entries.find { it.id == id }
    }
}

class VoiceSettings(context: Context) {

    companion object {
        val openAIVoices: List<Voice> = listOf(
            Voice("alloy",   "Alloy",   "Neutral & balanced"),
            Voice("echo",    "Echo",    "Warm & clear (male)"),
            Voice("fable",   "Fable",   "Expressive, British accent"),
            Voice("onyx",    "Onyx",    "Deep & authoritative (male)"),
            Voice("nova",    "Nova",    "Friendly & upbeat (female)"),
            Voice("shimmer", "Shimmer", "Soft & gentle (female)"),
        )

        val englishProviders = listOf(TTSProvider.OPENAI, TTSProvider.SYSTEM)
        val creoleProviders  = listOf(TTSProvider.OPENAI, TTSProvider.SYSTEM)

        fun displayName(provider: TTSProvider) = when (provider) {
            TTSProvider.GROQ   -> "OpenAI"  // GROQ kept for migration compat, treated as OPENAI
            TTSProvider.OPENAI -> "OpenAI"
            TTSProvider.SYSTEM -> "System Voice"
        }

        fun description(provider: TTSProvider) = when (provider) {
            TTSProvider.GROQ   -> "Natural multilingual voices (6 options)"
            TTSProvider.OPENAI -> "Natural multilingual voices (6 options)"
            TTSProvider.SYSTEM -> "Built-in device voice — works offline"
        }
    }

    data class Voice(val id: String, val name: String, val description: String)

    private val prefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)

    private val _englishProvider = MutableStateFlow(
        TTSProvider.from(prefs.getString("englishProvider", null))
            ?.let { if (it == TTSProvider.GROQ) TTSProvider.OPENAI else it }
            ?: TTSProvider.OPENAI
    )
    val englishProvider: StateFlow<TTSProvider> = _englishProvider.asStateFlow()

    private val _creoleProvider = MutableStateFlow(
        TTSProvider.from(prefs.getString("creoleProvider", null)) ?: TTSProvider.OPENAI
    )
    val creoleProvider: StateFlow<TTSProvider> = _creoleProvider.asStateFlow()

    // Creole OpenAI voice (backward-compat key "openAIVoice"; default nova)
    private val _openAIVoice = MutableStateFlow(prefs.getString("openAIVoice", "nova") ?: "nova")
    val openAIVoice: StateFlow<String> = _openAIVoice.asStateFlow()

    // English OpenAI voice (separate from Creole)
    private val _englishOpenAIVoice = MutableStateFlow(prefs.getString("englishOpenAIVoice", "alloy") ?: "alloy")
    val englishOpenAIVoice: StateFlow<String> = _englishOpenAIVoice.asStateFlow()

    private val _englishPlaybackSpeed = MutableStateFlow(
        prefs.getFloat("englishPlaybackSpeed", 1.0f).toDouble()
    )
    val englishPlaybackSpeed: StateFlow<Double> = _englishPlaybackSpeed.asStateFlow()

    // Default 0.75 — Creole voices tend to speak fast
    private val _creolePlaybackSpeed = MutableStateFlow(
        prefs.getFloat("creolePlaybackSpeed", 0.75f).toDouble()
    )
    val creolePlaybackSpeed: StateFlow<Double> = _creolePlaybackSpeed.asStateFlow()

    fun setEnglishProvider(p: TTSProvider) {
        _englishProvider.value = p
        prefs.edit().putString("englishProvider", p.id).apply()
    }

    fun setCreoleProvider(p: TTSProvider) {
        _creoleProvider.value = p
        prefs.edit().putString("creoleProvider", p.id).apply()
    }

    fun setOpenAIVoice(id: String) {
        _openAIVoice.value = id
        prefs.edit().putString("openAIVoice", id).apply()
    }

    fun setEnglishOpenAIVoice(id: String) {
        _englishOpenAIVoice.value = id
        prefs.edit().putString("englishOpenAIVoice", id).apply()
    }

    fun setEnglishPlaybackSpeed(speed: Double) {
        _englishPlaybackSpeed.value = speed
        prefs.edit().putFloat("englishPlaybackSpeed", speed.toFloat()).apply()
    }

    fun setCreolePlaybackSpeed(speed: Double) {
        _creolePlaybackSpeed.value = speed
        prefs.edit().putFloat("creolePlaybackSpeed", speed.toFloat()).apply()
    }
}

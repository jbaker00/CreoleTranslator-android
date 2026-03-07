package com.creole.translator.data

import android.content.Context
import com.creole.translator.model.TranslationDirection
import com.creole.translator.model.TranslationEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TranslationHistoryManager(context: Context) {

    private val prefs = context.getSharedPreferences("translation_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val maxEntries = 50
    private val key = "entries"

    private val _entries = MutableStateFlow<List<TranslationEntry>>(emptyList())
    val entries: StateFlow<List<TranslationEntry>> = _entries.asStateFlow()

    init {
        _entries.value = loadFromPrefs()
    }

    fun addEntry(sourceText: String, translatedText: String, direction: TranslationDirection) {
        val entry = TranslationEntry(
            sourceText = sourceText,
            translatedText = translatedText,
            direction = direction
        )
        val updated = (listOf(entry) + _entries.value).take(maxEntries)
        _entries.value = updated
        saveToPrefs(updated)
    }

    fun deleteEntry(entry: TranslationEntry) {
        val updated = _entries.value.filter { it.id != entry.id }
        _entries.value = updated
        saveToPrefs(updated)
    }

    fun clearAll() {
        _entries.value = emptyList()
        prefs.edit().remove(key).apply()
    }

    private fun saveToPrefs(entries: List<TranslationEntry>) {
        val serializable = entries.map { entry ->
            mapOf(
                "id" to entry.id,
                "timestamp" to entry.timestamp.toString(),
                "sourceText" to entry.sourceText,
                "translatedText" to entry.translatedText,
                "direction" to entry.direction.name
            )
        }
        prefs.edit().putString(key, gson.toJson(serializable)).apply()
    }

    private fun loadFromPrefs(): List<TranslationEntry> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Map<String, String>>>() {}.type
            val raw: List<Map<String, String>> = gson.fromJson(json, type)
            raw.mapNotNull { map ->
                try {
                    TranslationEntry(
                        id = map["id"] ?: return@mapNotNull null,
                        timestamp = map["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
                        sourceText = map["sourceText"] ?: return@mapNotNull null,
                        translatedText = map["translatedText"] ?: return@mapNotNull null,
                        direction = TranslationDirection.valueOf(
                            map["direction"] ?: TranslationDirection.CREOLE_TO_ENGLISH.name
                        )
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

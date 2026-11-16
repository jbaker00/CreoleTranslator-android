package com.creole.translator.model

import java.util.Date
import java.util.UUID

/** A language the app translates. Haitian Creole is always one side of a pair. Mirrors iOS `Language`. */
enum class Language(val code: String) {
    CREOLE("ht"),
    ENGLISH("en"),
    SPANISH("es");

    /** The language's own name for itself — what its speakers look for. */
    val nativeName: String get() = when (this) {
        CREOLE -> "Kreyòl"
        ENGLISH -> "English"
        SPANISH -> "Español"
    }

    val englishName: String get() = when (this) {
        CREOLE -> "Creole"
        ENGLISH -> "English"
        SPANISH -> "Spanish"
    }

    /** What a Kreyòl speaker calls the language ("Panyòl", not "Español"). */
    val kreyolName: String get() = when (this) {
        CREOLE -> "Kreyòl"
        ENGLISH -> "Anglè"
        SPANISH -> "Panyòl"
    }

    /**
     * No one flag stands for English or Spanish, so the flag is the phone's
     * country when that country speaks the language (the country in the
     * phone's language setting wins over its region), else 🇺🇸 / 🇲🇽.
     */
    val flag: String get() = when (this) {
        CREOLE -> "\uD83C\uDDED\uD83C\uDDF9"  // 🇭🇹
        ENGLISH -> regionFlag("en", ENGLISH_REGIONS) ?: "\uD83C\uDDFA\uD83C\uDDF8"  // 🇺🇸
        SPANISH -> regionFlag("es", SPANISH_REGIONS) ?: "\uD83C\uDDF2\uD83C\uDDFD"  // 🇲🇽
    }

    companion object {
        val ENGLISH_REGIONS = setOf("US", "CA", "GB", "AU", "NZ", "IE", "JM", "BS", "BB", "TT", "BZ", "GY", "ZA", "NG", "GH", "KE", "LR")
        val SPANISH_REGIONS = setOf("MX", "DO", "CL", "CO", "AR", "PE", "VE", "EC", "GT", "CU", "BO", "HN", "PY", "SV", "NI", "CR", "PA", "UY", "PR", "ES")

        fun fromCode(code: String): Language? = entries.firstOrNull { code.startsWith(it.code) }

        /** Phone languages, most preferred first. */
        private fun phoneLocales(): List<java.util.Locale> {
            val list = android.os.LocaleList.getDefault()
            return (0 until list.size()).map { list[it] }
        }

        /** Spanish is the phone's first language. */
        val phoneIsSpanish: Boolean
            get() = (phoneLocales().firstOrNull() ?: java.util.Locale.getDefault()).language == "es"

        /**
         * Whether a fresh install opens on Kreyòl + Español instead of Kreyòl +
         * English: Spanish is the phone's first language, or the phone's region
         * is a Spanish-speaking country whatever its language.
         */
        val phoneSuggestsSpanish: Boolean
            get() = phoneIsSpanish ||
                java.util.Locale.getDefault().country.uppercase() in SPANISH_REGIONS ||
                (phoneLocales().firstOrNull()?.country?.uppercase() in SPANISH_REGIONS)

        private fun regionFlag(code: String, regions: Set<String>): String? {
            val locales = phoneLocales()
            val fromLanguages = locales.firstOrNull { it.language == code && it.country.uppercase() in regions }?.country
            val deviceRegion = (locales.firstOrNull() ?: java.util.Locale.getDefault()).country.uppercase()
            val region = (fromLanguages ?: deviceRegion.takeIf { it in regions })?.uppercase() ?: return null
            return region.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        }
    }
}

/** The four supported directions; Creole is always one side. Mirrors iOS `TranslationDirection`. */
enum class TranslationDirection(val source: Language, val target: Language) {
    CREOLE_TO_ENGLISH(Language.CREOLE, Language.ENGLISH),
    ENGLISH_TO_CREOLE(Language.ENGLISH, Language.CREOLE),
    CREOLE_TO_SPANISH(Language.CREOLE, Language.SPANISH),
    SPANISH_TO_CREOLE(Language.SPANISH, Language.CREOLE);

    val sourceLanguage: String get() = source.code
    val targetLanguage: String get() = target.code
    val sourceLabel: String get() = source.englishName
    val targetLabel: String get() = target.englishName
    val sourceFlag: String get() = source.flag
    val targetFlag: String get() = target.flag

    /** The non-Creole language of the pair. */
    val partner: Language get() = if (source == Language.CREOLE) target else source

    /** Proxy direction code ("ht-en", "es-ht", …) — the system prompt lives server-side. */
    val proxyDirection: String get() = "${source.code}-${target.code}"

    val reversed: TranslationDirection get() = of(target, source) ?: this

    /** Same side of the conversation, other partner language (English → Creole becomes Spanish → Creole). */
    fun withPartner(partner: Language): TranslationDirection {
        if (partner == Language.CREOLE) return this
        return (if (source == Language.CREOLE) of(Language.CREOLE, partner) else of(partner, Language.CREOLE)) ?: this
    }

    companion object {
        fun of(source: Language, target: Language): TranslationDirection? =
            entries.firstOrNull { it.source == source && it.target == target }

        fun fromProxy(code: String): TranslationDirection? {
            val parts = code.split("-")
            if (parts.size != 2) return null
            val s = Language.entries.firstOrNull { it.code == parts[0] } ?: return null
            val t = Language.entries.firstOrNull { it.code == parts[1] } ?: return null
            return of(s, t)
        }
    }
}

data class TranslationResult(
    val transcription: String,
    val translation: String,
    val direction: TranslationDirection,
    /** Set when the proxy captured this translation for QA; null for overrides and older proxies. */
    val sampleId: String? = null,
    /** Model's self-reported 1–5 confidence, null if it didn't report one. */
    val confidence: Int? = null
)

enum class TranslationSource(val wire: String) { VOICE("voice"), TYPED("typed") }

enum class FeedbackRating(val wire: String) { UP("up"), DOWN("down") }

enum class FeedbackTarget(val wire: String) { TRANSLATION("translation"), STT("stt") }

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
    /** gpt-transcribe declines rather than guesses, so an empty transcript
     * means "heard nothing usable" — shown as a status, not an error. */
    object NothingHeard : GroqError() {
        override val message = "Didn't catch that — try again, a little closer to the microphone."
    }
}

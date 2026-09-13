package com.creole.translator.data

/**
 * Offline Haitian Creole vs English detector. No network, no tokens.
 *
 * Scores a text by counting hits against two small lexicons of very
 * high-frequency function words (the closed class that shows up in nearly
 * every sentence and that the two languages don't share), plus orthographic
 * cues that only occur in Creole. Anything short or ambiguous is UNKNOWN so
 * the caller keeps the user's selected direction — a wrong auto-flip is
 * worse than no flip.
 *
 * Pure Kotlin so it can be unit-tested without Android.
 */
object LanguageDetector {

    enum class Language { HT, EN, UNKNOWN }

    data class Result(val language: Language, val htScore: Int, val enScore: Int) {
        val margin: Int get() = kotlin.math.abs(htScore - enScore)
    }

    // Creole function words and markers. Deliberately excludes words that are
    // also common in English ("a", "an", "men" ≈ but/hands, "sa"...) unless the
    // English sense is rare in translator input.
    private val HT_WORDS = setOf(
        "mwen", "m", "ou", "li", "nou", "yo", "w", "l", "n", "y",
        "ap", "pa", "te", "ta", "va", "pral", "fèk", "fek", "konn", "ka",
        "nan", "pou", "ak", "avèk", "avek", "sou", "anba", "devan", "dèyè", "deye",
        "se", "ki", "sa", "gen", "genyen", "fè", "fe", "ale", "vini", "wè", "we",
        "kote", "kijan", "kisa", "kilè", "kile", "kiyès", "kiyes", "poukisa", "konbyen",
        "bonjou", "bonswa", "mèsi", "mesi", "wi", "non", "tanpri", "souple", "eskize",
        "anpil", "tou", "byen", "mal", "bèl", "bel", "gwo", "piti", "jodi", "jodia",
        "la", "yon", "kèk", "kek", "chak", "tout", "lòt", "lot", "menm",
        "manje", "dlo", "lakay", "kay", "moun", "timoun", "fanm", "gason", "zanmi",
        "renmen", "vle", "bezwen", "kapab", "dwe", "konnen", "pale", "di", "tande",
        "kounye", "kounya", "apre", "anvan", "toujou", "janm", "jamè", "jame",
        "ede", "lajan", "travay", "lekòl", "lekol", "legliz", "machin", "twalèt", "twalet",
        "sak", "ni", "oswa", "paske", "si", "lè", "le", "depi",
    )

    // English function words / stopwords.
    private val EN_WORDS = setOf(
        "the", "is", "are", "am", "was", "were", "be", "been", "being",
        "you", "i", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them",
        "my", "your", "his", "its", "our", "their",
        "to", "of", "and", "or", "but", "in", "on", "at", "for", "with", "from", "by",
        "this", "that", "these", "those", "there", "here",
        "what", "where", "when", "why", "how", "who", "which",
        "do", "does", "did", "have", "has", "had", "can", "could", "will", "would", "should",
        "not", "no", "yes", "please", "thank", "thanks", "hello", "hi", "good", "morning",
        "want", "need", "know", "go", "going", "come", "see", "get", "make",
        "very", "much", "many", "some", "any", "all", "one", "two",
        "bathroom", "water", "food", "money", "work", "school", "house", "home",
    )

    // Letters/patterns that essentially never occur in English spelling.
    private val HT_ORTHOGRAPHY = Regex("[èòàÈÒÀ]|\\bou\\s+(ye|ka|te|ap|pa)\\b|\\b(m|w|l|n|y)\\s*'?\\s*(ap|te|pa|pral)\\b")

    // Words that nail it as Creole on their own even in a two-word input.
    private val HT_STRONG = setOf("mwen", "bonjou", "mèsi", "mesi", "kijan", "kote", "kisa", "poukisa",
        "konbyen", "twalèt", "twalet", "lakay", "avèk", "genyen", "tanpri", "souple", "kounye", "kounya")

    private val TOKEN = Regex("[\\p{L}']+")

    fun detect(text: String): Result {
        val tokens = TOKEN.findAll(text.lowercase()).map { it.value.trim('\'') }.filter { it.isNotEmpty() }.toList()
        if (tokens.isEmpty()) return Result(Language.UNKNOWN, 0, 0)

        var ht = 0
        var en = 0
        for (t in tokens) {
            if (t in HT_WORDS) ht += if (t in HT_STRONG) 3 else 1
            if (t in EN_WORDS) en += 1
        }
        ht += 2 * HT_ORTHOGRAPHY.findAll(text).count()

        val language = when {
            ht == 0 && en == 0 -> Language.UNKNOWN
            // Need a clear winner: at least 2 points of margin, or a strong
            // single cue with nothing on the other side.
            ht >= en + 2 || (ht >= 3 && en == 0) -> Language.HT
            en >= ht + 2 || (en >= 2 && ht == 0) -> Language.EN
            else -> Language.UNKNOWN
        }
        return Result(language, ht, en)
    }
}

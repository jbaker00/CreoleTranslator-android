package com.creole.translator.data

import com.creole.translator.data.LanguageDetector.Detected
import com.creole.translator.model.Language
import org.junit.Assert.assertEquals
import org.junit.Test

/** Behaviour of the iOS LanguageDetector.swift port (word lists and thresholds identical). */
class LanguageDetectorTest {

    private fun en(text: String) = LanguageDetector.detect(text, Language.ENGLISH)
    private fun es(text: String) = LanguageDetector.detect(text, Language.SPANISH)

    @Test
    fun creolePhrasesDetectedAsCreole() {
        listOf(
            "Bonjou, kijan ou ye?",
            "Mwen pa konnen, ou di mwen.",
            "Kote twalèt la ye?",
            "Mwen renmen ou anpil",
            "Nou pral lakay jodi a",
            "Li gen anpil lajan",
            "M ap boule",
            "Èske ou ka ede m?",
            "Konbyen sa koute?",
            "Mèsi anpil, zanmi mwen",
            "Kote lopital la ye?",
        ).forEach { assertEquals("expected CREOLE for: $it", Detected.CREOLE, en(it)) }
    }

    @Test
    fun englishPhrasesDetectedAsEnglish() {
        listOf(
            "Where is the bathroom?",
            "How are you doing?",
            "I don't know, you tell me",
            "It is not a lioness. It is a lion",
            "Thank you very much",
            "We are going to the school tomorrow",
            "Can you help me please?",
            "How much does this cost?",
            "Turn left at the corner.",
        ).forEach { assertEquals("expected ENGLISH for: $it", Detected.ENGLISH, en(it)) }
    }

    @Test
    fun spanishWithoutKnownWordsFallsBack() {
        // No listed Spanish word: the caller keeps the last direction used.
        assertEquals(Detected.UNKNOWN, es("Doble a la izquierda en la esquina."))
    }

    @Test
    fun spanishPhrasesDetectedAsSpanishInTheSpanishPair() {
        listOf(
            "¿Dónde está el hospital?",
            "Hola, ¿cómo estás?",
            "Muchas gracias por la ayuda",
            "¿Cuánto cuesta esto?",
            "Necesito un médico",
        ).forEach { assertEquals("expected SPANISH for: $it", Detected.SPANISH, es(it)) }
    }

    @Test
    fun creoleStillWinsInTheSpanishPair() {
        // Words Creole shares with Spanish ("la", "se", "te"…) don't vote, but the rest do.
        listOf("Kote lopital la ye?", "Mwen pa konnen", "Bonjou, kijan ou ye?").forEach {
            assertEquals("expected CREOLE for: $it", Detected.CREOLE, es(it))
        }
    }

    @Test
    fun oneWordDecidesOnlyWhenStrong() {
        assertEquals(Detected.CREOLE, en("Bonjou"))
        assertEquals(Detected.ENGLISH, en("Hello"))
        assertEquals(Detected.SPANISH, es("Gracias"))
        assertEquals(Detected.CREOLE, en("mèsi"))
        assertEquals(Detected.UNKNOWN, en("Continue"))
        assertEquals(Detected.UNKNOWN, en("Paris"))
    }

    @Test
    fun shortPhrasesDecideOnOneNonWeakWord() {
        assertEquals(Detected.ENGLISH, en("Go home"))
        // One weak English word ("on" could be Creole "yon") — never enough for a short phrase.
        assertEquals(Detected.UNKNOWN, en("on Paris"))
    }

    @Test
    fun ambiguousOrEmptyIsUnknown() {
        listOf("", "   ", "OK?", "12345").forEach {
            val expected = if (it == "OK?") Detected.ENGLISH else Detected.UNKNOWN // "ok" is a strong English word
            assertEquals("for: '$it'", expected, en(it))
        }
    }

    @Test
    fun codeSwitchingSplitStaysUnknown() {
        // A 3–2-style split must not clear the strict > 60% majority.
        assertEquals(Detected.UNKNOWN, en("Jodia fem 4 jou back to the gym"))
    }

    @Test
    fun answerIsNeverTheThirdLanguage() {
        // Spanish in the English pair is not "SPANISH" — the caller offers a pair switch instead.
        val r = en("¿Dónde está el hospital?")
        assert(r != Detected.SPANISH)
        assertEquals(Detected.SPANISH, es("¿Dónde está el hospital?"))
    }

    @Test
    fun decomposedAccentsCountLikeComposed() {
        // "e" + combining grave, as some keyboards send it.
        assertEquals(Detected.CREOLE, en("mèsi"))
    }
}

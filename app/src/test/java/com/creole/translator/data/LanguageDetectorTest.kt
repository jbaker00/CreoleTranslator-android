package com.creole.translator.data

import com.creole.translator.data.LanguageDetector.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {

    private fun lang(text: String) = LanguageDetector.detect(text).language

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
            "Se pa yon lyon fi. Se yon lyon.",
            "Konbyen sa koute?",
            "Mèsi anpil, zanmi mwen",
        ).forEach { assertEquals("expected HT for: $it", Language.HT, lang(it)) }
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
            "I used to live there",
        ).forEach { assertEquals("expected EN for: $it", Language.EN, lang(it)) }
    }

    @Test
    fun ambiguousOrEmptyIsUnknown() {
        listOf("", "   ", "Beautiful", "Continue", "Tired", "OK", "12345", "Paris").forEach {
            assertEquals("expected UNKNOWN for: '$it'", Language.UNKNOWN, lang(it))
        }
    }

    @Test
    fun mixedTextLeansOnTheStrongerSignal() {
        // Garbled voice input with English bleed-through: mostly Creole tokens still win.
        assertEquals(Language.HT, lang("Mwen pa konnen sa, put it in the trash, mwen di ou"))
        // English sentence with one Creole word quoted stays English.
        assertEquals(Language.EN, lang("What does the word bonjou mean in the morning?"))
    }
}

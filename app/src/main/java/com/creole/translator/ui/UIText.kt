package com.creole.translator.ui

import com.creole.translator.model.Language

/**
 * Interface text, mirroring iOS `UIText` (GroqService.swift) string for string.
 * Spanish when the phone's first language is Spanish, English otherwise.
 * Strings a Creole speaker must read are written Kreyòl-first at the call site
 * (there is no Haitian Creole phone language to follow). The Spanish and
 * Kreyòl copy still needs a native-speaker review (iOS BACKLOG.md).
 */
object UIText {
    val isSpanish: Boolean get() = Language.phoneIsSpanish

    fun t(english: String, spanish: String): String = if (isSpanish) spanish else english

    /** Signpost text in the pair's other language, shown next to its Kreyòl twin. */
    fun pair(partner: Language, english: String, spanish: String): String =
        if (partner == Language.SPANISH) spanish else english

    fun listen(language: Language): String = when (language) {
        Language.CREOLE -> "Koute"
        Language.ENGLISH -> "Listen"
        Language.SPANISH -> "Escuchar"
    }
}

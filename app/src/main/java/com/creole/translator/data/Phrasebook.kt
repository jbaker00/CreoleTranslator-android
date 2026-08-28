package com.creole.translator.data

/**
 * Offline phrasebook — static, pre-translated common phrases.
 * Mirrors iOS Phrasebook.swift (52 entries, 6 categories).
 * No network/LLM calls — works offline and costs nothing.
 *
 * NOTE: drafted by AI, corrected by a native speaker. Review before
 * shipping any further changes, especially Emergency/Medical.
 */
data class PhrasebookEntry(
    val english: String,
    val creole: String
)

data class PhrasebookCategory(
    val name: String,
    val iconName: String, // Material icon name mapping
    val entries: List<PhrasebookEntry>
)

object Phrasebook {
    val categories: List<PhrasebookCategory> = listOf(
        PhrasebookCategory("Greetings", "waving_hand", listOf(
            PhrasebookEntry("Hello / Good day", "Allo bonjou"),
            PhrasebookEntry("Good evening", "Bonswa"),
            PhrasebookEntry("How are you?", "Kijan ou ye?"),
            PhrasebookEntry("I'm fine, thank you", "Mwen byen, mèsi"),
            PhrasebookEntry("Thank you", "Mèsi"),
            PhrasebookEntry("You're welcome", "Pa dekwa"),
            PhrasebookEntry("Please", "Sil vous plè"),
            PhrasebookEntry("Goodbye", "Orevwa"),
            PhrasebookEntry("My name is...", "Mwen rele..."),
            PhrasebookEntry("Nice to meet you", "Mwen byen kontan fè konesans ou"),
        )),
        PhrasebookCategory("Basics", "chat_bubble", listOf(
            PhrasebookEntry("Yes", "Wi"),
            PhrasebookEntry("No", "Non"),
            PhrasebookEntry("Excuse me", "Eskize m"),
            PhrasebookEntry("I don't understand", "Mwen pa konprann"),
            PhrasebookEntry("Do you speak English?", "Èske ou pale angle?"),
            PhrasebookEntry("How much does this cost?", "Konbyen sa koute?"),
            PhrasebookEntry("Where is the bathroom?", "Kote twalèt la ye sil vous plè"),
            PhrasebookEntry("One, two, three", "En, de, twa"),
        )),
        PhrasebookCategory("Directions", "signpost", listOf(
            PhrasebookEntry("Where is...?", "Kote... ye?"),
            PhrasebookEntry("Left", "Goch"),
            PhrasebookEntry("Right", "Dwat"),
            PhrasebookEntry("Straight ahead", "Toudwat"),
            PhrasebookEntry("Near / Far", "Toupre / Lwen"),
            PhrasebookEntry("Can you help me find...?", "Èske ou ka ede m jwenn...?"),
            PhrasebookEntry("I am lost", "Mwen pèdi"),
        )),
        PhrasebookCategory("Emergency", "warning", listOf(
            PhrasebookEntry("Help!", "Anmwe!"),
            PhrasebookEntry("Call the police", "Rele lapolis"),
            PhrasebookEntry("Call an ambulance", "Rele anbilans"),
            PhrasebookEntry("I need a doctor", "Mwen bezwen yon doktè"),
            PhrasebookEntry("Fire!", "Dife!"),
            PhrasebookEntry("It's an emergency", "Se yon ijans"),
            PhrasebookEntry("Where is the hospital?", "Kote lopital la?"),
            PhrasebookEntry("I am in danger", "Mwen nan danje"),
        )),
        PhrasebookCategory("Medical", "medical_services", listOf(
            PhrasebookEntry("I am sick", "Mwen malad"),
            PhrasebookEntry("I have a headache", "Mwen gen tèt fè mal"),
            PhrasebookEntry("I have a fever", "Mwen gen lafyèv"),
            PhrasebookEntry("It hurts here", "Li fè mal isit la"),
            PhrasebookEntry("I am allergic to...", "Mwen fè alèji ak..."),
            PhrasebookEntry("I need medicine", "Mwen bezwen medikaman"),
            PhrasebookEntry("Are you okay?", "Èske ou byen?"),
            PhrasebookEntry("I need water", "Mwen bezwen dlo"),
        )),
        PhrasebookCategory("Travel", "flight", listOf(
            PhrasebookEntry("Where is the airport?", "Kote ayewopò a ye sil vous plè"),
            PhrasebookEntry("I would like a taxi", "Mwen bezwen yon taksi sil vous plè"),
            PhrasebookEntry("How do I get to...?", "Kijan pou m rive nan...?"),
            PhrasebookEntry("What time is it?", "Ki lè li ye?"),
            PhrasebookEntry("I am a tourist", "Mwen se yon touris"),
            PhrasebookEntry("Can you recommend a restaurant?", "Èske ou ka rekòmande yon restoran?"),
            PhrasebookEntry("Safe travels", "Bon vwayaj"),
        )),
    )
}

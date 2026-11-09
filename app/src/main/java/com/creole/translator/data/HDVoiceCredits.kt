package com.creole.translator.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * AI ("HD") voice playback — OpenAI via the api-proxy, billed per request — is
 * metered per device: a few free plays a day, more for each rewarded ad.
 * Free plays reset at local midnight and are used first; ad plays carry over.
 * The count lives on the device, so the api-proxy's per-device and global
 * OpenAI TTS caps stay the real spend ceiling. Mirrors iOS HDVoiceCredits
 * (VoiceSettings.swift) — same constants and storage keys.
 */
class HDVoiceCredits(context: Context) {

    companion object {
        const val FREE_PLAYS_PER_DAY = 5
        const val PLAYS_PER_AD = 10
        /**
         * Grants allowed per day when no video can be shown (no-fill, airplane
         * mode, ad blocker). Keeps a genuine no-fill from punishing the user
         * without making "turn off Wi-Fi, tap Watch Video" an unlimited bypass.
         */
        const val NO_AD_GRANTS_PER_DAY = 1

        private const val DAY_KEY = "hdFreePlaysDay"
        private const val USED_KEY = "hdFreePlaysUsed"
        private const val BONUS_KEY = "hdBonusPlays"
        private const val NO_AD_DAY_KEY = "hdNoAdGrantDay"
        private const val NO_AD_USED_KEY = "hdNoAdGrantsUsed"
        private const val NO_AD_BLOCKED_KEY = "hdNoAdGrantsBlocked"

        private fun today(): String {
            val c = Calendar.getInstance()
            return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"
        }
    }

    private val prefs = context.getSharedPreferences("hd_voice_credits", Context.MODE_PRIVATE)

    private val freeUsedToday: Int
        get() = if (prefs.getString(DAY_KEY, null) == today()) prefs.getInt(USED_KEY, 0) else 0

    private val freePlaysLeft: Int get() = maxOf(0, FREE_PLAYS_PER_DAY - freeUsedToday)
    private val bonusPlaysLeft: Int get() = prefs.getInt(BONUS_KEY, 0)
    private fun computePlaysLeft() = freePlaysLeft + bonusPlaysLeft

    private val _playsLeft = MutableStateFlow(computePlaysLeft())
    val playsLeft: StateFlow<Int> = _playsLeft.asStateFlow()

    /** Call after an HD clip actually arrives. */
    fun consume() {
        if (freePlaysLeft > 0) {
            prefs.edit().putString(DAY_KEY, today()).putInt(USED_KEY, freeUsedToday + 1).apply()
        } else if (bonusPlaysLeft > 0) {
            prefs.edit().putInt(BONUS_KEY, bonusPlaysLeft - 1).apply()
        }
        refresh()
    }

    fun grantAdReward() {
        prefs.edit().putInt(BONUS_KEY, bonusPlaysLeft + PLAYS_PER_AD).apply()
        refresh()
    }

    private val noAdGrantsToday: Int
        get() = if (prefs.getString(NO_AD_DAY_KEY, null) == today()) prefs.getInt(NO_AD_USED_KEY, 0) else 0

    val canGrantWithoutAd: Boolean get() = noAdGrantsToday < NO_AD_GRANTS_PER_DAY

    fun recordGrantWithoutAd() {
        prefs.edit().putString(NO_AD_DAY_KEY, today()).putInt(NO_AD_USED_KEY, noAdGrantsToday + 1).apply()
    }

    /** Lifetime count of refused no-video grants, for analytics. */
    fun recordBlockedGrant(): Int {
        val total = prefs.getInt(NO_AD_BLOCKED_KEY, 0) + 1
        prefs.edit().putInt(NO_AD_BLOCKED_KEY, total).apply()
        return total
    }

    /** Re-reads storage so a new day's free plays show up (call on foreground). */
    fun refresh() {
        _playsLeft.value = computePlaysLeft()
    }
}

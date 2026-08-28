package com.creole.translator.ui

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.creole.translator.data.AnalyticsManager
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

// Shows an interstitial ad every INTERSTITIAL_INTERVAL successful translations,
// capped per session and spaced by a minimum time gap so rapid translators
// aren't hit with back-to-back full-screen ads. Mirrors iOS InterstitialAdManager.swift.
class InterstitialAdManager(private val context: Context) {

    companion object {
        const val INTERSTITIAL_INTERVAL = 4
        const val MAX_PER_SESSION = 6
        const val MIN_MS_BETWEEN_SHOWS = 120_000L
        // Android keeps the process alive across long pauses; without this,
        // heavy users hit the session cap once and never see interstitials again.
        const val SESSION_RESET_AFTER_BACKGROUND_MS = 30L * 60 * 1000

        private const val AD_UNIT_ID = "ca-app-pub-7871017136061682/7673641342"
    }

    private var interstitialAd: InterstitialAd? = null
    private var translationsSinceLastShow = 0
    private var shownThisSession = 0
    private var lastShownAtMs: Long? = null
    private var backgroundedAtMs: Long? = null

    init { preload() }

    fun appBackgrounded() {
        backgroundedAtMs = SystemClock.elapsedRealtime()
    }

    fun appForegrounded() {
        val bg = backgroundedAtMs ?: return
        if (SystemClock.elapsedRealtime() - bg < SESSION_RESET_AFTER_BACKGROUND_MS) return
        translationsSinceLastShow = 0
        shownThisSession = 0
        lastShownAtMs = null
    }

    /**
     * Call after each successful translation; shows an ad when due.
     * Pass [isSpeaking] so an ad never covers a spoken translation —
     * a skipped opportunity retries on the next translation.
     */
    fun translationCompleted(activity: Activity, isSpeaking: Boolean) {
        translationsSinceLastShow++
        val spacedOut = lastShownAtMs?.let {
            SystemClock.elapsedRealtime() - it >= MIN_MS_BETWEEN_SHOWS
        } ?: true
        if (translationsSinceLastShow < INTERSTITIAL_INTERVAL ||
            shownThisSession >= MAX_PER_SESSION ||
            isSpeaking ||
            !spacedOut
        ) {
            val reason = when {
                translationsSinceLastShow < INTERSTITIAL_INTERVAL -> "interval"
                shownThisSession >= MAX_PER_SESSION -> "max_per_session"
                isSpeaking -> "is_speaking"
                else -> "min_spacing"
            }
            AnalyticsManager.logInterstitialSkipped(reason)
            return
        }
        showIfReady(activity)
    }

    private fun preload() {
        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        // Count only when the ad actually reaches the screen, so a
                        // failed presentation doesn't burn a session slot.
                        override fun onAdShowedFullScreenContent() {
                            shownThisSession++
                            translationsSinceLastShow = 0
                            AnalyticsManager.logInterstitialShown(translationsSinceLastShow, shownThisSession)
                            lastShownAtMs = SystemClock.elapsedRealtime()
                        }
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            preload()
                        }
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            interstitialAd = null
                            preload()
                        }
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun showIfReady(activity: Activity) {
        interstitialAd?.show(activity) ?: preload()
    }
}

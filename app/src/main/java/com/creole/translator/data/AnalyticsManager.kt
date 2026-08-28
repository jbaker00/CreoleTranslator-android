package com.creole.translator.data

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.content.pm.PackageManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Mirrors iOS FirebaseAnalytics usage.
 * All events include platform="android" so you can split Android vs iOS in the console.
 * Safe to call even if Firebase isn't configured — falls back to Log.d.
 */
object AnalyticsManager {

    private const val TAG = "Analytics"
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var initialized = false

    private var appVersion: String = "unknown"
    private fun getAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) { "unknown" }
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appVersion = getAppVersion(context)
        try {
            firebaseAnalytics = Firebase.analytics
            // Set user properties once — lets you filter Android vs iOS
            firebaseAnalytics?.setUserProperty("os_platform", "android")
            firebaseAnalytics?.setUserProperty("os_version", Build.VERSION.RELEASE ?: "unknown")
            firebaseAnalytics?.setUserProperty("app_version", appVersion)
            Log.d(TAG, "Firebase Analytics initialized version=$appVersion")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase init failed (using Log-only): ${e.message}")
        }
    }

    private fun logEvent(name: String, params: Bundle) {
        // Always add common params
        params.putString("platform", "android")
        params.putString("os_version", Build.VERSION.RELEASE ?: "unknown")
        params.putString("app_version", appVersion)
        try {
            firebaseAnalytics?.logEvent(name, params)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase log failed: ${e.message}")
        }
        // Also Logcat so you can verify in Android Studio Logcat filter "Analytics"
        Log.d(TAG, "event=$name params=${bundleToString(params)}")
    }

    private fun bundleToString(b: Bundle): String =
        b.keySet().joinToString(", ") { k -> "$k=${b.getString(k) ?: b.get(k).toString()}" }

    // --- Translation events ---
    // Called on every successful translation — this is your core "how many translations" metric
    fun logTranslation(direction: String, charLength: Int, isVoice: Boolean, success: Boolean) {
        val p = Bundle().apply {
            putString("direction", direction) // ht-en or en-ht
            putInt("char_length", charLength)
            putString("input_mode", if (isVoice) "voice" else "text")
            putString("result", if (success) "success" else "failure")
        }
        logEvent("translation", p)
    }

    fun logTranslationFailed(direction: String, isVoice: Boolean, error: String) {
        val p = Bundle().apply {
            putString("direction", direction)
            putString("input_mode", if (isVoice) "voice" else "text")
            putString("error", error.take(100))
        }
        logEvent("translation_failed", p)
    }

    // --- Ad events ---
    fun logInterstitialShown(translationCount: Int, shownThisSession: Int) {
        val p = Bundle().apply {
            putInt("translation_count", translationCount)
            putInt("shown_this_session", shownThisSession)
        }
        logEvent("interstitial_shown", p)
    }

    fun logInterstitialSkipped(reason: String) {
        logEvent("interstitial_skipped", Bundle().apply { putString("reason", reason) })
    }

    fun logRewardedUnlock(via: String, voice: String) {
        val p = Bundle().apply {
            putString("via", via) // rewarded_ad, no_fill, present_failed
            putString("voice", voice)
        }
        logEvent("premium_voices_unlocked", p)
    }

    // --- Review prompt ---
    fun logReviewRequested(version: String) {
        logEvent("review_requested", Bundle().apply { putString("version", version) })
    }

    // --- TTS fallback — mirrors iOS tts_fallback_to_computer ---
    fun logTtsFallback(provider: String, language: String, reason: String) {
        val p = Bundle().apply {
            putString("provider", provider)
            putString("language", language)
            putString("reason", reason.take(100))
        }
        logEvent("tts_fallback_to_computer", p)
    }

    fun logScreenView(screenName: String) {
        logEvent("screen_view", Bundle().apply { putString("screen_name", screenName) })
    }
}

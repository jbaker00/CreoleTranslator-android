package com.creole.translator.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.ApplicableAgeFeaturesRequest
import com.google.android.play.agesignals.model.AgeSignalsStatus
import com.google.android.play.agesignals.model.ApplicableAgeFeature
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Age band from a Play Age Signals answer. Pure logic, unit-tested.
 *
 * Play shares a range (e.g. 13–15, 16–17, 18+) as [ageLower]/[ageUpper];
 * `ageUpper` is null for the open-ended top band and both are null when no age
 * was shared.
 */
enum class AgeBand {
    UNDER_13, TEEN, ADULT, UNKNOWN;

    val isMinor: Boolean get() = this == UNDER_13 || this == TEEN

    companion object {
        fun of(ageLower: Int?, ageUpper: Int?): AgeBand = when {
            ageUpper != null && ageUpper < 13 -> UNDER_13
            ageUpper != null && ageUpper < 18 -> TEEN
            ageLower != null && ageLower >= 18 -> ADULT
            else -> UNKNOWN
        }
    }
}

/**
 * Google Play Age Signals (California AB 1043 and Brazil's Digital ECA make
 * age sharing mandatory from January 2027).
 *
 * Play's terms allow the signal only for age-appropriate experiences required
 * by law, NOT for advertising, marketing, profiling or analytics. So this:
 *  - asks only where Play says sharing is mandatory (no prompt anywhere else),
 *  - uses the answer for one thing: a known minor gets non-personalized,
 *    age-rated ads (TFCD under 13, TFUA + RDP/NPA for every minor, max content
 *    rating G/T), which those privacy laws require,
 *  - keeps only the band on the device. Nothing is logged to analytics, sent
 *    to our proxy or used to pick ads.
 *
 * [applyCached] runs before the first ad request; [refresh] updates the band
 * once per launch.
 */
object AgeSignalsGate {
    private const val TAG = "AgeSignals"
    private const val PREFS = "age_signals"
    private const val KEY_BAND = "band"
    private const val TIMEOUT_MS = 3_000L

    @Volatile
    var band: AgeBand = AgeBand.UNKNOWN
        private set

    /** Apply the band saved on an earlier launch, so the first ad request is right. */
    fun applyCached(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BAND, null)
        band = saved?.let { runCatching { AgeBand.valueOf(it) }.getOrNull() } ?: AgeBand.UNKNOWN
        applyToMobileAds(band)
    }

    /**
     * Ask Play for the age band where sharing is mandatory, then call [onDone]
     * exactly once: when the answer is in, on any error, or after [TIMEOUT_MS]
     * so ads never wait on Play.
     */
    fun refresh(
        activity: Activity,
        manager: AgeSignalsManager? = null, // tests pass FakeAgeSignalsManager
        onDone: () -> Unit = {},
    ) {
        val finished = AtomicBoolean(false)
        val finish = { if (finished.compareAndSet(false, true)) onDone() }
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, TIMEOUT_MS)
        val fail = OnFailureListener { e -> Log.i(TAG, "age signals unavailable: ${e.message}"); finish() }

        try {
            val manager = manager ?: AgeSignalsManagerFactory.create(activity.applicationContext)
            manager.getApplicableAgeFeatures(ApplicableAgeFeaturesRequest.builder().build())
                .addOnSuccessListener { features ->
                    if (!features.features().contains(ApplicableAgeFeature.MANDATORY_SHARING)) {
                        finish() // not required here: never prompt, keep whatever we had
                        return@addOnSuccessListener
                    }
                    manager.requestAgeSignalsAccess(
                        AgeSignalsAccessRequest.builder().setActivity(activity).build()
                    ).addOnSuccessListener { access ->
                        if (access.ageSignalsStatus() != AgeSignalsStatus.SHARED) {
                            finish() // not shared / verification required: no actual knowledge
                            return@addOnSuccessListener
                        }
                        manager.checkAgeSignals(AgeSignalsRequest.builder().build())
                            .addOnSuccessListener { result ->
                                save(activity, AgeBand.of(result.ageLower(), result.ageUpper()))
                                finish()
                            }
                            .addOnFailureListener(fail)
                    }.addOnFailureListener(fail)
                }
                .addOnFailureListener(fail)
        } catch (e: Exception) {
            fail.onFailure(e) // no Play Store, old Play services, emulator without Play, ...
        }
    }

    private fun save(context: Context, newBand: AgeBand) {
        if (newBand == AgeBand.UNKNOWN) return // keep the last known band
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BAND, newBand.name).apply()
        band = newBand
        applyToMobileAds(newBand)
    }

    private fun applyToMobileAds(band: AgeBand) {
        val builder = MobileAds.getRequestConfiguration().toBuilder()
        when (band) {
            AgeBand.UNDER_13 -> builder
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            AgeBand.TEEN -> builder
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED)
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
            AgeBand.ADULT, AgeBand.UNKNOWN -> builder
                .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED)
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_UNSPECIFIED)
        }
        MobileAds.setRequestConfiguration(builder.build())
    }
}

/**
 * Every ad request in the app goes through here. For a known minor it asks for
 * non-personalized ads: `npa` (EEA-style non-personalized) and `rdp` (US state
 * privacy laws' restricted data processing, e.g. CCPA's under-16 rule).
 */
object AdRequests {
    fun build(): AdRequest {
        val builder = AdRequest.Builder()
        if (AgeSignalsGate.band.isMinor) {
            val extras = Bundle().apply {
                putString("npa", "1")
                putInt("rdp", 1)
            }
            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }
        return builder.build()
    }
}

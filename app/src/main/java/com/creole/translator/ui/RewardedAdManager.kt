package com.creole.translator.ui

import android.app.Activity
import android.content.Context
import com.creole.translator.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Rewarded ad shown to unlock premium AI voices for 24 hours (see VoiceSettings).
// Mirrors iOS RewardedAdManager.swift.
class RewardedAdManager(private val context: Context) {

    companion object {
        // Google sample rewarded unit — new production units can take hours to
        // start serving, so debug builds use this to always get a fill.
        private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val PROD_AD_UNIT_ID = "ca-app-pub-7871017136061682/9102335090"
        private val AD_UNIT_ID = if (BuildConfig.DEBUG) TEST_AD_UNIT_ID else PROD_AD_UNIT_ID
    }

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    init { preload() }

    fun preload() {
        if (rewardedAd != null || isLoading) return
        isLoading = true
        RewardedAd.load(
            context,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    rewardedAd = ad
                    _isReady.value = true
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedAd = null
                    _isReady.value = false
                }
            }
        )
    }

    /**
     * Shows the rewarded ad. Returns false if no ad is available
     * (caller decides whether to grant the unlock anyway).
     * [onDismiss] fires after the ad closes — safe point to present UI.
     * [onPresentFailure] fires if the SDK could not put the ad on screen.
     */
    fun show(
        activity: Activity,
        onReward: () -> Unit,
        onDismiss: () -> Unit,
        onPresentFailure: () -> Unit,
    ): Boolean {
        // One-shot: drop our reference so a re-entrant show() can't re-present the same ad.
        val ad = rewardedAd ?: run {
            preload()
            return false
        }
        rewardedAd = null
        _isReady.value = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onDismiss()
                preload()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onPresentFailure()
                preload()
            }
        }
        ad.show(activity) { onReward() }
        return true
    }
}

package com.creole.translator.ui

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

// Shows an interstitial ad every INTERSTITIAL_INTERVAL successful translations per session.
// TODO: Replace AD_UNIT_ID with the Android interstitial unit from your AdMob dashboard.
class InterstitialAdManager(private val context: Context) {

    companion object {
        const val INTERSTITIAL_INTERVAL = 25
        private const val AD_UNIT_ID = "ca-app-pub-7871017136061682/7673641342" // AdMob test unit
    }

    private var interstitialAd: InterstitialAd? = null

    init { preload() }

    private fun preload() {
        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
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

    fun showIfReady(activity: Activity) {
        interstitialAd?.show(activity) ?: preload()
    }
}

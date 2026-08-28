package com.creole.translator.ui

import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

const val BANNER_AD_UNIT_ID = "ca-app-pub-7871017136061682/5853776375"

// Adaptive anchored banner — pays significantly more than fixed BANNER (320x50)
// and respects gesture navigation insets so ads don't sit in the swipe zone.
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Width in dp for adaptive sizing (screen width minus window insets is handled by scaffold)
    val screenWidthDp = configuration.screenWidthDp

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        factory = { ctx ->
            AdView(ctx).apply {
                val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    ctx, screenWidthDp
                )
                setAdSize(adaptiveSize)
                adUnitId = BANNER_AD_UNIT_ID
                adListener = object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w("BannerAd", "Failed to load: ${error.message} code=${error.code}")
                    }
                    override fun onAdLoaded() {
                        Log.d("BannerAd", "Loaded adaptive banner ${adaptiveSize.width}x${adaptiveSize.height}")
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        },
        update = { adView ->
            // Recompute on rotation / config change
            val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                context, screenWidthDp
            )
            if (adView.adSize?.width != adaptiveSize.width || adView.adSize?.height != adaptiveSize.height) {
                adView.setAdSize(adaptiveSize)
                adView.loadAd(AdRequest.Builder().build())
            }
        }
    )
}

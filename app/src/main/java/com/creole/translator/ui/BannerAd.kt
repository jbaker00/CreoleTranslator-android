package com.creole.translator.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.creole.translator.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

// Google's test unit in debug — clicking real ads on our own devices risks AdMob invalid-traffic flags.
private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
private const val PROD_BANNER_AD_UNIT_ID = "ca-app-pub-7871017136061682/5853776375"
private val BANNER_AD_UNIT_ID = if (BuildConfig.DEBUG) TEST_BANNER_AD_UNIT_ID else PROD_BANNER_AD_UNIT_ID

// Adaptive anchored banner — pays significantly more than fixed BANNER (320x50)
// and respects gesture navigation insets so ads don't sit in the swipe zone.
//
// AdMob "Site Behavior: Navigation" (Nov 2026, flagged on v1.1 only): ads must
// not sit where they could be mistaken for app navigation. The banner lives in
// its own visually separate strip — divider on top, distinct background,
// 8dp gap above the ad — so it never reads as part of the app's controls and
// a tap aimed at content can't land on it. Mirrors iOS ContentView's banner
// (Divider overlay + tertiarySystemBackground).
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        AdaptiveBanner()
    }
}

@Composable
private fun AdaptiveBanner() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Width in dp for adaptive sizing (screen width minus window insets is handled by scaffold)
    val screenWidthDp = configuration.screenWidthDp

    AndroidView(
        modifier = Modifier.fillMaxWidth(),
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

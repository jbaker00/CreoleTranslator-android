package com.creole.translator

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.android.play.core.review.ReviewManagerFactory
import com.creole.translator.data.AnalyticsManager
import com.creole.translator.ui.ConsentManager
import com.creole.translator.ui.HistoryScreen
import com.creole.translator.ui.PhrasebookScreen
import com.creole.translator.ui.InterstitialAdManager
import com.creole.translator.ui.MainScreen
import com.creole.translator.ui.MainViewModel
import com.creole.translator.ui.RewardedAdManager
import com.creole.translator.ui.Screen
import com.creole.translator.ui.SettingsScreen
import com.creole.translator.ui.theme.CreoleTranslatorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val interstitialAdManager by lazy { InterstitialAdManager(this) }
    private val rewardedAdManager by lazy { RewardedAdManager(this) }
    private lateinit var consentInformation: ConsentInformation

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Permission denied; MainScreen will show an error when user tries to record
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: lets BannerAd respect gesture nav insets
        WindowCompat.setDecorFitsSystemWindows(window, false)
        AnalyticsManager.init(this)

        // Predictive back is enabled via AndroidManifest android:enableOnBackInvokedCallback
        // Handle system back to navigate between our Screen enum instead of exiting
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (viewModel.currentScreen.value) {
                    Screen.HISTORY, Screen.SETTINGS, Screen.PHRASEBOOK -> viewModel.showMain()
                    Screen.MAIN -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        // UMP consent must be requested BEFORE MobileAds.initialize per Google
        requestConsentAndInitAds()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.interstitialEvent.collect {
                    interstitialAdManager.translationCompleted(
                        this@MainActivity,
                        isSpeaking = viewModel.isSpeaking.value
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reviewEvent.collect {
                    delay(1000)
                    val manager = ReviewManagerFactory.create(this@MainActivity)
                    manager.requestReviewFlow().addOnSuccessListener { info ->
                        manager.launchReviewFlow(this@MainActivity, info)
                    }
                }
            }
        }

        if (!viewModel.hasMicPermission()) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            CreoleTranslatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentScreen by viewModel.currentScreen.collectAsState()

                    when (currentScreen) {
                        Screen.MAIN -> MainScreen(viewModel)
                        Screen.HISTORY -> HistoryScreen(viewModel)
                        Screen.PHRASEBOOK -> PhrasebookScreen(viewModel)
                        Screen.SETTINGS -> SettingsScreen(viewModel, rewardedAdManager)
                    }
                }
            }
        }
    }

    private fun requestConsentAndInitAds() {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { _ ->
                    // Consent gathered (or not required) — safe to init ads.
                    initMobileAds()
                }
            },
            { _ ->
                // Consent info update failed — proceed with limited ads rather than no ads
                initMobileAds()
            }
        )
        // If consent was already gathered on a prior launch, canRequestAds is true
        // and we still need to init (loadAndShow will be no-op and still calls init via success above)
        // No extra init here to avoid double-init
    }

    private fun initMobileAds() {
        MobileAds.initialize(this) {}
        // Re-initialize signal for ConsentManager if needed elsewhere
        ConsentManager.onAdsInitialized()
    }

    override fun onStart() {
        super.onStart()
        interstitialAdManager.appForegrounded()
    }

    override fun onStop() {
        super.onStop()
        interstitialAdManager.appBackgrounded()
    }
}

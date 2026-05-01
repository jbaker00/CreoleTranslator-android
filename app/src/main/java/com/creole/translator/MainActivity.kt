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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.MobileAds
import com.creole.translator.ui.HistoryScreen
import com.creole.translator.ui.InterstitialAdManager
import com.creole.translator.ui.MainScreen
import com.creole.translator.ui.MainViewModel
import com.creole.translator.ui.Screen
import com.creole.translator.ui.SettingsScreen
import com.creole.translator.ui.theme.CreoleTranslatorTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val interstitialAdManager by lazy { InterstitialAdManager(this) }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Permission denied; MainScreen will show an error when user tries to record
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.interstitialEvent.collect {
                    interstitialAdManager.showIfReady(this@MainActivity)
                }
            }
        }

        // Request microphone permission upfront
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
                        Screen.SETTINGS -> SettingsScreen(viewModel)
                    }
                }
            }
        }
    }
}

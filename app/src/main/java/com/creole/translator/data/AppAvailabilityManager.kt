package com.creole.translator.data

import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val KEY_DISABLED = "android_app_disabled"
private const val KEY_MESSAGE = "android_disabled_message"
private const val DEFAULT_MESSAGE =
    "This app is no longer available. Please check the Play Store for the latest version."

/**
 * Android-only remote kill switch, backed by Firebase Remote Config.
 * The iOS app never reads these keys, so flipping them in the Firebase
 * console cannot affect iOS even though both apps share the same
 * Firebase project — this is intentionally not ported to iOS.
 */
object AppAvailabilityManager {

    private const val TAG = "AppAvailability"

    private val _isDisabled = MutableStateFlow(false)
    val isDisabled: StateFlow<Boolean> = _isDisabled.asStateFlow()

    private val _disabledMessage = MutableStateFlow(DEFAULT_MESSAGE)
    val disabledMessage: StateFlow<String> = _disabledMessage.asStateFlow()

    fun init() {
        try {
            val remoteConfig = Firebase.remoteConfig
            remoteConfig.setConfigSettingsAsync(
                remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }
            )
            remoteConfig.setDefaultsAsync(
                mapOf(KEY_DISABLED to false, KEY_MESSAGE to DEFAULT_MESSAGE)
            )
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    applyConfig(remoteConfig)
                } else {
                    Log.w(TAG, "Remote Config fetch failed: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote Config init failed (app stays enabled): ${e.message}")
        }
    }

    private fun applyConfig(remoteConfig: FirebaseRemoteConfig) {
        _isDisabled.value = remoteConfig.getBoolean(KEY_DISABLED)
        remoteConfig.getString(KEY_MESSAGE).takeIf { it.isNotBlank() }?.let {
            _disabledMessage.value = it
        }
    }
}

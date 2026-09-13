package com.creole.translator.data

import android.content.Context
import android.util.Log
import com.creole.translator.BuildConfig
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val KEY_DISABLED = "android_app_disabled"
private const val KEY_MESSAGE = "android_disabled_message"
private const val DEFAULT_MESSAGE =
    "This app is no longer available. Please check the Play Store for the latest version."
private const val HEARTBEAT_URL = "https://us-central1-jbaker-api-proxy.cloudfunctions.net/api/v1/heartbeat"

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

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
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

        // Fire on every launch, independent of the disable check above — once a
        // device is actually killed it can never reach a translate/transcribe
        // call again, so this is the only signal that tells the version-adoption
        // dashboard "this device updated and is now blocked."
        sendHeartbeat(context)
    }

    private fun applyConfig(remoteConfig: FirebaseRemoteConfig) {
        _isDisabled.value = remoteConfig.getBoolean(KEY_DISABLED)
        remoteConfig.getString(KEY_MESSAGE).takeIf { it.isNotBlank() }?.let {
            _disabledMessage.value = it
        }
    }

    private fun sendHeartbeat(context: Context) {
        try {
            val prefs = context.getSharedPreferences("proxy_prefs", Context.MODE_PRIVATE)
            val deviceId = prefs.getString("proxyDeviceId", null)
                ?: java.util.UUID.randomUUID().toString().also {
                    prefs.edit().putString("proxyDeviceId", it).apply()
                }
            val request = Request.Builder()
                .url(HEARTBEAT_URL)
                .addHeader("x-device-id", deviceId)
                .addHeader("x-app-version", BuildConfig.VERSION_CODE.toString())
                .addHeader("x-platform", "android")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d(TAG, "Heartbeat failed (non-fatal): ${e.message}")
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.d(TAG, "Heartbeat skipped: ${e.message}")
        }
    }
}

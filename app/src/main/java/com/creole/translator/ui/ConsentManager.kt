package com.creole.translator.ui

import android.app.Activity
import com.google.android.ump.UserMessagingPlatform

/**
 * Thin helper for UMP consent — mirrors iOS DataPrivacyConsent + ATT flow.
 * Call [showPrivacyOptions] from Settings to let users revoke/change consent.
 */
object ConsentManager {
    private var adsInitialized = false

    fun onAdsInitialized() {
        adsInitialized = true
    }

    fun showPrivacyOptions(activity: Activity, onComplete: (Boolean) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            onComplete(error == null)
        }
    }

    fun isAdsInitialized() = adsInitialized
}

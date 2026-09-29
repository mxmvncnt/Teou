package com.lorenzomarci.sosring

import android.app.Application
import com.google.android.material.color.DynamicColors

class SosRingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyNightMode(PrefsManager(this).themeMode)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

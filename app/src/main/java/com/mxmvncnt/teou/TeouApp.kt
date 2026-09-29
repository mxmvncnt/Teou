package com.mxmvncnt.teou

import android.app.Application
import com.google.android.material.color.DynamicColors

class TeouApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyNightMode(PrefsManager(this).themeMode)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

package com.mxmvncnt.teou.app

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.mxmvncnt.teou.data.PrefsManager

class TeouApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyNightMode(PrefsManager(this).themeMode)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

package com.lorenzomarci.sosring

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat

object ThemeManager {
    fun applyNightMode(mode: Int) {
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    @ColorInt
    fun color(context: Context, @AttrRes attribute: Int): Int {
        val value = TypedValue()
        check(context.theme.resolveAttribute(attribute, value, true)) {
            "Theme attribute 0x${attribute.toString(16)} is not defined"
        }
        return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
    }
}

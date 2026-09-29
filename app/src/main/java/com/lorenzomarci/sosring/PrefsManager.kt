package com.lorenzomarci.sosring

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject

// number is an opaque stable contact ID, never shown in the UI (legacy installs keep phone numbers as IDs).
data class VipContact(val name: String, val number: String, val locationEnabled: Boolean = false)

data class LocationLogEntry(val name: String, val number: String, val timestamp: Long, val type: String)

enum class AppPalette {
    INDACO, TEAL, ARGILLA, ARDESIA;

    companion object {
        fun fromStoredOrdinal(value: Int): AppPalette = values().getOrElse(value) { INDACO }
    }
}

class PrefsManager(context: Context) {
    // Keep the existing store and keys so upgrading preserves paired contacts and sharing consent.
    private val prefs = context.getSharedPreferences("sosring_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CONTACTS = "vip_contacts"
        private const val KEY_LOCATION_LOGS = "location_logs"
        private const val KEY_THEME_PALETTE = "theme_palette"
        private const val KEY_THEME_MODE = "theme_mode"

        fun applyLocationEnabledUpdate(contacts: List<VipContact>, number: String, enabled: Boolean): List<VipContact> =
            contacts.map { c -> if (PhoneUtils.matches(c.number, number)) c.copy(locationEnabled = enabled) else c }
    }

    var themePalette: AppPalette
        get() = AppPalette.fromStoredOrdinal(prefs.getInt(KEY_THEME_PALETTE, AppPalette.INDACO.ordinal))
        set(value) = prefs.edit().putInt(KEY_THEME_PALETTE, value.ordinal).apply()

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM).let { stored ->
            if (stored in listOf(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES,
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) stored else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

    fun getContacts(): List<VipContact> = try {
        val arr = JSONArray(prefs.getString(KEY_CONTACTS, "[]"))
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            VipContact(obj.getString("name"), obj.getString("number"), obj.optBoolean("locationEnabled", false))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveContacts(contacts: List<VipContact>) {
        val arr = JSONArray()
        contacts.forEach { c ->
            arr.put(JSONObject().put("name", c.name).put("number", c.number).put("locationEnabled", c.locationEnabled))
        }
        prefs.edit().putString(KEY_CONTACTS, arr.toString()).apply()
    }

    fun updateContactLocationEnabled(number: String, enabled: Boolean) {
        saveContacts(applyLocationEnabledUpdate(getContacts(), number, enabled))
    }

    fun addLocationLog(name: String, number: String, type: String) {
        val now = System.currentTimeMillis()
        val logs = getLocationLogs().filter { it.timestamp >= now - 30L * 24 * 60 * 60 * 1000 }
        val arr = JSONArray()
        (listOf(LocationLogEntry(name, number, now, type)) + logs).forEach { entry ->
            arr.put(JSONObject().put("name", entry.name).put("number", entry.number)
                .put("timestamp", entry.timestamp).put("type", entry.type))
        }
        prefs.edit().putString(KEY_LOCATION_LOGS, arr.toString()).apply()
    }

    fun getLocationLogs(): List<LocationLogEntry> = try {
        val arr = JSONArray(prefs.getString(KEY_LOCATION_LOGS, "[]"))
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            LocationLogEntry(obj.getString("name"), obj.getString("number"), obj.getLong("timestamp"), obj.getString("type"))
        }
    } catch (_: Exception) {
        emptyList()
    }
}

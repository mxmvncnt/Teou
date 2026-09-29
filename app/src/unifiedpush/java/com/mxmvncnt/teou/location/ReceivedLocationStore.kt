package com.mxmvncnt.teou.location

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import com.mxmvncnt.teou.messaging.Peer

data class ReceivedLocation(val lat: Double, val lon: Double, val receivedAt: Long)

class ReceivedLocationStore(context: Context) {
    private val prefs = context.getSharedPreferences("received_locations", Context.MODE_PRIVATE)

    fun requested(peer: Peer) {
        prefs.edit().putLong("request_${peer.idPub}", System.currentTimeMillis()).apply()
    }

    fun save(peer: Peer, lat: Double, lon: Double) {
        prefs.edit().putString("location_${peer.idPub}", JSONObject()
            .put("lat", lat).put("lon", lon).put("receivedAt", System.currentTimeMillis()).toString()).apply()
    }

    fun get(peer: Peer): ReceivedLocation? {
        return try {
            val json = JSONObject(prefs.getString("location_${peer.idPub}", null) ?: return null)
            val lat = json.getDouble("lat")
            val lon = json.getDouble("lon")
            val time = json.getLong("receivedAt")
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0 || time <= 0L) null
            else ReceivedLocation(lat, lon, time)
        } catch (_: Exception) { null }
    }

    fun unreachable(peer: Peer, location: ReceivedLocation, now: Long): Boolean =
        unansweredRequest(prefs.getLong("request_${peer.idPub}", 0L), location.receivedAt, now)

    fun remove(peer: Peer) {
        prefs.edit().remove("location_${peer.idPub}").remove("request_${peer.idPub}").apply()
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
}

internal fun unansweredRequest(requestedAt: Long, receivedAt: Long, now: Long): Boolean =
    requestedAt > receivedAt && now - requestedAt >= 60_000L

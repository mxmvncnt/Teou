package com.mxmvncnt.teou

import org.json.JSONObject

object P2pMessageFactory {
    const val TYPE_LOC_REQUEST = "pos_req"
    const val TYPE_LOC_RESPONSE = "pos_res"

    fun locRequest(now: Long = System.currentTimeMillis()): ByteArray =
        JSONObject().put("type", TYPE_LOC_REQUEST).put("ts", now).toString().toByteArray(Charsets.UTF_8)

    fun locResponse(lat: Double, lon: Double, accuracy: Double, now: Long = System.currentTimeMillis()): ByteArray =
        JSONObject().put("type", TYPE_LOC_RESPONSE).put("lat", lat).put("lon", lon)
            .put("acc", accuracy).put("ts", now).toString().toByteArray(Charsets.UTF_8)

    fun type(payload: ByteArray): String? = try {
        JSONObject(String(payload, Charsets.UTF_8)).optString("type", "").ifBlank { null }
    } catch (_: Exception) { null }

    fun timestamp(payload: ByteArray): Long? = try {
        val json = JSONObject(String(payload, Charsets.UTF_8))
        if (json.has("ts")) json.getLong("ts") else null
    } catch (_: Exception) { null }

    data class LocResponse(val lat: Double, val lon: Double, val accuracy: Double)

    fun parseLocResponse(payload: ByteArray): LocResponse? = try {
        val json = JSONObject(String(payload, Charsets.UTF_8))
        if (json.optString("type") != TYPE_LOC_RESPONSE) null else {
            val lat = json.getDouble("lat")
            val lon = json.getDouble("lon")
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) null
            else LocResponse(lat, lon, json.optDouble("acc", 0.0))
        }
    } catch (_: Exception) { null }
}

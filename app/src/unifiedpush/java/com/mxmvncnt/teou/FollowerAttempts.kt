package com.mxmvncnt.teou

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class FollowerAttempt(val idPub: String, val firstSeen: Long, val lastSeen: Long, val count: Int)

object FollowerAttemptPolicy {
    const val MIN_RECORD_INTERVAL_MS = 5L * 60_000L
    const val MAX_STORED = 20
    const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

    fun shouldRecord(lastRecorded: Long, now: Long): Boolean = now - lastRecorded >= MIN_RECORD_INTERVAL_MS
}

class FollowerAttempts(context: Context) {

    private val prefs = context.getSharedPreferences("follower_attempts", Context.MODE_PRIVATE)

    fun record(idPubB64: String, now: Long = System.currentTimeMillis()) {
        val attempts = all().toMutableList()
        val existing = attempts.indexOfFirst { it.idPub == idPubB64 }
        if (existing >= 0) {
            val current = attempts[existing]
            if (!FollowerAttemptPolicy.shouldRecord(current.lastSeen, now)) return
            attempts[existing] = current.copy(lastSeen = now, count = current.count + 1)
        } else {
            attempts.add(0, FollowerAttempt(idPubB64, now, now, 1))
        }
        val cutoff = now - FollowerAttemptPolicy.RETENTION_MS
        persist(attempts.filter { it.lastSeen >= cutoff }.take(FollowerAttemptPolicy.MAX_STORED))
    }

    fun all(): List<FollowerAttempt> {
        val json = prefs.getString(KEY_ATTEMPTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                FollowerAttempt(
                    idPub = obj.getString("idPub"),
                    firstSeen = obj.getLong("firstSeen"),
                    lastSeen = obj.getLong("lastSeen"),
                    count = obj.getInt("count")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun remove(idPubB64: String) {
        persist(all().filter { it.idPub != idPubB64 })
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun persist(attempts: List<FollowerAttempt>) {
        val arr = JSONArray()
        attempts.forEach { attempt ->
            arr.put(JSONObject().apply {
                put("idPub", attempt.idPub)
                put("firstSeen", attempt.firstSeen)
                put("lastSeen", attempt.lastSeen)
                put("count", attempt.count)
            })
        }
        prefs.edit().putString(KEY_ATTEMPTS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_ATTEMPTS = "attempts"
    }
}

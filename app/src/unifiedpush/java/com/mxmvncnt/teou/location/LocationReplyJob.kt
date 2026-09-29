package com.mxmvncnt.teou.location

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.mxmvncnt.teou.*
import com.mxmvncnt.teou.data.PrefsManager
import com.mxmvncnt.teou.messaging.*
import com.mxmvncnt.teou.util.PhoneUtils

class LocationReplyJob : JobService() {
    companion object {
        private const val TAG = "LocationReplyJob"
        private const val JOB_ID = 1791
        private const val NUMBER = "number"
        private const val ID_PUB = "id_pub"

        fun schedule(context: Context, peer: Peer) {
            val extras = PersistableBundle().apply {
                putString(NUMBER, peer.number)
                putString(ID_PUB, peer.idPub)
            }
            // ponytail: one pending fix at a time; use per-peer jobs if concurrent requests matter.
            val builder = JobInfo.Builder(JOB_ID, ComponentName(context, LocationReplyJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setExpedited(true)
            else builder.setOverrideDeadline(0L)
            try {
                val scheduler = context.getSystemService(JobScheduler::class.java)
                var result = scheduler.schedule(builder.build())
                if (result != JobScheduler.RESULT_SUCCESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Log.w(TAG, "Expedited reply unavailable; scheduling normal reply")
                    result = scheduler.schedule(builder.setExpedited(false).build())
                }
                if (result != JobScheduler.RESULT_SUCCESS) {
                    Log.w(TAG, "Location reply could not be scheduled")
                } else {
                    Log.i(TAG, "Location reply scheduled")
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Location job not available: ${e.message}", e)
            }
        }
    }

    private var helper: LocationHelper? = null
    @Volatile private var generation = 0

    override fun onStartJob(params: JobParameters): Boolean {
        Log.i(TAG, "Location reply job started")
        val token = ++generation
        val number = params.extras.getString(NUMBER) ?: return false
        val peer = PeerStore(this).get(number) ?: return false
        if (peer.idPub != params.extras.getString(ID_PUB) || !canShareWith(peer)) return false
        val location = LocationHelper(this)
        helper = location
        Handler(Looper.getMainLooper()).post {
            if (token == generation) location.requestSingleFix(object : LocationHelper.Callback {
                override fun onLocationReady(fix: Location) {
                    Thread {
                        try {
                            if (token == generation && PeerStore(this@LocationReplyJob).get(peer.number) == peer && canShareWith(peer)) {
                                P2pMessaging.sendLocation(this@LocationReplyJob, peer, fix.latitude, fix.longitude, fix.accuracy.toDouble())
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Location reply failed: ${e.message}")
                        } finally {
                            if (token == generation) jobFinished(params, false)
                        }
                    }.start()
                }

                override fun onLocationFailed() {
                    Log.w(TAG, "Location fix failed")
                    if (token == generation) jobFinished(params, false)
                }
            })
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        generation++
        helper?.stop()
        helper = null
        return false
    }

    private fun canShareWith(peer: Peer): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location reply blocked: background location permission missing")
            return false
        }
        return PrefsManager(this).getContacts().any { PhoneUtils.matches(it.number, peer.number) && it.locationEnabled }
    }
}

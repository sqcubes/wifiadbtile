package es.scub.wifiadb

import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.annotation.IntDef
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

internal class ADBManager(private val mContext: Context) {
    companion object {
        private const val TAG = "ADBManager"
        const val REFRESH_INTERVAL = 1000
        const val ADB_STATUS_STOPPING = 1
        const val ADB_STATUS_STOPPED = 2
        const val ADB_STATUS_STARTING = 3
        const val ADB_STATUS_STARTED = 4
        const val ADB_STATUS_NO_SU = 5
    }

    private var mOnStatusChangedListener: OnAdbStatusChangedListener? = null

    @IntDef(ADB_STATUS_STOPPING.toLong(), ADB_STATUS_STOPPED.toLong(), ADB_STATUS_STARTING.toLong(), ADB_STATUS_STARTED.toLong(), ADB_STATUS_NO_SU.toLong())
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    internal annotation class ADBStatus

    private var isAwaitingSU = false
    private var isAdbStarting = false
    private var isAdbStopping = false

    private var mAdbRefreshAsyncTask = AdbRefreshAsyncTask()

    private val mFirebaseAnalytics: FirebaseAnalytics

    init {
        this.mFirebaseAnalytics = FirebaseAnalytics.getInstance(mContext)
    }

    fun setOnStatusChangedListener(onStatusChangedListener: OnAdbStatusChangedListener?) {
        this.mOnStatusChangedListener = onStatusChangedListener
    }

    fun onStart() {
    }

    fun onResume() {
        resumeRefresh()
    }

    fun onPause() {
        pauseRefresh()
    }

    fun onStop() {
        pauseRefresh()
    }

    fun toggle() {
        Thread { internalToggle() }.start()
    }

    private fun resumeRefresh() {
        mAdbRefreshAsyncTask.cancel(true)
        mAdbRefreshAsyncTask = AdbRefreshAsyncTask()
        mAdbRefreshAsyncTask.execute()
    }

    private fun pauseRefresh() {
        mAdbRefreshAsyncTask.cancel(true)
    }

    fun onADBStatusChanged(status: ADBStatusResult) {
        if (!Looper.getMainLooper().isCurrentThread) {
            throw IllegalStateException("onADBStatusChanged() should be executed on the main thread")
        }

        if (mOnStatusChangedListener != null) {
            mOnStatusChangedListener!!.onADBStatusChanged(status)
        }
    }

    val adbStatus: ADBStatusResult
        get() {
            Log.d(TAG, "Receiving ADB Status...")
            if (Looper.getMainLooper().isCurrentThread) {
                throw IllegalStateException("getADBStatus() should not be executed on the main thread")
            }

            @ADBStatus var status: Int

            if (isAwaitingSU) {
                status = ADB_STATUS_NO_SU
                if (ADBUtility.hasSU()) {
                    isAwaitingSU = false
                }
            } else if (isAdbStarting) {
                status = ADB_STATUS_STARTING
                if (ADBUtility.isAdbStarted) {
                    status = ADB_STATUS_STARTED
                    isAdbStarting = false
                }
            } else if (isAdbStopping) {
                status = ADB_STATUS_STOPPING
                if (!ADBUtility.isAdbStarted) {
                    status = ADB_STATUS_STOPPED
                    isAdbStopping = false
                }
            } else if (ADBUtility.isAdbStarted) {
                status = ADB_STATUS_STARTED
            } else {
                status = ADB_STATUS_STOPPED
            }


            val adbStatusResult = getStatusResult(status)
            Log.d(TAG, String.format("Received ADB Status: %s", adbStatusResult.toString()))
            return adbStatusResult
        }

    private fun getStatusResult(@ADBStatus adbStatus: Int): ADBStatusResult {
        val ipAddress = ADBUtility.getIPAddress(mContext)
        return ADBStatusResult(adbStatus, ipAddress, ADBUtility.port)
    }

    fun internalToggle() {
        if (Looper.getMainLooper().isCurrentThread) {
            throw IllegalStateException("internalToggle() should not be executed on the main thread")
        }

        val started = isAdbStarting || ADBUtility.isAdbStarted
        if (started) {
            stopAdb()
        } else {
            startAdb()
        }
    }

    private fun startAdb() {
        isAdbStarting = true
        isAdbStopping = false
        publishStatus(ADB_STATUS_STARTING)

        var has_su = true
        try {
            ADBUtility.startAdb(false)
        } catch (e: NoSuperUserException) {
            has_su = false
            isAdbStarting = false
            isAdbStopping = false
            isAwaitingSU = true
            publishStatus(ADB_STATUS_NO_SU)
        }

        // log adb start event to Firebase
        val bundle = Bundle()
        bundle.putBoolean("has_su", has_su)
        mFirebaseAnalytics.logEvent("start_adb", bundle)

        resumeRefresh()
    }

    private fun stopAdb() {
        isAdbStarting = false
        isAdbStopping = true
        publishStatus(ADB_STATUS_STOPPING)
        try {
            ADBUtility.stopAdb()
        } catch (e: NoSuperUserException) {
            isAdbStarting = false
            isAdbStopping = false
            isAwaitingSU = true
            publishStatus(ADB_STATUS_NO_SU)
        }

        resumeRefresh()
    }

    private fun publishStatus(@ADBStatus adbStatus: Int) {
        val statusResult = getStatusResult(adbStatus)
        Handler(Looper.getMainLooper()).post { onADBStatusChanged(statusResult) }
    }

    internal interface OnAdbStatusChangedListener {
        fun onADBStatusChanged(status: ADBStatusResult)
    }

    internal inner class ADBStatusResult(@ADBStatus val adbStatus: Int, val adbip: String, val adbPort: Int) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val that = other as ADBStatusResult?
            return adbStatus == that!!.adbStatus && adbPort == that.adbPort && adbip == that.adbip
        }

        override fun hashCode(): Int {
            var result = adbStatus
            result = 31 * result + adbPort
            result = 31 * result + adbip.hashCode()
            return result
        }

        override fun toString(): String {
            return "ADBStatusResult{" +
                    "mADBStatus=" + adbStatus +
                    ", mADBPort=" + adbPort +
                    ", mADBIP='" + adbip + '\'' +
                    '}'
        }
    }

    private inner class AdbRefreshAsyncTask internal constructor() : AsyncTask<Void, ADBStatusResult, Void>() {
        private var mLastAdbStatus = -1

        override fun doInBackground(vararg voids: Void): Void? {
            while (!isCancelled) {
                val adbStatus = adbStatus
                val adbStatusHash = adbStatus.hashCode()
                if (adbStatusHash != mLastAdbStatus) {
                    mLastAdbStatus = adbStatusHash
                    publishProgress(adbStatus)
                }

                try {
                    Thread.sleep(REFRESH_INTERVAL.toLong())
                } catch (ignored: InterruptedException) {
                }

            }
            return null
        }

        override fun onProgressUpdate(vararg values: ADBStatusResult) {
            if (!isCancelled) {
                onADBStatusChanged(values[0])
            }
        }
    }
}

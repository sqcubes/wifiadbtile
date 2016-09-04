package es.scub.wifiadb

import android.content.Context
import android.graphics.drawable.Icon
import android.support.annotation.DrawableRes
import es.sqcub.wifiadb.R

internal class ADBPresenter(private val mContext: Context, private val mPresentingUI: ADBPresenter.ADBPresentingUI) : ADBManager.OnAdbStatusChangedListener {

    override fun onADBStatusChanged(status: ADBManager.ADBStatusResult) {
        val description: String
        @DrawableRes val iconRes: Int

        if (status.adbStatus == ADBManager.ADB_STATUS_NO_SU) {
            iconRes = R.drawable.ic_wifiadb_disabled
            description = mContext.getString(R.string.no_su)
        } else if (status.adbStatus == ADBManager.ADB_STATUS_STARTING) {
            iconRes = R.drawable.ic_wifiadb_enabled
            description = mContext.getString(R.string.starting)
        } else if (status.adbStatus == ADBManager.ADB_STATUS_STOPPING) {
            iconRes = R.drawable.ic_wifiadb_disabled
            description = mContext.getString(R.string.stopping)
        } else if (status.adbStatus == ADBManager.ADB_STATUS_STARTED) {
            iconRes = R.drawable.ic_wifiadb_enabled
            description = String.format("%s:%s", status.adbip, status.adbPort)
        } else {
            iconRes = R.drawable.ic_wifiadb_disabled
            description = mContext.getString(R.string.wifi_adb)
        }

        val icon = Icon.createWithResource(mContext, iconRes)
        mPresentingUI.onADBUIUpdate(icon, description)
    }

    internal interface ADBPresentingUI {
        fun onADBUIUpdate(icon: Icon, text: String)
    }
}

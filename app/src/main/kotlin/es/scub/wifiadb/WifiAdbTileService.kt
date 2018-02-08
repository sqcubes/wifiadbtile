package es.scub.wifiadb

import android.graphics.drawable.Icon
import android.service.quicksettings.TileService

class WifiAdbTileService : TileService(), ADBPresenter.ADBPresentingUI {
    private val mAdbManger: ADBManager by lazy {
        ADBManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        val adbPresenter = ADBPresenter(this, this)
        mAdbManger.setOnStatusChangedListener(adbPresenter)
    }

    override fun onTileAdded() {
        mAdbManger.onStart()
    }

    override fun onTileRemoved() {
        mAdbManger.onStop()
    }

    override fun onClick() {
        if (isLocked)
            unlockAndRun { mAdbManger.toggle() }
        else
            mAdbManger.toggle()
    }

    override fun onStartListening() {
        mAdbManger.onResume()
    }

    override fun onStopListening() {
        mAdbManger.onPause()
    }

    override fun onADBUIUpdate(icon: Icon, text: String) {
        qsTile.icon = icon
        qsTile.label = text
        qsTile.updateTile()
    }
}

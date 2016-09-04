package es.scub.wifiadb

import android.app.Activity
import android.graphics.drawable.Icon
import android.os.Bundle
import es.sqcub.wifiadb.R
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : Activity(), ADBPresenter.ADBPresentingUI {
    private val mAdbManager: ADBManager by lazy {
        ADBManager(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mAdbPresenter = ADBPresenter(this, this)
        mAdbManager.setOnStatusChangedListener(mAdbPresenter)
        imageButtonToggleAdb.setOnClickListener { onToggleAdbClick() }
    }

    override fun onStart() {
        super.onStart()
        mAdbManager.onStart()
    }

    override fun onResume() {
        super.onResume()
        mAdbManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        mAdbManager.onPause()
    }

    override fun onStop() {
        super.onStop()
        mAdbManager.onStop()
    }

    fun onToggleAdbClick() {
        mAdbManager.toggle()
    }

    override fun onADBUIUpdate(icon: Icon, text: String) {
        imageButtonToggleAdb.setImageIcon(icon)
        textDescription.text = text
    }
}

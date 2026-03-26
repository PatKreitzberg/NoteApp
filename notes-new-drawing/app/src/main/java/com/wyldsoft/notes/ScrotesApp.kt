package com.wyldsoft.notes

import android.app.Application
import android.os.Build
import com.onyx.android.sdk.rx.RxBaseAction
import com.onyx.android.sdk.utils.ResManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Application entry point. Initializes Onyx SDK resource manager and RxAction system,
 * and enables hidden API bypass on Android R+ for SDK compatibility.
 * Must be declared in AndroidManifest.xml as the application class.
 */
class ScrotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ResManager.init(this)
        RxBaseAction.init(this)
        checkHiddenApiBypass()
    }

    private fun checkHiddenApiBypass() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}
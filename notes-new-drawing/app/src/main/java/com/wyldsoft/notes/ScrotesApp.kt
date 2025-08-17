package com.wyldsoft.notes

import android.app.Application
import android.os.Build
import com.onyx.android.sdk.rx.RxBaseAction
import com.onyx.android.sdk.utils.ResManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

class ScrotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ResManager.init(this)
        RxBaseAction.init(this)
        checkHiddenApiBypass()
    }

    private fun checkHiddenApiBypass() {
        // Necessary for Onyx to compile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}
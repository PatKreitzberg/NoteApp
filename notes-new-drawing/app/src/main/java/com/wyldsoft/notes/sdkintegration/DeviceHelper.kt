package com.wyldsoft.notes.sdkintegration

import android.os.Build

object DeviceHelper {
    val isOnyxDevice: Boolean by lazy {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        manufacturer.contains("onyx") || brand.contains("onyx")
    }
}

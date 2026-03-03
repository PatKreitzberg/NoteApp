package com.wyldsoft.notes.sdkintegration.generic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.wyldsoft.notes.sdkintegration.BaseDeviceReceiver

class GenericDeviceReceiverWrapper : BaseDeviceReceiver() {
    private var screenOnListener: (() -> Unit)? = null
    private var receiver: BroadcastReceiver? = null

    override fun enable(context: Context, enable: Boolean) {
        if (enable) {
            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_ON) {
                        screenOnListener?.invoke()
                    }
                }
            }
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        } else {
            receiver?.let { context.unregisterReceiver(it) }
            receiver = null
        }
    }

    override fun setSystemNotificationPanelChangeListener(listener: (Boolean) -> Unit): BaseDeviceReceiver {
        // No-op on generic devices — no notification panel draw interference
        return this
    }

    override fun setSystemScreenOnListener(listener: () -> Unit): BaseDeviceReceiver {
        screenOnListener = listener
        return this
    }

    override fun cleanup() {
        receiver = null
        screenOnListener = null
    }
}

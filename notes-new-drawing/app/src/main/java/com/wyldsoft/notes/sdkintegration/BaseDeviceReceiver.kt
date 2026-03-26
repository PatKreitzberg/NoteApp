package com.wyldsoft.notes.sdkintegration

import android.content.Context

/**
 * Abstract interface for device-level system event listeners (notification panel,
 * screen-on). Decouples BaseDrawingActivity from the Onyx SDK's DeviceReceiver.
 * Concrete implementation: OnyxDeviceReceiverWrapper, which delegates to
 * GlobalDeviceReceiver (the Onyx BroadcastReceiver).
 */
abstract class BaseDeviceReceiver {
    abstract fun enable(context: Context, enable: Boolean)
    abstract fun setSystemNotificationPanelChangeListener(listener: (Boolean) -> Unit): BaseDeviceReceiver
    abstract fun setSystemScreenOnListener(listener: () -> Unit): BaseDeviceReceiver
    abstract fun cleanup()
}
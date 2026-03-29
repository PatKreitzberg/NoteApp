package com.wyldsoft.notes.touchhandling

import android.content.Context
import android.graphics.Rect
import com.onyx.android.sdk.api.device.epd.EpdController

/**
 * Utility for suppressing finger touch input on Onyx devices during pen drawing.
 * disableFingerTouch() sets a full-screen capacitive-touch-panel disable region
 * via EpdController so finger taps are ignored while the stylus is active.
 * enableFingerTouch() resets the region to re-allow finger input.
 *
 * Called by OnyxDrawingActivity's RawInputCallback in onBeginRawDrawing (disable)
 * and onEndRawDrawing (enable) to prevent accidental palm/finger marks.
 */
object TouchUtils {
    fun disableFingerTouch(context: Context) {
        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val rect = Rect(0, 0, width, height)
        val arrayRect: Array<Rect?> = arrayOf<Rect>(rect) as Array<Rect?>
        EpdController.setAppCTPDisableRegion(context, arrayRect)
    }

    fun enableFingerTouch(context: Context?) {
        EpdController.appResetCTPDisableRegion(context)
    }
}

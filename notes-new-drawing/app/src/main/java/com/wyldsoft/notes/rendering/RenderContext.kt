package com.wyldsoft.notes.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point

class RenderContext {
    @JvmField var paint = Paint()
    @JvmField var bitmap: Bitmap? = null
    @JvmField var canvas: Canvas? = null
    @JvmField var viewPoint: Point? = null
}

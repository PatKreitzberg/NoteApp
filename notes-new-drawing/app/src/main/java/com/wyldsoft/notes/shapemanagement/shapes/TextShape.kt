package com.wyldsoft.notes.shapemanagement.shapes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import com.wyldsoft.notes.rendering.RenderContext

/**
 * A shape that renders a text string at a fixed position on the canvas.
 * Position is stored as a single-point TouchPointList (x, y in note coordinates).
 * strokeWidth holds the font size in pixels; strokeColor holds the text color.
 */
class TextShape : Shape() {
    companion object {
        private const val TAG = "TextShape"
    }

    var text: String = ""
    var fontFamily: String = "sans-serif"

    override fun render(renderContext: RenderContext) {
        Log.d(TAG, "render text='$text' font=$fontFamily")
        val canvas = renderContext.canvas ?: return
        val pt = touchPointList?.points?.firstOrNull() ?: return
        val typeface = when (fontFamily) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        val paint = Paint().apply {
            isAntiAlias = true
            color = if (strokeColor != 0) strokeColor else Color.BLACK
            textSize = if (strokeWidth > 0f) strokeWidth else 32f
            this.typeface = typeface
            style = Paint.Style.FILL
        }
        canvas.drawText(text, pt.x, pt.y, paint)
    }
}

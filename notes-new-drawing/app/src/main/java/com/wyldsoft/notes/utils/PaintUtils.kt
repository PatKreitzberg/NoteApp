package com.wyldsoft.notes.utils

import android.graphics.Paint

fun createStrokePaint(): Paint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

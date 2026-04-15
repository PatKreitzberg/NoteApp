package com.wyldsoft.notes.data.mappers

import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import java.nio.ByteBuffer
import com.onyx.android.sdk.data.note.PenTexture
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.data.database.entities.ShapeEntity
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.shapemanagement.ShapeFactory
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import com.wyldsoft.notes.shapemanagement.shapes.TextShape
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ShapeMapper {
    private const val TAG = "ShapeMapper"
    private val json = Json { ignoreUnknownKeys = true }

    fun toEntity(shape: Shape, noteId: String): ShapeEntity {
        Log.d(TAG, "toEntity noteId=$noteId")
        val tpl = shape.touchPointList

        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()
        val pressures = mutableListOf<Float>()
        val tiltXs = mutableListOf<Int>()
        val tiltYs = mutableListOf<Int>()
        val timestamps = mutableListOf<Long>()

        if (tpl != null) {
            for (tp in tpl.points) {
                if (tp == null) continue
                xs.add(tp.x)
                ys.add(tp.y)
                pressures.add(tp.pressure)
                tiltXs.add(tp.tiltX)
                tiltYs.add(tp.tiltY)
                timestamps.add(tp.timestamp)
            }
        }

        val pointPairs = xs.zip(ys).map { listOf(it.first, it.second) }

        // Binary encoding: x(4) + y(4) + pressure(4) + tiltX(4) + tiltY(4) + timestamp(8) = 28 bytes/point
        val pointDataBuffer = ByteBuffer.allocate(xs.size * 28)
        for (i in xs.indices) {
            pointDataBuffer.putFloat(xs[i])
            pointDataBuffer.putFloat(ys[i])
            pointDataBuffer.putFloat(pressures[i])
            pointDataBuffer.putInt(tiltXs[i])
            pointDataBuffer.putInt(tiltYs[i])
            pointDataBuffer.putLong(timestamps[i])
        }

        val entityId = shape.entityId ?: NanoIdUtils.randomNanoId()
        shape.entityId = entityId

        val textContent = if (shape is TextShape) shape.text else null
        val fontSize = if (shape is TextShape) shape.strokeWidth else 32f
        val fontFamily = if (shape is TextShape) shape.fontFamily else "sans-serif"

        return ShapeEntity(
            id = entityId,
            noteId = noteId,
            type = shape.shapeType.toString(),
            points = json.encodeToString(pointPairs),
            strokeWidth = shape.strokeWidth,
            strokeColor = shape.strokeColor,
            penType = shape.penType.name,
            pressure = json.encodeToString(pressures.toList()),
            tiltX = json.encodeToString(tiltXs.toList()),
            tiltY = json.encodeToString(tiltYs.toList()),
            pointTimestamps = json.encodeToString(timestamps.toList()),
            timestamp = System.currentTimeMillis(),
            pointData = pointDataBuffer.array(),
            text = textContent,
            fontSize = fontSize,
            fontFamily = fontFamily,
            layer = shape.layer
        )
    }

    fun toShape(entity: ShapeEntity): Shape {
        Log.d(TAG, "toShape id=${entity.id}")
        val shapeType = entity.type.toIntOrNull() ?: run {
            Log.w(TAG, "Unrecognised shape type '${entity.type}' for id=${entity.id}, defaulting to PENCIL")
            ShapeFactory.SHAPE_PENCIL_SCRIBBLE
        }
        val shape = ShapeFactory.createShape(shapeType)

        shape.shapeType = shapeType
        shape.strokeColor = entity.strokeColor
        shape.strokeWidth = entity.strokeWidth
        shape.entityId = entity.id

        val penType = try {
            PenType.valueOf(entity.penType)
        } catch (e: IllegalArgumentException) {
            PenType.BALLPEN
        }
        shape.penType = penType

        if (penType == PenType.CHARCOAL_V2) {
            shape.texture = PenTexture.CHARCOAL_SHAPE_V2
        } else if (penType == PenType.CHARCOAL) {
            shape.texture = PenTexture.CHARCOAL_SHAPE_V1
        }

        val touchPointList = TouchPointList()
        val rawPointData = entity.pointData
        if (rawPointData != null && rawPointData.isNotEmpty()) {
            // Fast path: decode from binary blob (28 bytes per point)
            val buffer = ByteBuffer.wrap(rawPointData)
            val numPoints = rawPointData.size / 28
            repeat(numPoints) {
                val x = buffer.float
                val y = buffer.float
                val pressure = buffer.float
                val tiltX = buffer.int
                val tiltY = buffer.int
                val timestamp = buffer.long
                touchPointList.add(TouchPoint(x, y, pressure, 0f, tiltX, tiltY, timestamp))
            }
        } else {
            // Fallback: decode from legacy JSON fields (rows written before migration)
            val pointPairs: List<List<Float>> = json.decodeFromString(entity.points)
            val pressures: List<Float> = json.decodeFromString(entity.pressure)
            val tiltXs: List<Int> = json.decodeFromString(entity.tiltX)
            val tiltYs: List<Int> = json.decodeFromString(entity.tiltY)
            val timestamps: List<Long> = json.decodeFromString(entity.pointTimestamps)
            for (i in pointPairs.indices) {
                val x = pointPairs[i][0]
                val y = pointPairs[i][1]
                val pressure = pressures.getOrElse(i) { 0f }
                val tiltX = tiltXs.getOrElse(i) { 0 }
                val tiltY = tiltYs.getOrElse(i) { 0 }
                val timestamp = timestamps.getOrElse(i) { 0L }
                touchPointList.add(TouchPoint(x, y, pressure, 0f, tiltX, tiltY, timestamp))
            }
        }

        if (shape is TextShape) {
            shape.text = entity.text ?: ""
            shape.strokeWidth = entity.fontSize
            shape.fontFamily = entity.fontFamily
        }

        shape.layer = entity.layer
        shape.touchPointList = touchPointList
        shape.updateShapeRect()
        return shape
    }
}

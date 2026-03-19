package com.wyldsoft.notes.actions

import android.graphics.PointF
import com.wyldsoft.notes.data.repository.NoteRepository
import com.wyldsoft.notes.domain.models.Shape
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.pen.PenType
import com.wyldsoft.notes.shapemanagement.ShapesManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class SerializablePoint(val x: Float, val y: Float)

@Serializable
data class SerializableShape(
    val id: String,
    val type: String,
    val points: List<SerializablePoint>,
    val strokeWidth: Float,
    val strokeColor: Int,
    val penType: String,
    val pressure: List<Float>,
    val pointTimestamps: List<Long>,
    val timestamp: Long,
    val text: String? = null,
    val fontSize: Float = 32f,
    val fontFamily: String = "sans-serif",
    val layer: Int = 1
)

@Serializable
data class ActionRecord(
    val actionType: String,
    val noteId: String = "",
    val shapes: List<SerializableShape> = emptyList(),
    val secondaryShapes: List<SerializableShape> = emptyList(),
    val dx: Float = 0f,
    val dy: Float = 0f,
    val transformType: String? = null,
    val param: Float = 0f,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val shapeIds: List<String> = emptyList(),
    val fromLayer: Int = 0,
    val toLayer: Int = 0
)

object ActionSerializer {

    fun serialize(action: ActionInterface): String? {
        val record = when (action) {
            is DrawAction -> ActionRecord(
                actionType = "DRAW",
                noteId = action.noteId,
                shapes = listOf(action.shape.toSerializable())
            )
            is EraseAction -> ActionRecord(
                actionType = "ERASE",
                noteId = action.noteId,
                shapes = action.erasedShapes.map { it.toSerializable() }
            )
            is MoveAction -> ActionRecord(
                actionType = "MOVE",
                noteId = action.noteId,
                shapes = action.originalShapes.map { it.toSerializable() },
                dx = action.dx,
                dy = action.dy
            )
            is TransformAction -> ActionRecord(
                actionType = "TRANSFORM",
                noteId = action.noteId,
                shapes = action.originalShapes.map { it.toSerializable() },
                transformType = action.transformType.name,
                param = action.param,
                centerX = action.centerX,
                centerY = action.centerY
            )
            is EditTextAction -> ActionRecord(
                actionType = "EDIT_TEXT",
                noteId = action.noteId,
                shapes = listOfNotNull(action.oldShape?.toSerializable()),
                secondaryShapes = listOfNotNull(action.newShape?.toSerializable())
            )
            is TextFormattingAction -> ActionRecord(
                actionType = "TEXT_FORMATTING",
                noteId = action.noteId,
                shapes = action.beforeShapes.map { it.toSerializable() },
                secondaryShapes = action.afterShapes.map { it.toSerializable() }
            )
            is ConvertToTextAction -> ActionRecord(
                actionType = "CONVERT_TO_TEXT",
                noteId = action.noteId,
                shapes = action.originalShapes.map { it.toSerializable() },
                secondaryShapes = listOf(action.textShape.toSerializable())
            )
            is MoveLayerAction -> ActionRecord(
                actionType = "MOVE_LAYER",
                shapeIds = action.shapeIds,
                fromLayer = action.fromLayer,
                toLayer = action.toLayer
            )
            is SnapToLineAction -> ActionRecord(
                actionType = "SNAP_TO_LINE",
                noteId = action.noteId,
                shapes = listOf(action.originalShape.toSerializable()),
                secondaryShapes = listOf(action.lineShape.toSerializable())
            )
            else -> return null
        }
        return json.encodeToString(record)
    }

    fun deserialize(
        dataJson: String,
        noteRepository: NoteRepository,
        shapesManager: ShapesManager
    ): ActionInterface? {
        val record = try {
            json.decodeFromString<ActionRecord>(dataJson)
        } catch (e: Exception) {
            return null
        }
        return when (record.actionType) {
            "DRAW" -> {
                val shape = record.shapes.firstOrNull()?.toDomain() ?: return null
                DrawAction(record.noteId, shape, noteRepository, shapesManager)
            }
            "ERASE" -> {
                val shapes = record.shapes.map { it.toDomain() }
                EraseAction(record.noteId, shapes, noteRepository, shapesManager)
            }
            "MOVE" -> {
                val shapes = record.shapes.map { it.toDomain() }
                MoveAction(record.noteId, shapes, record.dx, record.dy, noteRepository, shapesManager)
            }
            "TRANSFORM" -> {
                val shapes = record.shapes.map { it.toDomain() }
                val transformType = TransformType.valueOf(record.transformType ?: return null)
                TransformAction(record.noteId, shapes, transformType, record.param, record.centerX, record.centerY, noteRepository, shapesManager)
            }
            "EDIT_TEXT" -> {
                val oldShape = record.shapes.firstOrNull()?.toDomain()
                val newShape = record.secondaryShapes.firstOrNull()?.toDomain()
                EditTextAction(record.noteId, oldShape, newShape, noteRepository, shapesManager)
            }
            "TEXT_FORMATTING" -> {
                val before = record.shapes.map { it.toDomain() }
                val after = record.secondaryShapes.map { it.toDomain() }
                TextFormattingAction(record.noteId, before, after, noteRepository, shapesManager)
            }
            "CONVERT_TO_TEXT" -> {
                val originals = record.shapes.map { it.toDomain() }
                val textShape = record.secondaryShapes.firstOrNull()?.toDomain() ?: return null
                ConvertToTextAction(record.noteId, originals, textShape, noteRepository, shapesManager)
            }
            "MOVE_LAYER" -> {
                MoveLayerAction(record.shapeIds, record.fromLayer, record.toLayer, noteRepository, shapesManager)
            }
            "SNAP_TO_LINE" -> {
                val original = record.shapes.firstOrNull()?.toDomain() ?: return null
                val line = record.secondaryShapes.firstOrNull()?.toDomain() ?: return null
                SnapToLineAction(record.noteId, original, line, noteRepository, shapesManager)
            }
            else -> null
        }
    }
}

private fun Shape.toSerializable() = SerializableShape(
    id = id,
    type = type.name,
    points = points.map { SerializablePoint(it.x, it.y) },
    strokeWidth = strokeWidth,
    strokeColor = strokeColor,
    penType = penType.name,
    pressure = pressure,
    pointTimestamps = pointTimestamps,
    timestamp = timestamp,
    text = text,
    fontSize = fontSize,
    fontFamily = fontFamily,
    layer = layer
)

private fun SerializableShape.toDomain() = Shape(
    id = id,
    type = ShapeType.valueOf(type),
    points = points.map { PointF(it.x, it.y) },
    strokeWidth = strokeWidth,
    strokeColor = strokeColor,
    penType = PenType.valueOf(penType),
    pressure = pressure,
    pointTimestamps = pointTimestamps,
    timestamp = timestamp,
    text = text,
    fontSize = fontSize,
    fontFamily = fontFamily,
    layer = layer
)

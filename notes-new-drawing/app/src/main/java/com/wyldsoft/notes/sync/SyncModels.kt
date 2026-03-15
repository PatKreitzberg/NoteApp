package com.wyldsoft.notes.sync

import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.database.entities.ShapeEntity
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.pen.PenType
import kotlinx.serialization.Serializable

@Serializable
data class NoteSyncDto(
    val note: NoteJson,
    val shapes: List<ShapeJson>,
    val notebookIds: List<String>
)

@Serializable
data class NoteJson(
    val id: String,
    val title: String,
    val parentNotebookId: String?,
    val folderId: String?,
    val settings: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val viewportScale: Float,
    val viewportScrollX: Float,
    val viewportScrollY: Float,
    val isPaginationEnabled: Boolean,
    val paperSize: String,
    val paperTemplate: String
)

@Serializable
data class ShapeJson(
    val id: String,
    val type: String,
    val points: List<PointJson>,
    val strokeWidth: Float,
    val strokeColor: Int,
    val penType: String,
    val pressure: List<Float>,
    val pointTimestamps: List<Long>,
    val timestamp: Long,
    val layer: Int = 1
)

@Serializable
data class PointJson(val x: Float, val y: Float)

@Serializable
data class NotebookJson(
    val id: String,
    val name: String,
    val folderId: String,
    val settings: String,
    val createdAt: Long,
    val modifiedAt: Long
)

@Serializable
data class FolderJson(
    val id: String,
    val name: String,
    val parentFolderId: String?,
    val createdAt: Long,
    val modifiedAt: Long
)

@Serializable
data class DeletionsManifest(val deletions: List<DeletionRecord>)

@Serializable
data class DeletionRecord(
    val entityId: String,
    val entityType: String,
    val deletedAt: Long
)

@Serializable
data class SyncStateManifest(val devices: Map<String, Long>)

// ---- Extension functions ----

fun NoteEntity.toNoteJson() = NoteJson(
    id = id,
    title = title,
    parentNotebookId = parentNotebookId,
    folderId = folderId,
    settings = settings,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    viewportScale = viewportScale,
    viewportScrollX = viewportScrollX,
    viewportScrollY = viewportScrollY,
    isPaginationEnabled = isPaginationEnabled,
    paperSize = paperSize,
    paperTemplate = paperTemplate
)

fun NoteJson.toEntity() = NoteEntity(
    id = id,
    title = title,
    parentNotebookId = parentNotebookId,
    folderId = folderId,
    settings = settings,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    viewportScale = viewportScale,
    viewportScrollX = viewportScrollX,
    viewportScrollY = viewportScrollY,
    isPaginationEnabled = isPaginationEnabled,
    paperSize = paperSize,
    paperTemplate = paperTemplate
)

fun ShapeEntity.toShapeJson() = ShapeJson(
    id = id,
    type = type.name,
    points = points.map { PointJson(it.x, it.y) },
    strokeWidth = strokeWidth,
    strokeColor = strokeColor,
    penType = penType.name,
    pressure = pressure,
    pointTimestamps = pointTimestamps,
    timestamp = timestamp,
    layer = layer
)

fun ShapeJson.toEntity(noteId: String): ShapeEntity {
    val shapeType = runCatching { ShapeType.valueOf(type) }.getOrDefault(ShapeType.STROKE)
    val pen = runCatching { PenType.valueOf(penType) }.getOrDefault(PenType.BALLPEN)
    return ShapeEntity(
        id = id,
        noteId = noteId,
        type = shapeType,
        points = points.map { android.graphics.PointF(it.x, it.y) },
        strokeWidth = strokeWidth,
        strokeColor = strokeColor,
        penType = pen,
        pressure = pressure,
        pointTimestamps = pointTimestamps,
        timestamp = timestamp,
        layer = layer
    )
}

fun NotebookEntity.toNotebookJson() = NotebookJson(
    id = id,
    name = name,
    folderId = folderId,
    settings = settings,
    createdAt = createdAt,
    modifiedAt = modifiedAt
)

fun NotebookJson.toEntity() = NotebookEntity(
    id = id,
    name = name,
    folderId = folderId,
    settings = settings,
    createdAt = createdAt,
    modifiedAt = modifiedAt
)

fun FolderEntity.toFolderJson() = FolderJson(
    id = id,
    name = name,
    parentFolderId = parentFolderId,
    createdAt = createdAt,
    modifiedAt = modifiedAt
)

fun FolderJson.toEntity() = FolderEntity(
    id = id,
    name = name,
    parentFolderId = parentFolderId,
    createdAt = createdAt,
    modifiedAt = modifiedAt
)

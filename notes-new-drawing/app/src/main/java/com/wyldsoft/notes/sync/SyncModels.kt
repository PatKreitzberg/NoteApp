package com.wyldsoft.notes.sync

import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity
import com.wyldsoft.notes.data.database.entities.NoteNotebookCrossRefEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.database.entities.ShapeEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    val tiltX: List<Int> = emptyList(),
    val tiltY: List<Int> = emptyList(),
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

private val syncJson = Json { ignoreUnknownKeys = true }

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

fun ShapeEntity.toShapeJson(): ShapeJson {
    // points stored as JSON string: [[x1,y1],[x2,y2],...]
    val pointPairs: List<List<Float>> = syncJson.decodeFromString(points)
    val pressureList: List<Float> = syncJson.decodeFromString(pressure)
    val tiltXList: List<Int> = syncJson.decodeFromString(tiltX)
    val tiltYList: List<Int> = syncJson.decodeFromString(tiltY)
    val timestampsList: List<Long> = syncJson.decodeFromString(pointTimestamps)
    return ShapeJson(
        id = id,
        type = type,
        points = pointPairs.map { PointJson(it[0], it[1]) },
        strokeWidth = strokeWidth,
        strokeColor = strokeColor,
        penType = penType,
        pressure = pressureList,
        tiltX = tiltXList,
        tiltY = tiltYList,
        pointTimestamps = timestampsList,
        timestamp = timestamp,
        layer = layer
    )
}

fun ShapeJson.toEntity(noteId: String): ShapeEntity {
    return ShapeEntity(
        id = id,
        noteId = noteId,
        type = type,
        points = syncJson.encodeToString(points.map { listOf(it.x, it.y) }),
        strokeWidth = strokeWidth,
        strokeColor = strokeColor,
        penType = penType,
        pressure = syncJson.encodeToString(pressure),
        tiltX = syncJson.encodeToString(tiltX),
        tiltY = syncJson.encodeToString(tiltY),
        pointTimestamps = syncJson.encodeToString(pointTimestamps),
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

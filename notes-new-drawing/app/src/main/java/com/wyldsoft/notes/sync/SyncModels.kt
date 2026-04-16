package com.wyldsoft.notes.sync

import com.wyldsoft.notes.data.database.entities.FolderEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity
import com.wyldsoft.notes.data.database.entities.NoteNotebookCrossRefEntity
import com.wyldsoft.notes.data.database.entities.NotebookEntity
import com.wyldsoft.notes.data.database.entities.PenProfileSetEntity
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
    val modifiedAt: Long,
    val trashedFromId: String? = null
)

@Serializable
data class FolderJson(
    val id: String,
    val name: String,
    val parentFolderId: String?,
    val createdAt: Long,
    val modifiedAt: Long,
    val trashedFromId: String? = null
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
    modifiedAt = modifiedAt,
    trashedFromId = trashedFromId
)

fun NotebookJson.toEntity() = NotebookEntity(
    id = id,
    name = name,
    folderId = folderId,
    settings = settings,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    trashedFromId = trashedFromId
)

fun FolderEntity.toFolderJson() = FolderJson(
    id = id,
    name = name,
    parentFolderId = parentFolderId,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    trashedFromId = trashedFromId
)

fun FolderJson.toEntity() = FolderEntity(
    id = id,
    name = name,
    parentFolderId = parentFolderId,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    trashedFromId = trashedFromId
)

@Serializable
data class PenProfileSetSyncJson(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val slot1Width: Float,
    val slot1PenType: String,
    val slot1ColorArgb: Int,
    val slot1Alpha: Float,
    val slot2Width: Float,
    val slot2PenType: String,
    val slot2ColorArgb: Int,
    val slot2Alpha: Float,
    val slot3Width: Float,
    val slot3PenType: String,
    val slot3ColorArgb: Int,
    val slot3Alpha: Float,
    val slot4Width: Float,
    val slot4PenType: String,
    val slot4ColorArgb: Int,
    val slot4Alpha: Float,
    val slot5Width: Float,
    val slot5PenType: String,
    val slot5ColorArgb: Int,
    val slot5Alpha: Float,
)

fun PenProfileSetEntity.toSyncJson() = PenProfileSetSyncJson(
    id = id, name = name, updatedAt = updatedAt,
    slot1Width = slot1Width, slot1PenType = slot1PenType, slot1ColorArgb = slot1ColorArgb, slot1Alpha = slot1Alpha,
    slot2Width = slot2Width, slot2PenType = slot2PenType, slot2ColorArgb = slot2ColorArgb, slot2Alpha = slot2Alpha,
    slot3Width = slot3Width, slot3PenType = slot3PenType, slot3ColorArgb = slot3ColorArgb, slot3Alpha = slot3Alpha,
    slot4Width = slot4Width, slot4PenType = slot4PenType, slot4ColorArgb = slot4ColorArgb, slot4Alpha = slot4Alpha,
    slot5Width = slot5Width, slot5PenType = slot5PenType, slot5ColorArgb = slot5ColorArgb, slot5Alpha = slot5Alpha,
)

fun PenProfileSetSyncJson.toEntity() = PenProfileSetEntity(
    id = id, name = name, updatedAt = updatedAt,
    slot1Width = slot1Width, slot1PenType = slot1PenType, slot1ColorArgb = slot1ColorArgb, slot1Alpha = slot1Alpha,
    slot2Width = slot2Width, slot2PenType = slot2PenType, slot2ColorArgb = slot2ColorArgb, slot2Alpha = slot2Alpha,
    slot3Width = slot3Width, slot3PenType = slot3PenType, slot3ColorArgb = slot3ColorArgb, slot3Alpha = slot3Alpha,
    slot4Width = slot4Width, slot4PenType = slot4PenType, slot4ColorArgb = slot4ColorArgb, slot4Alpha = slot4Alpha,
    slot5Width = slot5Width, slot5PenType = slot5PenType, slot5ColorArgb = slot5ColorArgb, slot5Alpha = slot5Alpha,
)

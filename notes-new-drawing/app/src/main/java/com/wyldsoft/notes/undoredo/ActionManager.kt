package com.wyldsoft.notes.undoredo

import android.util.Log
import com.wyldsoft.notes.data.database.entities.ShapeEntity
import com.wyldsoft.notes.data.database.entities.UndoHistoryEntity
import com.wyldsoft.notes.data.database.repository.UndoHistoryRepository
import com.wyldsoft.notes.data.mappers.ShapeMapper
import com.wyldsoft.notes.editor.EditorState
import com.wyldsoft.notes.rendering.DrawingPipeline
import com.wyldsoft.notes.selection.SelectionManager
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages undo and redo stacks. Actions are recorded via [recordAction].
 * After each undo or redo, [onComplete] is called on the main thread so the
 * caller can trigger a screen refresh.
 *
 * When [undoHistoryRepository], [noteId], and [scope] are provided, actions are
 * persisted to the database so the history survives note close/reopen.
 */
class ActionManager(
    private val undoHistoryRepository: UndoHistoryRepository? = null,
    private val noteId: String? = null,
    private val scope: CoroutineScope? = null,
    private val selectionManager: SelectionManager? = null
) {
    private val TAG = "ActionManager"
    private val json = Json { ignoreUnknownKeys = true }

    private val undoStack = ArrayDeque<ActionInterface>()
    private val redoStack = ArrayDeque<ActionInterface>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    fun recordAction(action: ActionInterface) {
        Log.d(TAG, "recordAction ${action::class.simpleName}")
        undoStack.addLast(action)
        redoStack.clear()
        updateState()
        persistRecordAction(action)
    }

    fun undo(scope: CoroutineScope, onComplete: () -> Unit) {
        Log.d(TAG, "undo stackSize=${undoStack.size}")
        if (undoStack.isEmpty()) return
        val action = undoStack.removeLast()
        scope.launch(Dispatchers.IO) {
            action.undo()
            redoStack.addLast(action)
            updateState()
            persistMoveToRedo(action.id)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun redo(scope: CoroutineScope, onComplete: () -> Unit) {
        Log.d(TAG, "redo stackSize=${redoStack.size}")
        if (redoStack.isEmpty()) return
        val action = redoStack.removeLast()
        scope.launch(Dispatchers.IO) {
            action.redo()
            undoStack.addLast(action)
            updateState()
            persistMoveToUndo(action.id)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun clear() {
        Log.d(TAG, "clear")
        undoStack.clear()
        redoStack.clear()
        updateState()
    }

    /**
     * Reconstructs the undo/redo stacks from the database after a note is loaded.
     * Must be called on the IO dispatcher after [DrawingPipeline.loadShapes] completes.
     */
    suspend fun loadFromDatabase(pipeline: DrawingPipeline) {
        val repo = undoHistoryRepository ?: return
        val nId = noteId ?: return
        Log.d(TAG, "loadFromDatabase noteId=$nId")

        val entries = repo.getActionsForNote(nId)
        val undoEntries = entries.filter { it.isUndoStack }.sortedBy { it.sequenceNumber }
        val redoEntries = entries.filter { !it.isUndoStack }.sortedBy { it.sequenceNumber }

        undoStack.clear()
        redoStack.clear()

        for (entry in undoEntries) {
            val action = reconstructAction(entry, pipeline) ?: continue
            undoStack.addLast(action)
        }
        for (entry in redoEntries) {
            val action = reconstructAction(entry, pipeline) ?: continue
            redoStack.addLast(action)
        }

        updateState()
        Log.d(TAG, "loadFromDatabase done: undoStack=${undoStack.size} redoStack=${redoStack.size}")
    }

    private fun updateState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        EditorState.setUndoRedoState(_canUndo.value, _canRedo.value)
    }

    // ── Database persistence ───────────────────────────────────────────────────

    private fun persistRecordAction(action: ActionInterface) {
        val repo = undoHistoryRepository ?: return
        val nId = noteId ?: return
        val sc = scope ?: return
        sc.launch(Dispatchers.IO) {
            repo.clearRedoStack(nId)
            val entity = serializeAction(action, nId, isUndoStack = true) ?: return@launch
            repo.saveAction(entity)
        }
    }

    private suspend fun persistMoveToRedo(actionId: String) {
        undoHistoryRepository?.updateStackType(actionId, isUndoStack = false)
    }

    private suspend fun persistMoveToUndo(actionId: String) {
        undoHistoryRepository?.updateStackType(actionId, isUndoStack = true)
    }

    private fun serializeAction(
        action: ActionInterface,
        nId: String,
        isUndoStack: Boolean
    ): UndoHistoryEntity? {
        return when (action) {
            is DrawAction -> UndoHistoryEntity(
                id = action.id,
                noteId = nId,
                actionType = "DRAW",
                isUndoStack = isUndoStack,
                sequenceNumber = System.currentTimeMillis(),
                shapesJson = serializeShapes(listOf(action.shape), nId)
            )
            is EraseAction -> UndoHistoryEntity(
                id = action.id,
                noteId = nId,
                actionType = "ERASE",
                isUndoStack = isUndoStack,
                sequenceNumber = System.currentTimeMillis(),
                shapesJson = serializeShapes(action.erasedShapes, nId)
            )
            is MoveAction -> UndoHistoryEntity(
                id = action.id,
                noteId = nId,
                actionType = "MOVE",
                isUndoStack = isUndoStack,
                sequenceNumber = System.currentTimeMillis(),
                shapesJson = json.encodeToString(action.shapeIds),
                dNoteX = action.dNoteX,
                dNoteY = action.dNoteY
            )
            is PasteAction -> UndoHistoryEntity(
                id = action.id,
                noteId = nId,
                actionType = "PASTE",
                isUndoStack = isUndoStack,
                sequenceNumber = System.currentTimeMillis(),
                shapesJson = serializeShapes(action.pastedShapes, nId)
            )
            else -> {
                Log.w(TAG, "serializeAction: unknown action type ${action::class.simpleName}")
                null
            }
        }
    }

    private fun serializeShapes(
        shapes: List<Shape>,
        nId: String
    ): String {
        val stored = shapes.map { shape ->
            val entity = ShapeMapper.toEntity(shape, nId)
            StoredShapeData(
                id = entity.id,
                type = entity.type,
                penType = entity.penType,
                strokeColor = entity.strokeColor,
                strokeWidth = entity.strokeWidth,
                points = entity.points,
                pressure = entity.pressure,
                tiltX = entity.tiltX,
                tiltY = entity.tiltY,
                pointTimestamps = entity.pointTimestamps,
                layer = entity.layer
            )
        }
        return json.encodeToString(stored)
    }

    private fun deserializeShapes(
        shapesJson: String,
        nId: String
    ): List<Shape> {
        val stored: List<StoredShapeData> = json.decodeFromString(shapesJson)
        return stored.map { data ->
            val entity = ShapeEntity(
                id = data.id,
                noteId = nId,
                type = data.type,
                penType = data.penType,
                strokeColor = data.strokeColor,
                strokeWidth = data.strokeWidth,
                points = data.points,
                pressure = data.pressure,
                tiltX = data.tiltX,
                tiltY = data.tiltY,
                pointTimestamps = data.pointTimestamps,
                layer = data.layer
            )
            ShapeMapper.toShape(entity)
        }
    }

    private fun reconstructAction(
        entry: UndoHistoryEntity,
        pipeline: DrawingPipeline
    ): ActionInterface? {
        val nId = noteId ?: return null
        return try {
            when (entry.actionType) {
                "DRAW" -> {
                    val shapes = deserializeShapes(entry.shapesJson, nId)
                    if (shapes.isEmpty()) return null
                    val shape = shapes.first()
                    // If the shape is currently in drawnShapes (undo stack case), use the
                    // live object so that identity-sensitive operations still work.
                    val liveShape = pipeline.getShapeById(shape.entityId ?: "") ?: shape
                    DrawAction(shape = liveShape, pipeline = pipeline, id = entry.id)
                }
                "ERASE" -> {
                    val shapes = deserializeShapes(entry.shapesJson, nId)
                    EraseAction(erasedShapes = shapes, pipeline = pipeline, id = entry.id)
                }
                "MOVE" -> {
                    val sm = selectionManager ?: return null
                    val shapeIds: List<String> = json.decodeFromString(entry.shapesJson)
                    MoveAction(
                        shapeIds = shapeIds,
                        dNoteX = entry.dNoteX,
                        dNoteY = entry.dNoteY,
                        pipeline = pipeline,
                        selectionManager = sm,
                        id = entry.id
                    )
                }
                "PASTE" -> {
                    val shapes = deserializeShapes(entry.shapesJson, nId)
                    PasteAction(pastedShapes = shapes, pipeline = pipeline, id = entry.id)
                }
                else -> {
                    Log.w(TAG, "reconstructAction: unknown actionType=${entry.actionType}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "reconstructAction failed for entry id=${entry.id}: ${e.message}")
            null
        }
    }
}

package com.wyldsoft.notes.data.database.repository

import android.graphics.RectF
import android.util.Log
import com.wyldsoft.notes.data.database.dao.HtrResultDao
import com.wyldsoft.notes.data.database.dao.HtrSearchRow
import com.wyldsoft.notes.data.database.entities.HtrResultEntity
import com.wyldsoft.notes.shapemanagement.shapes.Shape
import org.json.JSONArray

class HtrResultRepository(private val htrResultDao: HtrResultDao) {
    companion object {
        private const val TAG = "HtrResultRepository"
    }

    suspend fun upsert(entity: HtrResultEntity) {
        Log.d(TAG, "upsert id=${entity.id} noteId=${entity.noteId} text='${entity.text}'")
        htrResultDao.insert(entity)
    }

    suspend fun getByNoteId(noteId: String): List<HtrResultEntity> {
        Log.d(TAG, "getByNoteId noteId=$noteId")
        return htrResultDao.getByNoteId(noteId)
    }

    suspend fun deleteByNoteId(noteId: String) {
        Log.d(TAG, "deleteByNoteId noteId=$noteId")
        htrResultDao.deleteByNoteId(noteId)
    }

    suspend fun searchAcrossNotes(query: String): List<HtrSearchRow> {
        Log.d(TAG, "searchAcrossNotes query='$query'")
        return htrResultDao.searchAcrossNotes(query)
    }

    suspend fun updateBoundingBoxForShapes(noteId: String, movedShapes: List<Shape>) {
        Log.d(TAG, "updateBoundingBoxForShapes noteId=$noteId shapes=${movedShapes.size}")
        val movedIds = movedShapes.mapNotNull { it.entityId }.toSet()
        if (movedIds.isEmpty()) return

        val allResults = htrResultDao.getByNoteId(noteId)
        for (result in allResults) {
            val resultShapeIds = parseShapeIds(result.shapeIds)
            if (resultShapeIds.none { it in movedIds }) continue

            // Recompute bounding box: use updated touch points from movedShapes,
            // but we only have the moved shapes here. Any shape not moved keeps
            // the old contribution — approximate by expanding from moved shapes only.
            val box = computeBoundingBox(movedShapes.filter { it.entityId in resultShapeIds })
            if (box.isEmpty) continue
            htrResultDao.updateBoundingBox(result.id, box.left, box.top, box.right, box.bottom)
        }
    }

    private fun parseShapeIds(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun computeBoundingBox(shapes: List<Shape>): RectF {
        val rect = RectF(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)
        for (shape in shapes) {
            val points = shape.touchPointList?.points ?: continue
            for (point in points) {
                if (point == null) continue
                if (point.x < rect.left) rect.left = point.x
                if (point.y < rect.top) rect.top = point.y
                if (point.x > rect.right) rect.right = point.x
                if (point.y > rect.bottom) rect.bottom = point.y
            }
        }
        if (rect.left == Float.MAX_VALUE) return RectF()
        return rect
    }
}

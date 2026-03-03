package com.wyldsoft.notes.data.repository

import com.wyldsoft.notes.data.database.dao.RecognizedSegmentDao
import com.wyldsoft.notes.data.database.entities.RecognizedSegmentEntity
import kotlinx.coroutines.flow.Flow

class RecognizedSegmentRepository(
    private val dao: RecognizedSegmentDao
) {
    fun getSegmentsForNote(noteId: String): Flow<List<RecognizedSegmentEntity>> {
        return dao.getSegmentsForNote(noteId)
    }

    suspend fun getSegmentsForNoteOnce(noteId: String): List<RecognizedSegmentEntity> {
        return dao.getSegmentsForNoteOnce(noteId)
    }

    suspend fun searchText(query: String): List<RecognizedSegmentEntity> {
        return dao.searchText(query)
    }

    suspend fun saveSegments(segments: List<RecognizedSegmentEntity>) {
        dao.insertAll(segments)
    }

    suspend fun deleteAllForNote(noteId: String) {
        dao.deleteAllForNote(noteId)
    }

    suspend fun deleteByIds(ids: List<String>) {
        dao.deleteByIds(ids)
    }
}

package com.wyldsoft.notes.data.repository

import android.util.Log
import com.wyldsoft.notes.data.database.dao.DeletedItemDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.dao.ShapeDao
import com.wyldsoft.notes.data.database.entities.DeletedItemEntity
import com.wyldsoft.notes.data.database.entities.NoteEntity
import com.wyldsoft.notes.data.database.entities.ShapeEntity
import com.wyldsoft.notes.domain.models.Note
import com.wyldsoft.notes.domain.models.PaperTemplate
import com.wyldsoft.notes.domain.models.Shape
import kotlinx.coroutines.flow.*
import java.util.UUID

interface NoteRepository {
    suspend fun getNote(id: String): Note
    suspend fun saveNote(note: Note)
    suspend fun deleteNote(id: String)
    suspend fun addShape(noteId: String, shape: Shape)
    suspend fun removeShape(noteId: String, shapeId: String)
    fun getCurrentNote(): StateFlow<Note>
    suspend fun createNewNote(): Note
    suspend fun setCurrentNote(noteId: String)
    fun getNoteFlow(noteId: String): Flow<Note>
    suspend fun updateViewportState(noteId: String, scale: Float, scrollX: Float, scrollY: Float)
    suspend fun updatePaginationSettings(noteId: String, isPaginationEnabled: Boolean, paperSize: String)
    suspend fun updatePaperTemplate(noteId: String, paperTemplate: String)
    suspend fun updateShape(noteId: String, shape: Shape)
}

class NoteRepositoryImpl(
    private val noteDao: NoteDao,
    private val shapeDao: ShapeDao,
    private val deletedItemDao: DeletedItemDao? = null
) : NoteRepository {
    private var _currentNote = MutableStateFlow(Note()) // set a default empty note
    override fun getCurrentNote(): StateFlow<Note> = _currentNote.asStateFlow()
    
    override suspend fun getNote(id: String): Note {
        val noteEntity = noteDao.getNote(id)
        val shapes = shapeDao.getShapesForNoteOnce(id)
        return noteEntity.toNote(shapes)
    }
    
    override fun getNoteFlow(noteId: String): Flow<Note> {
        return noteDao.getNoteFlow(noteId).combine(
            shapeDao.getShapesForNote(noteId)
        ) { noteEntity, shapes ->
            noteEntity.toNote(shapes)
        }
    }
    
    override suspend fun saveNote(note: Note) {
        val noteEntity = note.toEntity()
        noteDao.update(noteEntity)
        
        // Update current note if it's the same
        if (_currentNote.value.id == note.id) {
            Log.d("NoteRepository", "Updating current note: ${note.id}")
            _currentNote.value = note
        }
    }
    
    override suspend fun deleteNote(id: String) {
        deletedItemDao?.insert(DeletedItemEntity(entityId = id, entityType = "note"))
        noteDao.deleteById(id)
    }
    
    override suspend fun addShape(noteId: String, shape: Shape) {
        val shapeEntity = shape.toEntity(noteId)
        shapeDao.insert(shapeEntity)
        refreshCurrentNoteIfMatch(noteId)
    }

    override suspend fun removeShape(noteId: String, shapeId: String) {
        shapeDao.deleteById(shapeId)
        refreshCurrentNoteIfMatch(noteId)
    }
    
    override suspend fun createNewNote(): Note {
        val noteEntity = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = "Untitled"
        )
        noteDao.insert(noteEntity)
        
        val note = noteEntity.toNote(emptyList())
        Log.d("NoteRepository", "Created new note: ${note.id}")
        _currentNote.value = note
        return note
    }
    
    override suspend fun setCurrentNote(noteId: String) {
        Log.d("NoteRepository", "Setting current note: $noteId")
        _currentNote.value = getNote(noteId)
    }
    
    override suspend fun updateViewportState(noteId: String, scale: Float, scrollX: Float, scrollY: Float) {
        Log.d("NoteRepository", "Updating viewport state for note: $noteId, scale: $scale, scrollX: $scrollX, scrollY: $scrollY")
        updateNoteFields(noteId) { entity ->
            entity.copy(
                viewportScale = scale,
                viewportScrollX = scrollX,
                viewportScrollY = scrollY
            )
        }
    }

    override suspend fun updatePaginationSettings(noteId: String, isPaginationEnabled: Boolean, paperSize: String) {
        updateNoteFields(noteId) { entity ->
            entity.copy(
                isPaginationEnabled = isPaginationEnabled,
                paperSize = paperSize
            )
        }
    }
    
    private suspend fun updateNoteFields(noteId: String, transform: (NoteEntity) -> NoteEntity) {
        val noteEntity = noteDao.getNote(noteId)
        if (noteEntity == null) {
            Log.e("NoteRepository", "Note with ID $noteId not found")
            return
        }
        val updatedEntity = transform(noteEntity).copy(modifiedAt = System.currentTimeMillis())
        noteDao.update(updatedEntity)
        refreshCurrentNoteIfMatch(noteId)
    }

    private suspend fun refreshCurrentNoteIfMatch(noteId: String) {
        if (_currentNote.value.id == noteId) {
            _currentNote.value = getNote(noteId)
        }
    }

    // Extension functions for converting between domain models and entities
    private fun NoteEntity.toNote(shapes: List<ShapeEntity>): Note {
        return Note(
            id = id,
            title = title,
            shapes = shapes.map { it.toShape() }.toMutableList(),
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            viewportScale = viewportScale,
            viewportScrollX = viewportScrollX,
            viewportScrollY = viewportScrollY,
            isPaginationEnabled = isPaginationEnabled,
            paperSize = paperSize,
            paperTemplate = PaperTemplate.fromString(paperTemplate)
        )
    }

    override suspend fun updateShape(noteId: String, shape: Shape) {
        val shapeEntity = shape.toEntity(noteId)
        shapeDao.update(shapeEntity)
        refreshCurrentNoteIfMatch(noteId)
    }

    override suspend fun updatePaperTemplate(noteId: String, paperTemplate: String) {
        updateNoteFields(noteId) { entity ->
            entity.copy(paperTemplate = paperTemplate)
        }
    }

    private fun Note.toEntity(): NoteEntity {
        return NoteEntity(
            id = id,
            title = title,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            viewportScale = viewportScale,
            viewportScrollX = viewportScrollX,
            viewportScrollY = viewportScrollY,
            isPaginationEnabled = isPaginationEnabled,
            paperSize = paperSize,
            paperTemplate = paperTemplate.name
        )
    }
    
    private fun ShapeEntity.toShape(): Shape {
        return Shape(
            id = id,
            type = type,
            points = points,
            strokeWidth = strokeWidth,
            strokeColor = strokeColor,
            penType = penType,
            pressure = pressure,
            pointTimestamps = pointTimestamps,
            timestamp = timestamp
        )
    }

    private fun Shape.toEntity(noteId: String): ShapeEntity {
        Log.d("NoteRepository","Shape.toEntity id is ${id}")
        return ShapeEntity(
            id = id,
            noteId = noteId,
            type = type,
            points = points,
            strokeWidth = strokeWidth,
            strokeColor = strokeColor,
            penType = penType,
            pressure = pressure,
            pointTimestamps = pointTimestamps,
            timestamp = timestamp
        )
    }
}
package com.wyldsoft.notes.data.repository

import android.util.Log
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.dao.ShapeDao
import com.wyldsoft.notes.data.database.entities.NoteEntity
import com.wyldsoft.notes.data.database.entities.ShapeEntity
import com.wyldsoft.notes.domain.models.Note
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
    suspend fun updateViewportState(noteId: String, scale: Float, offsetX: Float, offsetY: Float)
    suspend fun updatePaginationSettings(noteId: String, isPaginationEnabled: Boolean, paperSize: String)
}

class NoteRepositoryImpl(
    private val noteDao: NoteDao,
    private val shapeDao: ShapeDao
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
        noteDao.deleteById(id)
    }
    
    override suspend fun addShape(noteId: String, shape: Shape) {
        val shapeEntity = shape.toEntity(noteId)
        shapeDao.insert(shapeEntity)
        
        // Update current note if needed
        if (_currentNote.value.id == noteId) {
            Log.d("NoteRepository", "Adding shape to current note: $noteId")
            _currentNote.value = getNote(noteId)
        }
    }

    override suspend fun removeShape(noteId: String, shapeId: String) {
        shapeDao.deleteById(shapeId)
        
        // Update current note if needed
        if (_currentNote.value.id == noteId) {
            Log.d("NoteRepository", "Removing shape from current note: $noteId")
            _currentNote.value = getNote(noteId)
        }
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
    
    override suspend fun updateViewportState(noteId: String, scale: Float, offsetX: Float, offsetY: Float) {
        val notes = noteDao.getAllNotes()
        notes.forEach { Log.d("NoteRepository", "Note id: ${it.id}") }
        Log.d("NoteRepository", "There are ${notes.size} notes in the database")

        Log.d("NoteRepository", "Updating viewport state for note: $noteId, scale: $scale, offsetX: $offsetX, offsetY: $offsetY")
        val noteEntity = noteDao.getNote(noteId)

        if (noteEntity == null) {
            Log.e("NoteRepository", "Note with ID $noteId not found")
            return
        }

        Log.d("NoteRepository", "Current note entity: $noteEntity")
        val updatedEntity = noteEntity.copy(
            viewportScale = scale,
            viewportOffsetX = offsetX,
            viewportOffsetY = offsetY,
            modifiedAt = System.currentTimeMillis()
        )
        noteDao.update(updatedEntity)
        
        // Update current note if it's the same
        if (_currentNote.value.id == noteId) {
            Log.d("NoteRepository", "Updating viewport state for current note: $noteId")
            setCurrentNote(noteId)
        }
    }
    
    override suspend fun updatePaginationSettings(noteId: String, isPaginationEnabled: Boolean, paperSize: String) {
        val noteEntity = noteDao.getNote(noteId)
        val updatedEntity = noteEntity.copy(
            isPaginationEnabled = isPaginationEnabled,
            paperSize = paperSize,
            modifiedAt = System.currentTimeMillis()
        )
        noteDao.update(updatedEntity)
        
        // Update current note if it's the same
        if (_currentNote.value.id == noteId) {
            Log.d("NoteRepository", "Updating pagination settings for current note: $noteId")
            setCurrentNote(noteId)
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
            viewportOffsetX = viewportOffsetX,
            viewportOffsetY = viewportOffsetY,
            isPaginationEnabled = isPaginationEnabled,
            paperSize = paperSize
        )
    }
    
    private fun Note.toEntity(): NoteEntity {
        return NoteEntity(
            id = id,
            title = title,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
            viewportScale = viewportScale,
            viewportOffsetX = viewportOffsetX,
            viewportOffsetY = viewportOffsetY,
            isPaginationEnabled = isPaginationEnabled,
            paperSize = paperSize
        )
    }
    
    private fun ShapeEntity.toShape(): Shape {
        return Shape(
            id = id,
            type = type,
            points = points,
            strokeWidth = strokeWidth,
            strokeColor = strokeColor,
            pressure = pressure,
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
            pressure = pressure,
            timestamp = timestamp
        )
    }
}
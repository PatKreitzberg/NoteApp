package com.wyldsoft.notes.sync

import android.util.Log
import com.wyldsoft.notes.data.database.dao.FolderDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.dao.NotebookDao
import com.wyldsoft.notes.data.database.dao.ShapeDao
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "SyncUploader"

class SyncUploader(
    private val noteDao: NoteDao,
    private val notebookDao: NotebookDao,
    private val folderDao: FolderDao,
    private val shapeDao: ShapeDao,
    private val json: Json
) {
    suspend fun uploadFolders(
        client: DriveApiClient,
        foldersDir: String,
        lastSync: Long,
        isFirstSync: Boolean,
        errors: MutableList<String>
    ) {
        Log.d(TAG, "uploadFolders isFirstSync=$isFirstSync")
        val toUpload = if (isFirstSync) folderDao.getAllFolderEntities()
        else folderDao.getFoldersModifiedAfter(lastSync)
        uploadEntities(client, foldersDir, toUpload, errors,
            entityId = { it.id },
            encode = { json.encodeToString(it.toFolderJson()) }
        )
    }

    suspend fun uploadNotebooks(
        client: DriveApiClient,
        notebooksDir: String,
        lastSync: Long,
        isFirstSync: Boolean,
        errors: MutableList<String>
    ) {
        Log.d(TAG, "uploadNotebooks isFirstSync=$isFirstSync")
        val toUpload = if (isFirstSync) notebookDao.getAllNotebookEntities()
        else notebookDao.getNotebooksModifiedAfter(lastSync)
        uploadEntities(client, notebooksDir, toUpload, errors,
            entityId = { it.id },
            encode = { json.encodeToString(it.toNotebookJson()) }
        )
    }

    suspend fun uploadNotes(
        client: DriveApiClient,
        notesDir: String,
        lastSync: Long,
        isFirstSync: Boolean,
        errors: MutableList<String>
    ) {
        Log.d(TAG, "uploadNotes isFirstSync=$isFirstSync")
        val toUpload = if (isFirstSync) noteDao.getAllNoteEntities()
        else noteDao.getNotesModifiedAfter(lastSync)
        uploadEntities(client, notesDir, toUpload, errors,
            entityId = { it.id },
            encode = { note ->
                val shapes = shapeDao.getShapesForNoteOnce(note.id)
                val notebookIds = noteDao.getCrossRefsForNote(note.id).map { it.notebookId }
                val dto = NoteSyncDto(
                    note = note.toNoteJson(),
                    shapes = shapes.map { it.toShapeJson() },
                    notebookIds = notebookIds
                )
                json.encodeToString(dto)
            }
        )
    }

    private suspend fun <T> uploadEntities(
        client: DriveApiClient,
        directory: String,
        entities: List<T>,
        errors: MutableList<String>,
        entityId: (T) -> String,
        encode: suspend (T) -> String
    ) {
        val existingFiles = client.listFilesWithNames(directory).toMap()
        for (entity in entities) {
            try {
                val id = entityId(entity)
                val fileName = "$id.json"
                client.uploadJsonFile(directory, fileName, encode(entity), existingFiles[fileName]?.id)
            } catch (e: Exception) {
                val id = entityId(entity)
                Log.e(TAG, "Upload $id", e)
                errors.add("Upload $id: ${e.message}")
            }
        }
    }
}

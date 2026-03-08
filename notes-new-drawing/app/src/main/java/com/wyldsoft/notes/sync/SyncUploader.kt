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
        val toUpload = if (isFirstSync) folderDao.getAllFolderEntities()
        else folderDao.getFoldersModifiedAfter(lastSync)
        val existingFiles = client.listFilesWithNames(foldersDir).toMap()
        for (folder in toUpload) {
            try {
                val fileName = "${folder.id}.json"
                val folderJson = json.encodeToString(folder.toFolderJson())
                client.uploadJsonFile(foldersDir, fileName, folderJson, existingFiles[fileName]?.id)
            } catch (e: Exception) {
                errors.add("Upload folder ${folder.id}: ${e.message}")
            }
        }
    }

    suspend fun uploadNotebooks(
        client: DriveApiClient,
        notebooksDir: String,
        lastSync: Long,
        isFirstSync: Boolean,
        errors: MutableList<String>
    ) {
        val toUpload = if (isFirstSync) notebookDao.getAllNotebookEntities()
        else notebookDao.getNotebooksModifiedAfter(lastSync)
        val existingFiles = client.listFilesWithNames(notebooksDir).toMap()
        for (notebook in toUpload) {
            try {
                val fileName = "${notebook.id}.json"
                val notebookJson = json.encodeToString(notebook.toNotebookJson())
                client.uploadJsonFile(notebooksDir, fileName, notebookJson, existingFiles[fileName]?.id)
            } catch (e: Exception) {
                errors.add("Upload notebook ${notebook.id}: ${e.message}")
            }
        }
    }

    suspend fun uploadNotes(
        client: DriveApiClient,
        notesDir: String,
        lastSync: Long,
        isFirstSync: Boolean,
        errors: MutableList<String>
    ) {
        val toUpload = if (isFirstSync) noteDao.getAllNoteEntities()
        else noteDao.getNotesModifiedAfter(lastSync)
        val existingFiles = client.listFilesWithNames(notesDir).toMap()
        for (note in toUpload) {
            try {
                val shapes = shapeDao.getShapesForNoteOnce(note.id)
                val notebookIds = noteDao.getCrossRefsForNote(note.id).map { it.notebookId }
                val dto = NoteSyncDto(
                    note = note.toNoteJson(),
                    shapes = shapes.map { it.toShapeJson() },
                    notebookIds = notebookIds
                )
                val fileName = "${note.id}.json"
                val noteJson = json.encodeToString(dto)
                client.uploadJsonFile(notesDir, fileName, noteJson, existingFiles[fileName]?.id)
            } catch (e: Exception) {
                errors.add("Upload note ${note.id}: ${e.message}")
            }
        }
    }
}

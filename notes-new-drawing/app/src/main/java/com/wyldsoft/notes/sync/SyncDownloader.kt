package com.wyldsoft.notes.sync

import android.util.Log
import com.wyldsoft.notes.data.database.dao.FolderDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.dao.NotebookDao
import com.wyldsoft.notes.data.database.dao.ShapeDao
import com.wyldsoft.notes.data.database.entities.NoteNotebookCrossRefEntity
import kotlinx.serialization.json.Json

private const val TAG = "SyncDownloader"

class SyncDownloader(
    private val noteDao: NoteDao,
    private val notebookDao: NotebookDao,
    private val folderDao: FolderDao,
    private val shapeDao: ShapeDao,
    private val json: Json
) {
    suspend fun downloadFolders(
        client: DriveApiClient,
        foldersDir: String,
        lastSync: Long,
        errors: MutableList<String>
    ) {
        Log.d(TAG, "downloadFolders")
        val remoteFiles = client.listFilesWithNames(foldersDir)
        for ((name, ref) in remoteFiles) {
            if (ref.modifiedTime <= lastSync) continue
            try {
                val rawJson = client.downloadJsonFile(ref.id)
                val folderJson = json.decodeFromString<FolderJson>(rawJson)
                val localFolder = folderDao.getFolder(folderJson.id)
                if (localFolder == null || folderJson.modifiedAt > localFolder.modifiedAt) {
                    folderDao.upsertFolder(folderJson.toEntity())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download folder $name", e)
                errors.add("Download folder $name: ${e.message}")
            }
        }
    }

    suspend fun downloadNotebooks(
        client: DriveApiClient,
        notebooksDir: String,
        lastSync: Long,
        errors: MutableList<String>
    ) {
        Log.d(TAG, "downloadNotebooks")
        val remoteFiles = client.listFilesWithNames(notebooksDir)
        for ((name, ref) in remoteFiles) {
            if (ref.modifiedTime <= lastSync) continue
            try {
                val rawJson = client.downloadJsonFile(ref.id)
                val notebookJson = json.decodeFromString<NotebookJson>(rawJson)
                val localNotebook = notebookDao.getNotebook(notebookJson.id)
                if (localNotebook == null || notebookJson.modifiedAt > localNotebook.modifiedAt) {
                    notebookDao.upsertNotebook(notebookJson.toEntity())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download notebook $name", e)
                errors.add("Download notebook $name: ${e.message}")
            }
        }
    }

    suspend fun downloadNotes(
        client: DriveApiClient,
        notesDir: String,
        lastSync: Long,
        errors: MutableList<String>
    ) {
        Log.d(TAG, "downloadNotes")
        val remoteFiles = client.listFilesWithNames(notesDir)
        for ((name, ref) in remoteFiles) {
            if (ref.modifiedTime <= lastSync) continue
            try {
                val rawJson = client.downloadJsonFile(ref.id)
                val dto = json.decodeFromString<NoteSyncDto>(rawJson)
                val localNote = noteDao.getNote(dto.note.id)
                if (localNote == null || dto.note.modifiedAt > localNote.modifiedAt) {
                    noteDao.upsertNote(dto.note.toEntity())
                    shapeDao.deleteAllForNote(dto.note.id)
                    shapeDao.insertAll(dto.shapes.map { it.toEntity(dto.note.id) })
                    noteDao.deleteCrossRefsForNote(dto.note.id)
                    for (notebookId in dto.notebookIds) {
                        noteDao.upsertCrossRef(NoteNotebookCrossRefEntity(dto.note.id, notebookId))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download note $name", e)
                errors.add("Download note $name: ${e.message}")
            }
        }
    }
}

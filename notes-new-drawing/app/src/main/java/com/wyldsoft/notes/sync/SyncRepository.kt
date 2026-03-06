package com.wyldsoft.notes.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.wyldsoft.notes.data.database.dao.DeletedItemDao
import com.wyldsoft.notes.data.database.dao.FolderDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.dao.NotebookDao
import com.wyldsoft.notes.data.database.dao.ShapeDao
import com.wyldsoft.notes.data.database.dao.SyncStateDao
import com.wyldsoft.notes.data.database.entities.DeletedItemEntity
import com.wyldsoft.notes.data.database.entities.NoteNotebookCrossRef
import com.wyldsoft.notes.data.database.entities.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

sealed class SyncResult {
    object Success : SyncResult()
    data class PartialSuccess(val errors: List<String>) : SyncResult()
    object NotSignedIn : SyncResult()
    object AlreadyRunning : SyncResult()
    data class Failure(val error: Throwable) : SyncResult()
}

private const val TAG = "SyncRepository"
private const val PREFS_NAME = "sync_prefs"
private const val KEY_DEVICE_ID = "device_id"
private const val DELETIONS_FILE = "deletions.json"
private const val SYNC_STATE_FILE = "sync_state.json"
private val json = Json { ignoreUnknownKeys = true }

class SyncRepository(
    private val noteDao: NoteDao,
    private val notebookDao: NotebookDao,
    private val folderDao: FolderDao,
    private val shapeDao: ShapeDao,
    private val deletedItemDao: DeletedItemDao,
    private val syncStateDao: SyncStateDao,
    private val context: Context
) {
    private val isRunning = AtomicBoolean(false)

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            id
        }
    }

    suspend fun getLastSyncTimestamp(): Long {
        val deviceId = getDeviceId()
        return syncStateDao.getByDeviceId(deviceId)?.lastSyncTimestamp ?: 0L
    }

    suspend fun performSync(): SyncResult = withContext(Dispatchers.IO) {
        val account = GoogleDriveManager.getSignedInAccount(context)
            ?: return@withContext SyncResult.NotSignedIn

        if (!isRunning.compareAndSet(false, true)) {
            return@withContext SyncResult.AlreadyRunning
        }

        try {
            val client = DriveApiClient.build(context, account)
            val deviceId = getDeviceId()
            val syncStartTime = System.currentTimeMillis()

            val lastSync = syncStateDao.getByDeviceId(deviceId)?.lastSyncTimestamp ?: 0L
            val isFirstSync = lastSync == 0L
            Log.d(TAG, "Starting sync. lastSync=$lastSync, firstSync=$isFirstSync")

            // Get or create App Folder + subdirs
            val appFolder = client.getOrCreateAppFolder()
            val foldersDir = client.getOrCreateSubfolder(appFolder, "folders")
            val notebooksDir = client.getOrCreateSubfolder(appFolder, "notebooks")
            val notesDir = client.getOrCreateSubfolder(appFolder, "notes")

            val errors = mutableListOf<String>()

            // ---- UPLOAD PHASE ----
            uploadFolders(client, foldersDir, lastSync, isFirstSync, errors)
            uploadNotebooks(client, notebooksDir, lastSync, isFirstSync, errors)
            uploadNotes(client, notesDir, lastSync, isFirstSync, errors)

            // ---- DOWNLOAD PHASE ----
            downloadFolders(client, foldersDir, lastSync, errors)
            downloadNotebooks(client, notebooksDir, lastSync, errors)
            downloadNotes(client, notesDir, lastSync, errors)

            // ---- DELETION PUSH ----
            pushDeletions(client, appFolder, errors)

            // ---- DELETION APPLY ----
            applyRemoteDeletions(client, appFolder, lastSync, errors)

            // ---- CLEANUP ----
            val thirtyDaysAgo = syncStartTime - 30L * 24 * 60 * 60 * 1000
            deletedItemDao.deleteOlderThan(thirtyDaysAgo)

            // ---- SAVE SYNC STATE ----
            syncStateDao.upsert(
                SyncStateEntity(
                    deviceId = deviceId,
                    lastSyncTimestamp = syncStartTime
                )
            )
            updateSyncStateOnDrive(client, appFolder, deviceId, syncStartTime, errors)

            Log.d(TAG, "Sync complete. errors=${errors.size}")
            if (errors.isEmpty()) SyncResult.Success
            else SyncResult.PartialSuccess(errors)

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            SyncResult.Failure(e)
        } finally {
            isRunning.set(false)
        }
    }

    private suspend fun uploadFolders(
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

    private suspend fun uploadNotebooks(
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

    private suspend fun uploadNotes(
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

    private suspend fun downloadFolders(
        client: DriveApiClient,
        foldersDir: String,
        lastSync: Long,
        errors: MutableList<String>
    ) {
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
                errors.add("Download folder $name: ${e.message}")
            }
        }
    }

    private suspend fun downloadNotebooks(
        client: DriveApiClient,
        notebooksDir: String,
        lastSync: Long,
        errors: MutableList<String>
    ) {
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
                errors.add("Download notebook $name: ${e.message}")
            }
        }
    }

    private suspend fun downloadNotes(
        client: DriveApiClient,
        notesDir: String,
        lastSync: Long,
        errors: MutableList<String>
    ) {
        val remoteFiles = client.listFilesWithNames(notesDir)
        for ((name, ref) in remoteFiles) {
            if (ref.modifiedTime <= lastSync) continue
            try {
                val rawJson = client.downloadJsonFile(ref.id)
                val dto = json.decodeFromString<NoteSyncDto>(rawJson)
                val localNote = runCatching { noteDao.getNote(dto.note.id) }.getOrNull()
                if (localNote == null || dto.note.modifiedAt > localNote.modifiedAt) {
                    noteDao.upsertNote(dto.note.toEntity())
                    // Replace shapes atomically
                    shapeDao.deleteAllForNote(dto.note.id)
                    shapeDao.insertAll(dto.shapes.map { it.toEntity(dto.note.id) })
                    // Replace cross-refs
                    noteDao.deleteCrossRefsForNote(dto.note.id)
                    for (notebookId in dto.notebookIds) {
                        noteDao.upsertCrossRef(NoteNotebookCrossRef(dto.note.id, notebookId))
                    }
                }
            } catch (e: Exception) {
                errors.add("Download note $name: ${e.message}")
            }
        }
    }

    private suspend fun pushDeletions(
        client: DriveApiClient,
        appFolder: String,
        errors: MutableList<String>
    ) {
        try {
            val localTombstones = deletedItemDao.getAll()
            if (localTombstones.isEmpty()) return

            val localRecords = localTombstones.map {
                DeletionRecord(it.entityId, it.entityType, it.deletedAt)
            }

            // Merge with existing remote deletions.json
            val existing = client.findFile(appFolder, DELETIONS_FILE)
            val mergedRecords = if (existing != null) {
                val remoteJson = client.downloadJsonFile(existing.id)
                val remote = runCatching { json.decodeFromString<DeletionsManifest>(remoteJson) }
                    .getOrNull()?.deletions ?: emptyList()
                (remote + localRecords).distinctBy { it.entityId }
            } else {
                localRecords
            }

            val manifest = DeletionsManifest(mergedRecords)
            client.uploadJsonFile(appFolder, DELETIONS_FILE, json.encodeToString(manifest), existing?.id)
        } catch (e: Exception) {
            errors.add("Push deletions: ${e.message}")
        }
    }

    private suspend fun applyRemoteDeletions(
        client: DriveApiClient,
        appFolder: String,
        lastSync: Long,
        errors: MutableList<String>
    ) {
        try {
            val deletionsRef = client.findFile(appFolder, DELETIONS_FILE) ?: return
            val rawJson = client.downloadJsonFile(deletionsRef.id)
            val manifest = json.decodeFromString<DeletionsManifest>(rawJson)

            for (record in manifest.deletions) {
                if (record.deletedAt <= lastSync) continue
                try {
                    when (record.entityType) {
                        "note" -> {
                            val local = runCatching { noteDao.getNote(record.entityId) }.getOrNull()
                            if (local != null && local.modifiedAt < record.deletedAt) {
                                noteDao.deleteById(record.entityId)
                            }
                        }
                        "notebook" -> {
                            val local = notebookDao.getNotebook(record.entityId)
                            if (local != null && local.modifiedAt < record.deletedAt) {
                                notebookDao.deleteById(record.entityId)
                            }
                        }
                        "folder" -> {
                            val local = folderDao.getFolder(record.entityId)
                            if (local != null && local.modifiedAt < record.deletedAt) {
                                folderDao.deleteById(record.entityId)
                            }
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Apply deletion ${record.entityId}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("Apply remote deletions: ${e.message}")
        }
    }

    private suspend fun updateSyncStateOnDrive(
        client: DriveApiClient,
        appFolder: String,
        deviceId: String,
        syncTime: Long,
        errors: MutableList<String>
    ) {
        try {
            val existing = client.findFile(appFolder, SYNC_STATE_FILE)
            val manifest = if (existing != null) {
                val remoteJson = client.downloadJsonFile(existing.id)
                runCatching { json.decodeFromString<SyncStateManifest>(remoteJson) }
                    .getOrNull() ?: SyncStateManifest(emptyMap())
            } else {
                SyncStateManifest(emptyMap())
            }
            val updated = manifest.copy(devices = manifest.devices + (deviceId to syncTime))
            client.uploadJsonFile(appFolder, SYNC_STATE_FILE, json.encodeToString(updated), existing?.id)
        } catch (e: Exception) {
            errors.add("Update sync state on Drive: ${e.message}")
        }
    }
}

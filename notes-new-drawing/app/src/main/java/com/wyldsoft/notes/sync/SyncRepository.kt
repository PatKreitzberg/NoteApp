package com.wyldsoft.notes.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.wyldsoft.notes.data.database.dao.DeletedItemDao
import com.wyldsoft.notes.data.database.dao.FolderDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.dao.NotebookDao
import com.wyldsoft.notes.data.database.dao.PenProfileSetDao
import com.wyldsoft.notes.data.database.dao.ShapeDao
import com.wyldsoft.notes.data.database.dao.SyncStateDao
import com.wyldsoft.notes.data.database.entities.DeletedItemEntity
import com.wyldsoft.notes.data.database.entities.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
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

class SyncRepository(
    private val noteDao: NoteDao,
    private val notebookDao: NotebookDao,
    private val folderDao: FolderDao,
    private val shapeDao: ShapeDao,
    private val deletedItemDao: DeletedItemDao,
    private val syncStateDao: SyncStateDao,
    private val penProfileSetDao: PenProfileSetDao,
    private val context: Context
) {
    private val isRunning = AtomicBoolean(false)
    private val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val uploader = SyncUploader(noteDao, notebookDao, folderDao, shapeDao, json)
    private val downloader = SyncDownloader(noteDao, notebookDao, folderDao, shapeDao, json)
    private val deletionHandler = SyncDeletionHandler(noteDao, notebookDao, folderDao, deletedItemDao, json)

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

            val appFolder = client.getOrCreateAppFolder()
            val foldersDir = client.getOrCreateSubfolder(appFolder, "folders")
            val notebooksDir = client.getOrCreateSubfolder(appFolder, "notebooks")
            val notesDir = client.getOrCreateSubfolder(appFolder, "notes")
            val penSetsDir = client.getOrCreateSubfolder(appFolder, "penProfileSets")

            val errors = mutableListOf<String>()

            uploader.uploadFolders(client, foldersDir, lastSync, isFirstSync, errors)
            uploader.uploadNotebooks(client, notebooksDir, lastSync, isFirstSync, errors)
            uploader.uploadNotes(client, notesDir, lastSync, isFirstSync, errors)
            syncPenProfileSets(client, penSetsDir, lastSync, isFirstSync, errors)

            downloader.downloadFolders(client, foldersDir, lastSync, errors)
            downloader.downloadNotebooks(client, notebooksDir, lastSync, errors)
            downloader.downloadNotes(client, notesDir, lastSync, errors)

            deletionHandler.pushDeletions(client, appFolder, errors)
            deletionHandler.applyRemoteDeletions(client, appFolder, lastSync, errors)

            val thirtyDaysAgo = syncStartTime - 30L * 24 * 60 * 60 * 1000
            cleanupOldTrashItems(thirtyDaysAgo)
            deletedItemDao.deleteOlderThan(thirtyDaysAgo)

            syncStateDao.upsert(SyncStateEntity(deviceId = deviceId, lastSyncTimestamp = syncStartTime))
            deletionHandler.updateSyncStateOnDrive(client, appFolder, deviceId, syncStartTime, errors)

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

    private suspend fun syncPenProfileSets(
        client: DriveApiClient,
        penSetsDir: String,
        lastSync: Long,
        isFirstSync: Boolean,
        errors: MutableList<String>
    ) {
        Log.d(TAG, "syncPenProfileSets isFirstSync=$isFirstSync")
        try {
            val existingFiles = client.listFilesWithNames(penSetsDir).toMap()
            // Upload local sets modified since last sync
            val toUpload = if (isFirstSync) penProfileSetDao.getAll()
            else penProfileSetDao.getModifiedAfter(lastSync)
            for (set in toUpload) {
                try {
                    val content = json.encodeToString(set.toSyncJson())
                    val fileName = "${set.id}.json"
                    client.uploadJsonFile(penSetsDir, fileName, content, existingFiles[fileName]?.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload pen set ${set.id}", e)
                    errors.add("pen_set_upload:${set.id}")
                }
            }
            // Download sets from Drive that are newer than local
            for ((_, ref) in existingFiles) {
                try {
                    val content = client.downloadJsonFile(ref.id)
                    val syncJson = json.decodeFromString<PenProfileSetSyncJson>(content)
                    val existing = penProfileSetDao.getById(syncJson.id)
                    if (existing == null || existing.updatedAt < syncJson.updatedAt) {
                        penProfileSetDao.insert(syncJson.toEntity())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download pen set ${ref.id}", e)
                    errors.add("pen_set_download:${ref.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncPenProfileSets failed", e)
            errors.add("pen_sets_sync")
        }
    }

    private suspend fun cleanupOldTrashItems(cutoffMs: Long) {
        Log.d(TAG, "cleanupOldTrashItems cutoff=$cutoffMs")
        val trashedFolders = folderDao.getFoldersInTrash()
        trashedFolders.filter { it.modifiedAt < cutoffMs }.forEach { folder ->
            Log.d(TAG, "Permanently deleting trashed folder id=${folder.id}")
            folderDao.deleteById(folder.id)
            deletedItemDao.insert(
                DeletedItemEntity(
                    entityId = folder.id,
                    entityType = "folder",
                    deletedAt = System.currentTimeMillis(),
                    originalParentId = folder.trashedFromId
                )
            )
        }

        val trashedNotebooks = notebookDao.getNotebooksInTrash()
        trashedNotebooks.filter { it.modifiedAt < cutoffMs }.forEach { notebook ->
            Log.d(TAG, "Permanently deleting trashed notebook id=${notebook.id}")
            notebookDao.deleteById(notebook.id)
            deletedItemDao.insert(
                DeletedItemEntity(
                    entityId = notebook.id,
                    entityType = "notebook",
                    deletedAt = System.currentTimeMillis(),
                    originalParentId = notebook.trashedFromId
                )
            )
        }
    }
}

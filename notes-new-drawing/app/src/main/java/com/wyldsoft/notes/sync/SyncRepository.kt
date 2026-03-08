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
import com.wyldsoft.notes.data.database.entities.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

            val errors = mutableListOf<String>()

            uploader.uploadFolders(client, foldersDir, lastSync, isFirstSync, errors)
            uploader.uploadNotebooks(client, notebooksDir, lastSync, isFirstSync, errors)
            uploader.uploadNotes(client, notesDir, lastSync, isFirstSync, errors)

            downloader.downloadFolders(client, foldersDir, lastSync, errors)
            downloader.downloadNotebooks(client, notebooksDir, lastSync, errors)
            downloader.downloadNotes(client, notesDir, lastSync, errors)

            deletionHandler.pushDeletions(client, appFolder, errors)
            deletionHandler.applyRemoteDeletions(client, appFolder, lastSync, errors)

            val thirtyDaysAgo = syncStartTime - 30L * 24 * 60 * 60 * 1000
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
}

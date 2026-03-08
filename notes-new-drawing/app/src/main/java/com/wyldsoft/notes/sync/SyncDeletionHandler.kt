package com.wyldsoft.notes.sync

import com.wyldsoft.notes.data.database.dao.DeletedItemDao
import com.wyldsoft.notes.data.database.dao.FolderDao
import com.wyldsoft.notes.data.database.dao.NoteDao
import com.wyldsoft.notes.data.database.dao.NotebookDao
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val DELETIONS_FILE = "deletions.json"
private const val SYNC_STATE_FILE = "sync_state.json"

class SyncDeletionHandler(
    private val noteDao: NoteDao,
    private val notebookDao: NotebookDao,
    private val folderDao: FolderDao,
    private val deletedItemDao: DeletedItemDao,
    private val json: Json
) {
    suspend fun pushDeletions(
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

    suspend fun applyRemoteDeletions(
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

    suspend fun updateSyncStateOnDrive(
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

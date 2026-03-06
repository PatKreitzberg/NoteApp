package com.wyldsoft.notes.sync

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class DriveFileRef(val id: String, val modifiedTime: Long)

class DriveApiClient private constructor(private val driveService: Drive) {

    companion object {
        fun build(context: Context, account: GoogleSignInAccount): DriveApiClient {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_APPDATA)
            )
            credential.selectedAccount = account.account
            val service = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Notes App").build()
            return DriveApiClient(service)
        }
    }

    suspend fun getOrCreateAppFolder(): String = withContext(Dispatchers.IO) {
        // The App Data folder is always "appDataFolder" — no need to create it
        "appDataFolder"
    }

    suspend fun getOrCreateSubfolder(parentId: String, name: String): String = withContext(Dispatchers.IO) {
        val existing = findFile(parentId, name)
        if (existing != null) return@withContext existing.id

        val metadata = File().apply {
            this.name = name
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf(parentId)
        }
        driveService.files().create(metadata)
            .setFields("id")
            .execute()
            .id
    }

    suspend fun findFile(parentId: String, name: String): DriveFileRef? = withContext(Dispatchers.IO) {
        val query = "'$parentId' in parents and name = '$name' and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("appDataFolder")
            .setFields("files(id, modifiedTime)")
            .execute()
        result.files?.firstOrNull()?.let { f ->
            DriveFileRef(f.id, f.modifiedTime?.value ?: 0L)
        }
    }

    suspend fun listFiles(parentId: String): List<DriveFileRef> = withContext(Dispatchers.IO) {
        val query = "'$parentId' in parents and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("appDataFolder")
            .setFields("files(id, name, modifiedTime)")
            .execute()
        result.files?.map { f ->
            DriveFileRef(f.id, f.modifiedTime?.value ?: 0L)
        } ?: emptyList()
    }

    suspend fun listFilesWithNames(parentId: String): List<Pair<String, DriveFileRef>> = withContext(Dispatchers.IO) {
        val query = "'$parentId' in parents and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("appDataFolder")
            .setFields("files(id, name, modifiedTime)")
            .execute()
        result.files?.map { f ->
            Pair(f.name, DriveFileRef(f.id, f.modifiedTime?.value ?: 0L))
        } ?: emptyList()
    }

    suspend fun uploadJsonFile(
        parentId: String,
        name: String,
        json: String,
        existingId: String?
    ): String = withContext(Dispatchers.IO) {
        val content = ByteArrayContent("application/json", json.toByteArray(Charsets.UTF_8))
        if (existingId != null) {
            driveService.files().update(existingId, File(), content)
                .setFields("id")
                .execute()
                .id
        } else {
            val metadata = File().apply {
                this.name = name
                parents = listOf(parentId)
            }
            driveService.files().create(metadata, content)
                .setFields("id")
                .execute()
                .id
        }
    }

    suspend fun downloadJsonFile(fileId: String): String = withContext(Dispatchers.IO) {
        val out = ByteArrayOutputStream()
        driveService.files().get(fileId)
            .executeMediaAndDownloadTo(out)
        out.toString(Charsets.UTF_8.name())
    }

    suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        driveService.files().delete(fileId).execute()
    }
}

package com.wyldsoft.notes.sync

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GoogleDriveManager {

    fun getSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        getSignInClient(context).signOut().addOnCompleteListener { onComplete() }
    }

    suspend fun uploadDatabaseFile(context: Context, account: GoogleSignInAccount): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Checkpoint the WAL to flush all pending writes
                val db = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    com.wyldsoft.notes.data.database.NotesDatabase::class.java,
                    "notes_database"
                ).build()
                db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")

                val dbFile = context.getDatabasePath("notes_database")
                if (!dbFile.exists()) {
                    return@withContext Result.failure(Exception("Database file not found"))
                }

                // Copy to temp file to avoid locking issues
                val tempFile = File(context.cacheDir, "notes_database_backup_temp")
                dbFile.copyTo(tempFile, overwrite = true)

                val credential = GoogleAccountCredential.usingOAuth2(
                    context, listOf(DriveScopes.DRIVE_FILE)
                )
                credential.selectedAccount = account.account

                val driveService = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("Notes App").build()

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = "notes_database_backup_$timestamp"
                }

                val mediaContent = FileContent("application/octet-stream", tempFile)
                val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, name")
                    .execute()

                tempFile.delete()

                Result.success(uploadedFile.name)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

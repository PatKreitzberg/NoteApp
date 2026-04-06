package com.wyldsoft.notes

import android.app.Application
import android.os.Build
import com.onyx.android.sdk.rx.RxBaseAction
import com.onyx.android.sdk.utils.ResManager
import com.wyldsoft.notes.data.database.NotesDatabase
import com.wyldsoft.notes.sync.SyncRepository
import com.wyldsoft.notes.sync.SyncWorker
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Application entry point. Initializes Onyx SDK resource manager and RxAction system,
 * and enables hidden API bypass on Android R+ for SDK compatibility.
 * Must be declared in AndroidManifest.xml as the application class.
 */
class ScrotesApp : Application() {
    lateinit var database: NotesDatabase
        private set

    lateinit var syncRepository: SyncRepository
        private set

    override fun onCreate() {
        super.onCreate()
        ResManager.init(this)
        RxBaseAction.init(this)
        database = NotesDatabase.getInstance(this)
        syncRepository = SyncRepository(
            noteDao = database.noteDao(),
            notebookDao = database.notebookDao(),
            folderDao = database.folderDao(),
            shapeDao = database.shapeDao(),
            deletedItemDao = database.deletedItemDao(),
            syncStateDao = database.syncStateDao(),
            context = this
        )
        SyncWorker.schedule(this)
        checkHiddenApiBypass()
    }

    private fun checkHiddenApiBypass() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}
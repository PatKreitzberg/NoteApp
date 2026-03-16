package com.wyldsoft.notes

import android.app.Application
import android.os.Build
import com.onyx.android.sdk.rx.RxBaseAction
import com.onyx.android.sdk.utils.ResManager
import com.wyldsoft.notes.data.database.NotesDatabase
import com.wyldsoft.notes.data.repository.*
import com.wyldsoft.notes.gestures.GestureSettingsRepository
import com.wyldsoft.notes.settings.DisplaySettingsRepository
import com.wyldsoft.notes.htr.GestureRecognitionManager
import com.wyldsoft.notes.htr.HTRManager
import com.wyldsoft.notes.htr.HTRRunManager
import com.wyldsoft.notes.presentation.viewmodel.SyncViewModel
import com.wyldsoft.notes.sdkintegration.DeviceHelper
import com.wyldsoft.notes.sync.SyncRepository
import com.wyldsoft.notes.sync.SyncWorker
import org.lsposed.hiddenapibypass.HiddenApiBypass

class ScrotesApp : Application() {

    val database: NotesDatabase by lazy { NotesDatabase.getDatabase(this) }

    val noteRepository: NoteRepository by lazy {
        NoteRepositoryImpl(
            db = database,
            noteDao = database.noteDao(),
            shapeDao = database.shapeDao(),
            deletedItemDao = database.deletedItemDao(),
            folderDao = database.folderDao()
        )
    }

    val folderRepository: FolderRepository by lazy {
        FolderRepositoryImpl(
            db = database,
            folderDao = database.folderDao(),
            notebookDao = database.notebookDao(),
            noteDao = database.noteDao(),
            shapeDao = database.shapeDao(),
            deletedItemDao = database.deletedItemDao()
        )
    }

    val notebookRepository: NotebookRepository by lazy {
        NotebookRepositoryImpl(
            db = database,
            notebookDao = database.notebookDao(),
            noteDao = database.noteDao(),
            shapeDao = database.shapeDao(),
            deletedItemDao = database.deletedItemDao(),
            folderRepository = folderRepository
        )
    }

    val gestureSettingsRepository: GestureSettingsRepository by lazy {
        GestureSettingsRepository(this)
    }

    val displaySettingsRepository: DisplaySettingsRepository by lazy {
        DisplaySettingsRepository(this)
    }

    val defaultNoteSettingsRepository: com.wyldsoft.notes.settings.DefaultNoteSettingsRepository by lazy {
        com.wyldsoft.notes.settings.DefaultNoteSettingsRepository(this)
    }

    val recognizedSegmentRepository: RecognizedSegmentRepository by lazy {
        RecognizedSegmentRepository(database.recognizedSegmentDao())
    }

    val htrRunManager: HTRRunManager by lazy {
        HTRRunManager(HTRManager(), GestureRecognitionManager(), recognizedSegmentRepository)
    }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(
            noteDao = database.noteDao(),
            notebookDao = database.notebookDao(),
            folderDao = database.folderDao(),
            shapeDao = database.shapeDao(),
            deletedItemDao = database.deletedItemDao(),
            syncStateDao = database.syncStateDao(),
            context = this
        )
    }

    val syncViewModel: SyncViewModel by lazy {
        SyncViewModel(syncRepository)
    }

    override fun onCreate() {
        super.onCreate()
        if (DeviceHelper.isOnyxDevice) {
            ResManager.init(this)
            RxBaseAction.init(this)
        }
        checkHiddenApiBypass()
        SyncWorker.schedule(this)
    }

    private fun checkHiddenApiBypass() {
        // Necessary for Onyx to compile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}

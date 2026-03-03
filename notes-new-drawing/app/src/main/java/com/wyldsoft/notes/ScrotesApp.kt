package com.wyldsoft.notes

import android.app.Application
import android.os.Build
import com.onyx.android.sdk.rx.RxBaseAction
import com.onyx.android.sdk.utils.ResManager
import com.wyldsoft.notes.data.database.NotesDatabase
import com.wyldsoft.notes.data.repository.*
import com.wyldsoft.notes.gestures.GestureSettingsRepository
import com.wyldsoft.notes.htr.HTRManager
import com.wyldsoft.notes.htr.HTRRunManager
import com.wyldsoft.notes.sdkintegration.DeviceHelper
import org.lsposed.hiddenapibypass.HiddenApiBypass

class ScrotesApp : Application() {

    val database: NotesDatabase by lazy { NotesDatabase.getDatabase(this) }

    val noteRepository: NoteRepository by lazy {
        NoteRepositoryImpl(noteDao = database.noteDao(), shapeDao = database.shapeDao())
    }

    val folderRepository: FolderRepository by lazy {
        FolderRepositoryImpl(folderDao = database.folderDao())
    }

    val notebookRepository: NotebookRepository by lazy {
        NotebookRepositoryImpl(notebookDao = database.notebookDao(), noteDao = database.noteDao())
    }

    val gestureSettingsRepository: GestureSettingsRepository by lazy {
        GestureSettingsRepository(this)
    }

    val recognizedSegmentRepository: RecognizedSegmentRepository by lazy {
        RecognizedSegmentRepository(database.recognizedSegmentDao())
    }

    val htrRunManager: HTRRunManager by lazy {
        HTRRunManager(HTRManager(), recognizedSegmentRepository)
    }

    override fun onCreate() {
        super.onCreate()
        if (DeviceHelper.isOnyxDevice) {
            ResManager.init(this)
            RxBaseAction.init(this)
        }
        checkHiddenApiBypass()
    }

    private fun checkHiddenApiBypass() {
        // Necessary for Onyx to compile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}
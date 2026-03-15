package com.wyldsoft.notes.export

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import java.io.File

/**
 * Handles the final step of exporting a PDF: either sharing via Intent
 * or triggering the system file-save picker.
 *
 * @param context          Application or Activity context.
 * @param file             The generated PDF file (in cache dir).
 * @param action           Whether to share or save to a user-chosen location.
 * @param saveFileLauncher ActivityResultLauncher for CreateDocument; required when action == SAVE_FILE.
 * @param onFilePending    Called with the file before launching the save picker so the
 *                         caller can keep a reference to copy it after the picker returns.
 */
fun dispatchExport(
    context: Context,
    file: File,
    action: ExportAction,
    saveFileLauncher: ActivityResultLauncher<String>,
    onFilePending: (File) -> Unit
) {
    when (action) {
        ExportAction.SHARE -> {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF"))
        }
        ExportAction.SAVE_FILE -> {
            onFilePending(file)
            saveFileLauncher.launch(file.name)
        }
    }
}

package com.wyldsoft.notes.sync

import android.content.Context
import androidx.work.*
import com.wyldsoft.notes.ScrotesApp
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ScrotesApp
        return when (app.syncRepository.performSync()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.PartialSuccess -> Result.success()
            is SyncResult.NotSignedIn -> Result.success() // not signed in — don't retry
            is SyncResult.AlreadyRunning -> Result.success()
            is SyncResult.Failure -> Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "sync_periodic"
        private const val ONE_TIME_WORK_NAME = "sync_one_time"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun scheduleOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

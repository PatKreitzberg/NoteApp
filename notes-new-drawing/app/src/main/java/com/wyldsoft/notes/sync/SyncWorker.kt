package com.wyldsoft.notes.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wyldsoft.notes.ScrotesApp
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork")
        val app = applicationContext as ScrotesApp
        return when (app.syncRepository.performSync()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.PartialSuccess -> Result.success()
            is SyncResult.NotSignedIn -> Result.success()
            is SyncResult.AlreadyRunning -> Result.success()
            is SyncResult.Failure -> Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val PERIODIC_WORK_NAME = "sync_periodic"
        private const val ONE_TIME_WORK_NAME = "sync_one_time"

        fun schedule(context: Context) {
            Log.d(TAG, "schedule")
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
            Log.d(TAG, "scheduleOneTime")
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

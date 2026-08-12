package com.diprish.utilitymeter.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.diprish.utilitymeter.UtilityMeterApp
import java.util.concurrent.TimeUnit

/**
 * Scheduled backup. Silently re-authorizes (works without a prompt once the
 * user has connected Drive) and uploads. If consent has lapsed it simply
 * succeeds without backing up — the user reconnects from the Backup screen.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as UtilityMeterApp
        if (!app.backupManager.isAutoEnabled()) return Result.success()

        return when (val outcome = app.authManager.authorize()) {
            is AuthOutcome.Authorized -> try {
                app.backupManager.backup(outcome.token)
                Result.success()
            } catch (t: Throwable) {
                Result.retry()
            }
            is AuthOutcome.NeedsConsent -> Result.success()
            is AuthOutcome.Error -> Result.retry()
        }
    }
}

/** Enables or cancels the daily backup job. */
object BackupScheduler {
    private const val WORK_NAME = "daily_drive_backup"

    fun setEnabled(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}

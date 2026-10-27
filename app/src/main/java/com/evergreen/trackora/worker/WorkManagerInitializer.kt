package com.evergreen.trackora.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily reminder about finished work that has not been delivered.
 */
@Singleton
class WorkManagerInitializer @Inject constructor() {
    
    companion object {
        private const val WORK_NAME = "undelivered_work_reminder"
        // The 15-minute job an installed build may still have queued.
        // Cancelled on next launch so upgrades stop being spammed.
        private const val LEGACY_WORK_NAME = "in_progress_work_checker"
        // One day, in TimeUnit.DAYS below.
        private val REPEAT_INTERVAL = 1L
    }
    
    /**
     * Starts the reminder, once a day. It used to run every fifteen minutes,
     * which is the platform minimum and posted up to ninety-six notifications
     * a day to anyone with a single open job — the fastest way to be
     * uninstalled.
     */
    fun startPeriodicCheck(workManager: WorkManager) {
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)

        val periodicWorkRequest = PeriodicWorkRequestBuilder<UndeliveredWorkReminderWorker>(
            REPEAT_INTERVAL,
            TimeUnit.DAYS
        )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }
    
    /**
     * Cancels the periodic work (if needed for future use).
     */
    fun cancelPeriodicCheck(workManager: WorkManager) {
        workManager.cancelUniqueWork(WORK_NAME)
    }
}


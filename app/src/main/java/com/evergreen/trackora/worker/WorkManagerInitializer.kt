package com.evergreen.trackora.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initializes and manages the periodic work for checking in-progress works.
 */
@Singleton
class WorkManagerInitializer @Inject constructor() {
    
    companion object {
        private const val WORK_NAME = "in_progress_work_checker"
        // Android enforces a minimum interval of 15 minutes for periodic work
        // Note: User requested 10 minutes, but 15 minutes is the closest we can achieve
        private val REPEAT_INTERVAL = 15L
    }
    
    /**
     * Starts the periodic work that checks for in-progress works every 15 minutes.
     * Note: Android enforces a minimum interval of 15 minutes for periodic work.
     */
    fun startPeriodicCheck(workManager: WorkManager) {
        val periodicWorkRequest = PeriodicWorkRequestBuilder<InProgressWorkCheckerWorker>(
            REPEAT_INTERVAL,
            TimeUnit.MINUTES
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


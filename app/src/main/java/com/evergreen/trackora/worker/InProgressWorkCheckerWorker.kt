package com.evergreen.trackora.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.repository.WorkEntryRepository
import com.evergreen.trackora.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker that periodically checks for in-progress works and shows a notification if any are found.
 * Note: Android enforces a minimum interval of 15 minutes for periodic work, so this runs every 15 minutes.
 */
@HiltWorker
class InProgressWorkCheckerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: WorkEntryRepository
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        return try {
            // Get all in-progress works
            val inProgressWorks = repository.getEntriesByStatus(Status.IN_PROGRESS)
            
            // If there are any in-progress works, show notification
            if (inProgressWorks.isNotEmpty()) {
                val notificationHelper = NotificationHelper(applicationContext)
                notificationHelper.showInProgressWorkNotification(inProgressWorks.size)
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}


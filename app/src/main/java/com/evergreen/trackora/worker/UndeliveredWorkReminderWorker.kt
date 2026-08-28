package com.evergreen.trackora.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.repository.WorkEntryRepository
import com.evergreen.trackora.notification.NotificationHelper
import com.evergreen.trackora.settings.ReminderPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Posts the once-a-day reminder about work that is finished but not delivered.
 *
 * ### What changed and why
 *
 * This replaces a worker that ran every fifteen minutes and reported
 * `IN_PROGRESS` work. Both halves were wrong.
 *
 * The cadence was the platform minimum, so a user with one open job could be
 * notified ninety-six times a day.
 *
 * The subject was wrong too: a tradesperson knows what they are working on,
 * because they are the one working on it. What they forget is the finished
 * piece sitting on the shelf that the customer has not collected and has not
 * paid for. `COMPLETED` is exactly that state — `DELIVERED` is separate — so
 * that is what this reports.
 */
@HiltWorker
class UndeliveredWorkReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: WorkEntryRepository,
    private val reminderPreferences: ReminderPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (!reminderPreferences.enabled.first()) {
                return Result.success()
            }

            val undelivered = repository.getEntriesByStatus(Status.COMPLETED)
            if (undelivered.isNotEmpty()) {
                NotificationHelper(applicationContext)
                    .showUndeliveredWorkNotification(undelivered.size)
            }

            Result.success()
        } catch (e: Exception) {
            // Retry rather than fail: a reminder that silently stops for good
            // after one transient database error is worse than a late one.
            Result.retry()
        }
    }
}

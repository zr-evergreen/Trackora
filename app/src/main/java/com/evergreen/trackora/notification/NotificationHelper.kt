package com.evergreen.trackora.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.evergreen.trackora.MainActivity
import com.evergreen.trackora.R

/**
 * Helper class for managing notifications in the Trackora app.
 */
class NotificationHelper(private val context: Context) {
    
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    companion object {
        private const val CHANNEL_ID = "in_progress_work_channel"
        private const val CHANNEL_NAME = "In Progress Work Reminders"
        private const val NOTIFICATION_ID = 1
        
        /**
         * Creates the notification channel (required for Android 8.0+).
         */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                    enableVibration(true)
                }
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    
    /**
     * Shows a notification when there are in-progress works.
     */
    fun showInProgressWorkNotification(count: Int) {
        val title = context.getString(R.string.notification_in_progress_title)
        val message = context.resources.getQuantityString(
            R.plurals.notification_in_progress_message,
            count,
            count
        )
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_all_works)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}





















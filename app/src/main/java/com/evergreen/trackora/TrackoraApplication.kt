package com.evergreen.trackora

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.evergreen.trackora.notification.NotificationHelper
import com.evergreen.trackora.worker.WorkManagerInitializer
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject

/**
 * Application class for Trackora.
 * Hilt uses this to generate the dependency injection components.
 */
@HiltAndroidApp
class TrackoraApplication : Application() {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize WorkManager with HiltWorkerFactory
        // This must be done after super.onCreate() so Hilt injection is available
        val config = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
        WorkManager.initialize(this, config)
        
        // Create notification channel
        NotificationHelper.createNotificationChannel(this)
        
        // Get WorkManagerInitializer from Hilt and start periodic work
        val appComponent = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkManagerInitializerEntryPoint::class.java
        )
        val workManagerInitializer = appComponent.workManagerInitializer()
        val workManager = WorkManager.getInstance(this)
        workManagerInitializer.startPeriodicCheck(workManager)
    }
}

// Entry point for accessing WorkManagerInitializer
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface WorkManagerInitializerEntryPoint {
    fun workManagerInitializer(): WorkManagerInitializer
}


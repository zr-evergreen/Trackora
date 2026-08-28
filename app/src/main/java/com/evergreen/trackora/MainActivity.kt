package com.evergreen.trackora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.evergreen.trackora.locale.LocaleManager
import com.evergreen.trackora.navigation.NavGraph
import com.evergreen.trackora.navigation.TodayRoute
import com.evergreen.trackora.ui.theme.TrackoraTheme
import com.evergreen.trackora.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Main activity for the Trackora application.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var localeManager: LocaleManager
    @Inject lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            localeManager.applySavedLocale()
            themeManager.applySavedTheme()
        }
        requestNotificationPermissionIfNeeded()

        enableEdgeToEdge()
        setContent {
            TrackoraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        startDestination = TodayRoute
                    )
                }
            }
        }
    }

    /**
     * Asks for POST_NOTIFICATIONS on Android 13 and above.
     *
     * The permission was declared in the manifest but never requested, so the
     * daily reminder — the only thing this app does while closed — silently
     * never fired on any modern device. Declaring a runtime permission without
     * requesting it is indistinguishable from not having the feature.
     *
     * Asked once at launch rather than behind a rationale screen: the reminder
     * is on by default and is the app's single background behaviour, so there
     * is nothing to explain that the system dialog does not already say. A
     * denial is respected — the system stops showing the dialog, the worker
     * still runs, and its notification is simply dropped by the platform.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* respected either way */ }
}


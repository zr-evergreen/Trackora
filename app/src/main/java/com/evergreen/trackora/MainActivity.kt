package com.evergreen.trackora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import android.window.SplashScreenView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
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
import com.evergreen.trackora.ui.theme.TrackoraMotion
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
        // Before super.onCreate, as the library requires: it reads the splash
        // theme, then swaps in Theme.Trackora once the first frame is ready.
        // The splash stays only as long as the app takes to draw; nothing
        // here holds it longer.
        installSplashScreen().setOnExitAnimationListener(::leaveSplash)
        super.onCreate(savedInstanceState)
        // The stored choices, in case they differ from what is already in
        // place. Normally they do not: AppCompat applies the stored language
        // and TrackoraApplication the theme before this activity is created,
        // so neither recreates it. The first launch after an update does
        // recreate it once, while both stores are filled.
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
     * Hands over from the splash to the first screen, which is already drawn
     * beneath it: the splash fades as its mark lifts slightly towards the
     * viewer, instead of being cut away. Short, because by now the app is
     * ready and the splash is only in the way; with animations turned off it
     * is removed at once. A splash without a mark just fades.
     */
    private fun leaveSplash(splash: SplashScreenViewProvider) {
        val duration = TrackoraMotion.MEDIUM.toLong()
        // TrackoraMotion.EmphasizedAccelerate, as a platform interpolator.
        val leaving = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
        splash.iconOrNull()?.animate()
            ?.scaleX(1.08f)
            ?.scaleY(1.08f)
            ?.setDuration(duration)
            ?.setInterpolator(leaving)
            ?.start()
        splash.view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(leaving)
            .withEndAction(splash::remove)
            .start()
    }

    /**
     * The splash's mark, or null when it has none.
     *
     * Android 12 and later sometimes show a splash of plain colour with no
     * icon, for example when the app is opened from its daily reminder
     * notification. For that splash, core-splashscreen 1.0.1's `iconView`
     * throws a NullPointerException instead of returning null, so on those
     * versions the icon is read from the platform's splash view directly.
     * Before Android 12 the library draws the splash itself, and its icon
     * view is always there.
     */
    private fun SplashScreenViewProvider.iconOrNull(): View? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PlatformSplash.iconOf(view) else iconView

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

/**
 * The parts of Android 12's own splash that MainActivity reads. Kept out of
 * MainActivity, so Android 7 to 11, which do not have [SplashScreenView],
 * never load a class that refers to it.
 */
@RequiresApi(Build.VERSION_CODES.S)
private object PlatformSplash {
    fun iconOf(splash: View): View? = (splash as? SplashScreenView)?.iconView
}


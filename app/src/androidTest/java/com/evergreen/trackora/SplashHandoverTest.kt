package com.evergreen.trackora

import android.Manifest
import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.window.SplashScreen
import android.window.SplashScreenView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens MainActivity behind each style of splash Android 13 and later can
 * show, and checks the hand-over from the splash to the app.
 *
 * The solid-colour splash has no icon. It is what Android shows when the app
 * is opened from its daily reminder notification, and MainActivity used to
 * crash on it: core-splashscreen 1.0.1 throws when asked for an icon the
 * splash does not have. Android 12 shows that splash too, but only Android 13
 * lets a test ask for it, hence the minimum.
 *
 * The splash is watched where Android attaches it, on the activity's window,
 * so a test that never saw one fails rather than passing without having
 * checked anything.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class SplashHandoverTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun allowNotifications() {
        // Otherwise MainActivity asks for it, and the dialog opens over the app.
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    @Test
    fun aSplashWithoutAnIconFadesOutAndTheAppOpens() {
        val splash = openBehind(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR)

        assertTrue("Android showed no splash", splash.wasShown)
        assertNull("Expected a splash without an icon", splash.icon)
        assertEquals("The splash did not fade out", 0f, splash.alphaWhenRemoved, 0f)
    }

    @Test
    fun aSplashWithAnIconFadesOutAsItsIconLifts() {
        val splash = openBehind(SplashScreen.SPLASH_SCREEN_STYLE_ICON)

        assertTrue("Android showed no splash", splash.wasShown)
        assertNotNull("Expected a splash with an icon", splash.icon)
        assertEquals("The splash did not fade out", 0f, splash.alphaWhenRemoved, 0f)
        assertEquals("The icon did not lift", 1.08f, splash.iconScaleWhenRemoved, 0.001f)
    }

    /** What the window saw of the splash. Written on the main thread, read after [removed]. */
    private class Splash {
        val removed = CountDownLatch(1)
        var wasShown = false
        var icon: View? = null
        var alphaWhenRemoved = Float.NaN
        var iconScaleWhenRemoved = Float.NaN
    }

    /**
     * Opens MainActivity with [style] as the reminder notification does, in a
     * new task, and waits until the splash has been removed. Had MainActivity
     * thrown while leaving the splash, the app's process, and this test with
     * it, would have died instead.
     */
    private fun openBehind(style: Int): Splash {
        val splash = Splash()
        val watcher = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.CREATED) {
                (activity.window.decorView as ViewGroup)
                    .setOnHierarchyChangeListener(SplashWatcher(splash))
            }
        }
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        monitor.addLifecycleCallback(watcher)
        try {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val options = ActivityOptions.makeBasic().setSplashScreenStyle(style).toBundle()
            ActivityScenario.launch<MainActivity>(intent, options).use { scenario ->
                assertTrue(
                    "The splash was not shown, or not removed, within 10 s",
                    splash.removed.await(10, TimeUnit.SECONDS)
                )
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
        } finally {
            monitor.removeLifecycleCallback(watcher)
        }
        return splash
    }

    /**
     * Sees the splash arrive on the window, then leave it. Its leaving is
     * taken from the splash itself: Android takes it off the window without
     * telling the window's listener (checked on API 34).
     */
    private class SplashWatcher(private val splash: Splash) : ViewGroup.OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View, child: View) {
            if (child !is SplashScreenView) return
            splash.wasShown = true
            splash.icon = child.iconView
            child.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    splash.alphaWhenRemoved = view.alpha
                    splash.iconScaleWhenRemoved = splash.icon?.scaleX ?: Float.NaN
                    splash.removed.countDown()
                }
            })
        }

        override fun onChildViewRemoved(parent: View, child: View) = Unit
    }
}

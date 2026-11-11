package com.evergreen.trackora.recovery

import android.app.Activity
import android.content.Intent
import android.util.Log

/**
 * Starts the app again in a new process.
 *
 * Finishing the activity is not enough: the process would live on, and with
 * it the open database, which remembers that it was refused. This asks
 * Android to start the launcher activity in a new task, then ends the
 * process, so Android starts a new one for that activity.
 *
 * Whether Android then does start it cannot be known from here: the process
 * is gone. The recovery screen says how to reopen the app by hand should it
 * not come back, and the next launch opens the database normally.
 */
internal object AppRestart {

    private const val TAG = "TrackoraRecovery"

    /**
     * Returns false, and leaves the process running, when the new start could
     * not even be asked for: no launcher activity, or Android refused the
     * request. The caller then shows how to close and reopen the app. On
     * success the process ends and this does not return.
     */
    fun restart(activity: Activity): Boolean = restart(
        launcher = activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.component,
        start = { activity.startActivity(Intent.makeRestartActivityTask(it)) },
        exit = { Runtime.getRuntime().exit(0) },
        log = { e -> Log.e(TAG, "Could not ask Android to start Trackora again", e) },
    )

    internal fun <T : Any> restart(launcher: T?, start: (T) -> Unit, exit: () -> Unit, log: (Throwable?) -> Unit): Boolean {
        if (launcher == null) {
            log(null)
            return false
        }
        try {
            start(launcher)
        } catch (e: RuntimeException) {
            log(e)
            return false
        }
        exit()
        return true
    }
}

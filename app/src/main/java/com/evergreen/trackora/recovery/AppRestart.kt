package com.evergreen.trackora.recovery

import android.app.Activity
import android.content.Intent

/**
 * Starts the app again in a new process.
 *
 * Finishing the activity is not enough: the process would live on, and with
 * it the open database, which remembers that it was refused. This asks
 * Android to start the launcher activity in a new task, then ends the
 * process, so Android starts a new one for that activity. Should a phone not
 * do that, the recovery screen already says how to reopen the app by hand,
 * and the next launch opens the database normally.
 */
internal object AppRestart {

    fun restart(activity: Activity) {
        val launcher = activity.packageManager.getLaunchIntentForPackage(activity.packageName)?.component
        if (launcher != null) activity.startActivity(Intent.makeRestartActivityTask(launcher))
        Runtime.getRuntime().exit(0)
    }
}

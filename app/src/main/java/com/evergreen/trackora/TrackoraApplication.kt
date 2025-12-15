package com.evergreen.trackora

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Trackora.
 * Hilt uses this to generate the dependency injection components.
 */
@HiltAndroidApp
class TrackoraApplication : Application()


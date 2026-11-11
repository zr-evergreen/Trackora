package com.evergreen.trackora.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The restart ends the process only after Android has accepted the request to
 * start the app again. When the request cannot be made, the process is left
 * running and the caller is told, so the screen can say how to reopen the app.
 */
class AppRestartTest {

    private val events = mutableListOf<String>()
    private val logged = mutableListOf<Throwable?>()

    private fun restart(launcher: String?, start: (String) -> Unit = { events += "start $it" }) =
        AppRestart.restart(launcher, start, exit = { events += "exit" }, log = { logged += it })

    @Test
    fun theProcessEndsOnlyAfterTheNewStartWasAskedFor() {
        assertTrue(restart("launcher"))
        assertEquals(listOf("start launcher", "exit"), events)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun withoutALauncherActivityNothingEndsAndTheCallerIsTold() {
        assertFalse(restart(null))
        assertEquals(emptyList<String>(), events)
        assertEquals(1, logged.size)
    }

    @Test
    fun aRefusedStartLeavesTheProcessRunningAndTheCallerIsTold() {
        val refusal = SecurityException("not allowed")
        assertFalse(restart("launcher") { throw refusal })
        assertEquals("The process ended although nothing was started", emptyList<String>(), events)
        assertEquals(listOf<Throwable?>(refusal), logged)
    }
}

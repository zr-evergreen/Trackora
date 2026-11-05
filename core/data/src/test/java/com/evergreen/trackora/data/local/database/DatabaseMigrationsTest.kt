package com.evergreen.trackora.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The migrations must reach the current version from every version a user
 * could have, one step at a time. A gap is no longer papered over by deleting
 * the database: the app would fail to open it. So a gap has to fail here
 * first, on every build, before it can reach a phone.
 */
class DatabaseMigrationsTest {

    private val steps = DatabaseMigrations.ALL.map { it.startVersion to it.endVersion }

    @Test
    fun `there is one migration for every version step up to the current version`() {
        val expected = (1 until TrackoraDatabase.VERSION).map { it to it + 1 }

        assertEquals(expected, steps)
    }

    @Test
    fun `no version step has two migrations`() {
        assertEquals(steps.distinct(), steps)
    }
}

package com.evergreen.trackora

import android.Manifest
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evergreen.trackora.data.local.database.DatabaseDamage
import com.evergreen.trackora.data.local.database.TrackoraDatabase
import com.evergreen.trackora.data.local.entity.WorkEntry
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.ui.recovery.RecoveryCheckButtonTag
import com.evergreen.trackora.ui.recovery.RecoveryScreenTag
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Damage first found while the app is open, by the real fail-safe: the
 * recovery screen takes the place of every screen at once, in the same
 * activity and process, with no recreation or restart, and the app's
 * navigation, Add included, is gone.
 *
 * The damage is found by a [TrackoraDatabase] opened here, with the app's own
 * builder and fail-safe, on the app's own database path: whether the app's
 * own instance is already open depends on the tests run before this one in
 * the same process, so the test does not rely on it. A screen's first read
 * would take the same way, through the same fail-safe.
 *
 * It leaves this test process with the app's database refused, as a real
 * refusal would: MainActivity opened by a later test shows the recovery
 * screen. SplashHandoverTest checks only the splash and does not mind.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryOnDamageTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val database: File get() = context.getDatabasePath(TrackoraDatabase.DATABASE_NAME)
    private val seedName = "recovery-on-damage-seed"

    @Before
    fun allowNotifications() {
        // Otherwise MainActivity asks for it on Android 13 and later, over the app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun deleteTestFiles() {
        listOf(database, context.getDatabasePath(seedName)).forEach { db ->
            db.parentFile?.listFiles { f -> f.name.startsWith(db.name) }?.forEach { it.deleteRecursively() }
        }
    }

    private val appLabels get() = listOf(R.string.nav_today, R.string.nav_all_work, R.string.nav_reports, R.string.nav_settings).map(context::getString)

    @Test
    fun damageFoundWhileTheAppIsOpenPutsRecoveryInPlaceOfEveryScreenAtOnce() {
        assertFalse("An earlier test left the app's database refused in this process", DatabaseDamage.isRefusedUntilRestart(context))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val settings = context.getString(R.string.nav_settings)
            compose.waitUntil(20_000) { compose.onAllNodesWithText(settings).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithTag(RecoveryScreenTag).assertCountEquals(0)
            var before: MainActivity? = null
            scenario.onActivity { before = it }

            damageIsFoundByTheFailSafe()

            compose.waitUntil(10_000) { compose.onAllNodesWithTag(RecoveryScreenTag).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(RecoveryCheckButtonTag).assertIsDisplayed()
            appLabels.forEach { label ->
                assertEquals("'$label' is still reachable", 0, compose.onAllNodesWithText(label).fetchSemanticsNodes().size)
            }
            assertEquals(
                "Add is still reachable", 0,
                compose.onAllNodesWithContentDescription(context.getString(R.string.content_add_work_entry)).fetchSemanticsNodes().size,
            )
            // The same activity, still running: nothing was recreated or restarted.
            var after: MainActivity? = null
            scenario.onActivity { after = it }
            assertSame("MainActivity was recreated", before, after)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertTrue(DatabaseDamage.isRefusedUntilRestart(context))
        }
    }

    /**
     * Puts a database with entries in place of the app's, with the entries
     * table's first page damaged, then reads it through the app's own builder
     * and fail-safe, which finds the damage and refuses the database. The
     * files are replaced, not written over, so an instance the app already
     * has open keeps reading the files it had open.
     */
    private fun damageIsFoundByTheFailSafe() = runBlocking {
        val seed = TrackoraDatabase.build(context, seedName)
        try {
            repeat(5) {
                seed.workEntryDao().insertEntry(WorkEntry(title = "Entry $it", status = Status.COMPLETED, date = LocalDate.of(2026, 1, 1)))
            }
        } finally {
            seed.close()
        }
        val seedFile = context.getDatabasePath(seedName)
        val (root, pageSize) = SQLiteDatabase.openDatabase(seedFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val root = db.rawQuery("SELECT rootpage FROM sqlite_master WHERE name = 'work_entries'", null).use { it.moveToFirst(); it.getInt(0) }
            val size = db.rawQuery("PRAGMA page_size", null).use { it.moveToFirst(); it.getInt(0) }
            root to size
        }
        listOf("", "-wal", "-shm", "-journal").forEach { File(database.path + it).delete() }
        seedFile.copyTo(database)
        RandomAccessFile(database, "rw").use { it.seek((root - 1L) * pageSize); it.write(ByteArray(pageSize) { 0xAB.toByte() }) }

        val opened = TrackoraDatabase.build(context)
        runCatching { opened.workEntryDao().getEntriesByStatus(Status.COMPLETED.name) }
        assertTrue("The fail-safe wrote no marker", File(database.path + ".damaged").exists())
    }
}

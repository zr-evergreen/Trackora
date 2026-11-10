package com.evergreen.trackora

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evergreen.trackora.data.local.database.TrackoraDatabase
import com.evergreen.trackora.theme.ThemeManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * The backup rules packaged in this APK, checked against the paths the app
 * actually writes on this device, rather than against names copied into a
 * test. The fail-safe is made to record damage for real, on a database of the
 * test's own, and the marker and copies it writes are mapped onto the app's
 * database name.
 *
 * BackupRulesTest checks the rule files themselves.
 */
@RunWith(AndroidJUnit4::class)
class BackupRulesRuntimePathsTest {

    private data class Rule(val kind: String, val domain: String, val path: String)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The test's own database and preferences, never the app's. */
    private val probe = "backup-rules-probe"
    private val databases: File get() = context.getDatabasePath(probe).parentFile!!

    @Before
    @After
    fun deleteProbeFiles() {
        databases.listFiles { f -> f.name.startsWith(probe) }?.forEach { it.deleteRecursively() }
        context.deleteSharedPreferences(probe)
    }

    /** Where each domain the rules name begins on this device, found from the app's own APIs. */
    private val domains: Map<String, File> by lazy {
        mapOf(
            "database" to databases,
            "file" to context.filesDir,
            "sharedpref" to sharedPreferencesFolder(),
            "external" to context.getExternalFilesDir(null)!!,
        )
    }

    /** The folder SharedPreferences writes to, found by writing to it. */
    private fun sharedPreferencesFolder(): File {
        context.getSharedPreferences(probe, Context.MODE_PRIVATE).edit().putBoolean(probe, true).commit()
        return context.filesDir.parentFile!!.walkTopDown().maxDepth(2).single { it.name == "$probe.xml" }.parentFile!!
    }

    /** The domain [file] is in, and its path there. */
    private fun locate(file: File): Pair<String, String> =
        domains.entries.single { file.path.startsWith(it.value.path + "/") }
            .let { (domain, root) -> domain to file.relativeTo(root).path }

    /** The rules packaged in this APK, by section. */
    private val packaged: Map<String, List<Rule>> by lazy { parse(R.xml.data_extraction_rules) + parse(R.xml.backup_rules) }

    private fun parse(id: Int): Map<String, List<Rule>> {
        val sections = linkedMapOf<String, MutableList<Rule>>()
        var section = ""
        val parser = context.resources.getXml(id)
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "cloud-backup", "device-transfer", "full-backup-content" -> {
                        section = parser.name
                        sections[section] = mutableListOf()
                    }
                    "include", "exclude" -> sections.getValue(section) += Rule(
                        parser.name,
                        parser.getAttributeValue(null, "domain"),
                        parser.getAttributeValue(null, "path"),
                    )
                }
            }
        } finally {
            parser.close()
        }
        return sections
    }

    /** Whether [rule] excludes the file at [path] in [domain], as Android matches paths. */
    private fun excludes(rule: Rule, domain: String, path: String): Boolean {
        if (rule.kind != "exclude" || rule.domain != domain) return false
        val excluded = rule.path.removePrefix("./").trimEnd('/')
        return excluded.isEmpty() || excluded == "." || path == excluded || path.startsWith("$excluded/")
    }

    @Test
    fun theFailSafesMarkerIsBackedUpAndItsCopiesAreNotWhereItActuallyWritesThem() {
        val database = File(databases, probe)
        databases.mkdirs()
        database.writeBytes(ByteArray(8192) { 0x5A }) // not a database: the fail-safe records it as damaged
        val opened = TrackoraDatabase.build(context, probe)
        assertTrue("Opened a file that is not a database", runCatching { opened.openHelper.writableDatabase }.isFailure)
        opened.close()

        // What the fail-safe wrote beside the database: one marker file and one folder of copies.
        val written = databases.listFiles { f -> f.name.startsWith("$probe.") }!!.toList()
        val marker = written.single { it.isFile }
        val copies = written.single { it.isDirectory }
        val copy = copies.walkTopDown().first { it.isFile }

        // The same paths for the app's own database.
        fun forTheAppsDatabase(f: File) = TrackoraDatabase.DATABASE_NAME + locate(f).second.removePrefix(probe)
        val markerPath = forTheAppsDatabase(marker)
        val copiesPath = forTheAppsDatabase(copies)
        val copyPath = forTheAppsDatabase(copy)

        assertEquals(setOf("cloud-backup", "device-transfer", "full-backup-content"), packaged.keys)
        packaged.forEach { (section, rules) ->
            assertTrue("$section leaves out the marker, $markerPath", rules.none { excludes(it, "database", markerPath) })
            assertTrue("$section backs up the copies folder, $copiesPath", rules.any { excludes(it, "database", copiesPath) })
            assertTrue("$section backs up a copy, $copyPath", rules.any { excludes(it, "database", copyPath) })
        }
    }

    @Test
    fun theDatabaseAndSettingsAreBackedUpAndPhotosAreNotWhereTheAppActuallyStoresThem() {
        val database = context.getDatabasePath(TrackoraDatabase.DATABASE_NAME)
        val keep = listOf("", "-wal", "-shm", "-journal").map { File(database.path + it) } +
            // The names LocaleModule and SettingsModule give their DataStores.
            listOf("settings_prefs", "theme_prefs", "locale_prefs").map { context.preferencesDataStoreFile(it) } +
            File(domains.getValue("sharedpref"), "${ThemeManager.LAUNCH_THEME_PREFS}.xml")
        val photos = context.getExternalFilesDir(null)!!
        val leaveOut = listOf(File(photos, "IMG_20261107_101010.jpg"), File(photos, "restored/1/IMG_20261107_101010.jpg"))

        packaged.forEach { (section, rules) ->
            keep.map(::locate).forEach { (domain, path) ->
                assertTrue("$section leaves out $domain/$path", rules.none { excludes(it, domain, path) })
            }
            leaveOut.map(::locate).forEach { (domain, path) ->
                assertTrue("$section backs up $domain/$path", rules.any { excludes(it, domain, path) })
            }
        }
    }
}

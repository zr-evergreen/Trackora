package com.evergreen.trackora

import com.evergreen.trackora.data.local.database.TrackoraDatabase
import com.evergreen.trackora.theme.ThemeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The backup rules the app ships, read from the source files, so that a change
 * that would back up the wrong files, or leave out the right ones, fails the
 * build.
 *
 * Android uses data_extraction_rules.xml on Android 12 and later (one section
 * for the cloud backup, one for a transfer to a new device) and
 * backup_rules.xml on Android 7 to 11 (one set for both). In each, everything
 * not excluded is backed up, unless an `<include>` appears, after which only
 * what is included is. BackupRulesRuntimePathsTest checks the same rules
 * against the paths the app actually writes, on a device.
 */
class BackupRulesTest {

    private data class Rule(val kind: String, val domain: String, val path: String)

    private val db = TrackoraDatabase.DATABASE_NAME

    /** What must always be backed up: the database, its settings and the fail-safe's marker. */
    private val mustBeBackedUp = listOf(
        "database" to db,
        "database" to "$db-wal",
        "database" to "$db-shm",
        "database" to "$db-journal",
        // The marker, kept on purpose so that a damaged database stays refused when restored.
        "database" to "$db.damaged",
        "file" to "datastore/settings_prefs.preferences_pb",
        "file" to "datastore/theme_prefs.preferences_pb",
        "file" to "datastore/locale_prefs.preferences_pb",
        "sharedpref" to "${ThemeManager.LAUNCH_THEME_PREFS}.xml",
    )

    /** What must never be backed up, by any route. */
    private val mustBeExcluded = listOf(
        // The fail-safe's copies, and anything in their folder.
        "database" to "$db.damaged-copies",
        "database" to "$db.damaged-copies/1793900000000/$db",
        // Photos, wherever in the external files folder they are.
        "external" to "IMG_20261107_101010.jpg",
        "external" to "IMG_20261107_101010_123_gallery.png",
        "external" to "restored/1/IMG_20261107_101010.jpg",
    )

    private fun xml(file: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main", file)).documentElement

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

    private fun Element.rules(): List<Rule> =
        children().map { Rule(it.tagName, it.getAttribute("domain"), it.getAttribute("path")) }

    private val modern by lazy { xml("res/xml/data_extraction_rules.xml") }

    /** Every set of rules Android may apply, by name. */
    private val routes: Map<String, List<Rule>> by lazy {
        val legacy = xml("res/xml/backup_rules.xml")
        assertEquals("data-extraction-rules", modern.tagName)
        assertEquals("full-backup-content", legacy.tagName)
        modern.children().associate { it.tagName to it.rules() } + ("full-backup-content" to legacy.rules())
    }

    /** Whether [rule] excludes the file at [path] in [domain], as Android matches paths. */
    private fun excludes(rule: Rule, domain: String, path: String): Boolean {
        if (rule.kind != "exclude") return false
        val target = when {
            rule.domain == domain -> path
            rule.domain == "root" -> when (domain) {
                "database" -> "databases/$path"
                "file" -> "files/$path"
                "sharedpref" -> "shared_prefs/$path"
                else -> return false
            }
            else -> return false
        }
        val excluded = rule.path.removePrefix("./").trimEnd('/')
        return excluded.isEmpty() || excluded == "." || target == excluded || target.startsWith("$excluded/")
    }

    @Test
    fun theManifestUsesTheseRulesAndAndroidsOwnBackup() {
        val application = xml("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("true", application.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(android, "dataExtractionRules"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(android, "fullBackupContent"))
        assertFalse("A backup agent would replace these rules", application.hasAttributeNS(android, "backupAgent"))
    }

    @Test
    fun bothRoutesOnAndroid12AndLaterHaveTheirOwnRules() {
        // A missing section would mean that route backs up everything.
        assertEquals(setOf("cloud-backup", "device-transfer"), modern.children().map { it.tagName }.toSet())
        val cloud = modern.children().single { it.tagName == "cloud-backup" }
        assertFalse(
            "disableIfNoEncryptionCapabilities stays unset (false)",
            cloud.getAttribute("disableIfNoEncryptionCapabilities") == "true"
        )
    }

    @Test
    fun nothingIsIncludedExplicitly() {
        // One <include> would make Android back up only what is included.
        routes.forEach { (route, rules) ->
            assertTrue("$route has an <include>", rules.none { it.kind == "include" })
        }
    }

    @Test
    fun theFailSafesCopiesAndPhotosAreExcludedFromEveryRoute() {
        routes.forEach { (route, rules) ->
            mustBeExcluded.forEach { (domain, path) ->
                assertTrue("$route backs up $domain/$path", rules.any { excludes(it, domain, path) })
            }
        }
    }

    @Test
    fun theDatabaseItsSettingsAndTheMarkerAreBackedUpByEveryRoute() {
        routes.forEach { (route, rules) ->
            mustBeBackedUp.forEach { (domain, path) ->
                val by = rules.filter { excludes(it, domain, path) }
                assertTrue("$route leaves out $domain/$path, by $by", by.isEmpty())
            }
        }
    }

    @Test
    fun everyRuleUsesAKnownDomainAndALiteralPath() {
        val domains = setOf("root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref")
        routes.forEach { (route, rules) ->
            rules.forEach {
                assertTrue("$route: unknown element ${it.kind}", it.kind == "exclude" || it.kind == "include")
                assertTrue("$route: unknown domain ${it.domain}", it.domain in domains)
                assertTrue("$route: ${it.path} is not a literal path", it.path.isNotEmpty() && '*' !in it.path && ".." !in it.path)
            }
        }
    }
}

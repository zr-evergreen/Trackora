package com.evergreen.trackora.data.local.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Keeps [KnownSchemas], which the recheck accepts a database by, in step with
 * the schemas Room exported for every version (`core/data/schemas`, packaged
 * as test assets). A new database version, table, column, index or foreign
 * key fails this until the recheck knows it: otherwise a sound database of
 * that version would stay refused, or the recheck would ignore what changed.
 */
@RunWith(AndroidJUnit4::class)
class ExpectedSchemaTest {

    private val folder = TrackoraDatabase::class.java.name
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun exported(version: Int): JSONObject =
        JSONObject(assets.open("$folder/$version.json").bufferedReader().use { it.readText() }).getJSONObject("database")

    @Test
    fun everyExportedVersionIsKnownAndNoOther() {
        val versions = assets.list(folder)!!.map { it.removeSuffix(".json").toInt() }.toSet()
        assertEquals(versions, KnownSchemas.byVersion.keys)
        assertEquals(TrackoraDatabase.VERSION, versions.max())
    }

    @Test
    fun eachKnownSchemaIsExactlyTheExportedOne() {
        KnownSchemas.byVersion.forEach { (version, known) ->
            val database = exported(version)
            assertEquals(version, database.getInt("version"))
            assertEquals("Version $version", database.getString("identityHash"), known.identityHash)
            assertEquals("Version $version has views", 0, database.optJSONArray("views")?.length() ?: 0)

            val entities = database.getJSONArray("entities")
            assertEquals("Version $version: the recheck reads work_entries only", 1, entities.length())
            val table = entities.getJSONObject(0)
            assertEquals("work_entries", table.getString("tableName"))
            assertEquals("Version $version has indices", 0, table.optJSONArray("indices")?.length() ?: 0)
            assertEquals("Version $version has foreign keys", 0, table.optJSONArray("foreignKeys")?.length() ?: 0)

            val primaryKey = table.getJSONObject("primaryKey").getJSONArray("columnNames")
                .let { names -> List(names.length()) { names.getString(it) } }
            val fields = table.getJSONArray("fields")
            val columns = List(fields.length()) { i ->
                val field = fields.getJSONObject(i)
                val column = field.getString("columnName")
                Column(column, field.getString("affinity"), field.getBoolean("notNull"), primaryKey.indexOf(column) + 1)
            }
            assertEquals("Version $version", columns, known.columns)
        }
    }
}

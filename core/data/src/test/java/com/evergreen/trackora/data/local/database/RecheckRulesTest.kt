package com.evergreen.trackora.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every condition [RecheckRules] lifts a refusal on, and what it keeps one for. */
class RecheckRulesTest {

    private val v1 = KnownSchemas.byVersion.getValue(1)
    private val v2 = KnownSchemas.byVersion.getValue(2)

    private fun facts(
        integrity: List<String> = listOf("ok"),
        schema: Schema = v2,
        userVersion: Int = schema.version,
        identityHash: String? = schema.identityHash,
        columns: List<Column> = schema.columns,
        indexCount: Int = 0,
        foreignKeyCount: Int = 0,
        otherObjects: List<String> = emptyList(),
    ) = CopyFacts(integrity, userVersion, identityHash, columns, indexCount, foreignKeyCount, otherObjects)

    @Test
    fun soundVersion2AndVersion1DatabasesPass() {
        assertEquals(Verdict.Sound(v2), RecheckRules.decide(facts(schema = v2)))
        assertEquals(Verdict.Sound(v1), RecheckRules.decide(facts(schema = v1)))
    }

    @Test
    fun onlyASingleRowSayingExactlyOkPasses() {
        listOf(
            emptyList(),
            listOf("ok", "ok"),
            listOf("OK"),
            listOf("ok "),
            listOf(" ok"),
            listOf("ok\n"),
            listOf("*** in database main ***", "Page 3 is never used"),
            listOf("row 7 missing from index sqlite_autoindex_x_1"),
        ).forEach { rows ->
            val verdict = RecheckRules.decide(facts(integrity = rows))
            assertTrue("$rows passed", verdict is Verdict.Damaged)
        }
    }

    @Test
    fun aVersionThisAppNeverShippedIsKept() {
        listOf(0, 3, -1, 99).forEach { version ->
            val verdict = RecheckRules.decide(facts(userVersion = version))
            assertTrue("user_version $version passed", verdict is Verdict.UnknownSchema)
        }
    }

    @Test
    fun theIdentityHashMustBeTheOneForItsVersion() {
        listOf(null, "", "0".repeat(32), v1.identityHash).forEach { hash ->
            val verdict = RecheckRules.decide(facts(schema = v2, identityHash = hash))
            assertTrue("identity $hash passed as version 2", verdict is Verdict.UnknownSchema)
        }
        // Version 2's hash on a database that says it is version 1.
        assertTrue(RecheckRules.decide(facts(schema = v1, identityHash = v2.identityHash)) is Verdict.UnknownSchema)
    }

    @Test
    fun theColumnsMustBeExactlyThoseOfTheVersion() {
        val variants = listOf(
            emptyList(),                                                   // no work_entries table
            v2.columns.dropLast(1),                                        // version 2 without photoUri
            v2.columns + Column("extra", "TEXT", false, 0),
            v2.columns.map { if (it.name == "title") it.copy(notNull = false) else it },
            v2.columns.map { if (it.name == "quantity") it.copy(type = "TEXT") else it },
            v2.columns.map { if (it.name == "id") it.copy(primaryKey = 0) else it },
            v2.columns.reversed(),
        )
        variants.forEach { columns ->
            val verdict = RecheckRules.decide(facts(columns = columns))
            assertTrue("$columns passed", verdict is Verdict.UnknownSchema)
        }
        // Version 1's columns under version 2's version and hash.
        assertTrue(RecheckRules.decide(facts(schema = v2, columns = v1.columns)) is Verdict.UnknownSchema)
    }

    @Test
    fun indicesOrForeignKeysNoVersionCreatedAreKept() {
        assertTrue(RecheckRules.decide(facts(indexCount = 1)) is Verdict.UnknownSchema)
        assertTrue(RecheckRules.decide(facts(foreignKeyCount = 1)) is Verdict.UnknownSchema)
    }

    @Test
    fun anyOtherTableViewTriggerOrIndexIsKept() {
        val verdict = RecheckRules.decide(facts(otherObjects = listOf("trigger wipe_entries")))
        assertTrue(verdict is Verdict.UnknownSchema)
    }

    @Test
    fun onlyTheAppsAndroidsAndSqlitesOwnObjectsAreExpected() {
        listOf(
            "table" to "work_entries", "table" to "room_master_table", "table" to "android_metadata",
            "table" to "sqlite_sequence", "table" to "sqlite_stat1", "index" to "sqlite_autoindex_room_master_table_1",
        ).forEach { (type, name) -> assertTrue("$type $name", RecheckRules.isExpectedObject(type, name)) }
        listOf(
            "table" to "notes", "view" to "work_entries_view", "trigger" to "wipe_entries", "index" to "by_date",
            "view" to "sqlite_like", "trigger" to "sqlite_like", "view" to "work_entries",
        ).forEach { (type, name) -> assertTrue("$type $name", !RecheckRules.isExpectedObject(type, name)) }
    }

    @Test
    fun damageIsReportedBeforeAnythingAboutTheSchema() {
        val verdict = RecheckRules.decide(facts(integrity = listOf("bad"), userVersion = 7, identityHash = null))
        assertEquals(Verdict.Damaged(listOf("bad")), verdict)
    }
}

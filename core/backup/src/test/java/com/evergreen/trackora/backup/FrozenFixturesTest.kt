package com.evergreen.trackora.backup

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The committed example backups, read on the JVM. FrozenFixturesDeviceTest
 * runs the same checks on Android.
 */
class FrozenFixturesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun load(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/v1/$name")) { "fixture $name is missing" }.use { it.readBytes() }

    private val checks by lazy { FixtureChecks(temp.newFolder(), ::load) }

    @Test
    fun everyFixtureReadsAsListed() = checks.everyFixtureReadsAsListed()

    @Test
    fun validFixtureHoldsExactlyTheFixtureData() = checks.validFixtureHoldsExactlyTheFixtureData()

    @Test
    fun emptyFixtureHoldsNothing() = checks.emptyFixtureHoldsNothing()

    @Test
    fun writerSpellsTheFixtureIdentically() = checks.writerSpellsTheFixtureIdentically()

    @Test
    fun theFixturesFolderHoldsExactlyTheListedFiles() {
        val folder = File(checkNotNull(javaClass.getResource("/v1/valid.zip")).toURI()).parentFile!!
        assertEquals(Fixtures.CASES.map { it.file }.sorted(), folder.list()!!.sorted())
    }

    /**
     * Writes the fixtures, only when TRACKORA_WRITE_FIXTURES names a folder.
     * It was run once to make `fixtures/v1`; the files are frozen and must not
     * be regenerated, or they would stop recording what format 1 was.
     */
    @Test
    fun writeFixtures() {
        val target = System.getenv("TRACKORA_WRITE_FIXTURES")
        assumeTrue("set TRACKORA_WRITE_FIXTURES to write the fixtures", target != null)
        val folder = File(target!!).apply { mkdirs() }
        Fixtures.make().forEach { (name, bytes) -> File(folder, name).writeBytes(bytes) }
    }
}

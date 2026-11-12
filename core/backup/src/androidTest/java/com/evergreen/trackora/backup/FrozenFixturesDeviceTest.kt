package com.evergreen.trackora.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The frozen example backups read on Android: its zlib, CRC-32, SHA-256 and
 * desugared java.time (on API 24-25), where FrozenFixturesTest uses the JVM's.
 * The test APK is its own package; nothing of the app is touched.
 */
@RunWith(AndroidJUnit4::class)
class FrozenFixturesDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scratch = File(instrumentation.targetContext.cacheDir, "frozen-fixtures").apply { deleteRecursively(); mkdirs() }
    private val checks = FixtureChecks(scratch) { name -> instrumentation.context.assets.open("v1/$name").use { it.readBytes() } }

    @After
    fun cleanUp() {
        scratch.deleteRecursively()
    }

    @Test
    fun everyFixtureReadsAsListed() = checks.everyFixtureReadsAsListed()

    @Test
    fun validFixtureHoldsExactlyTheFixtureData() = checks.validFixtureHoldsExactlyTheFixtureData()

    @Test
    fun emptyFixtureHoldsNothing() = checks.emptyFixtureHoldsNothing()

    /** Android's ZipOutputStream and zlib produce the same format-1 JSON, byte for byte. */
    @Test
    fun writerSpellsTheFixtureIdentically() = checks.writerSpellsTheFixtureIdentically()
}

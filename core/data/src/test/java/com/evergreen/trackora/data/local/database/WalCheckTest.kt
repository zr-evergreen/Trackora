package com.evergreen.trackora.data.local.database

import com.evergreen.trackora.data.local.database.WalCheck.Result
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [WalCheck] on WAL files, read only.
 *
 * The first group starts from a WAL written by SQLite itself (the laptop's
 * SQLite 3.53, page size 512): a table created, then three entries, each in
 * its own transaction, copied while the connection was still open, as a
 * crash leaves it. SQLite applies all five frames and sees the three entries.
 * The second uses another WAL SQLite wrote, with the padding it adds after a
 * commit (resources/wal/padded.wal, see [paddingSqliteWritesAfterACommitIsRead]).
 * The third writes WAL files from the format SQLite documents, for the cases
 * a real file cannot easily be made to show. WalCheckRealSqliteTest checks the
 * same cases against Android's own SQLite on a device.
 */
class WalCheckTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun check(bytes: ByteArray, pageSize: Int = 512): Result =
        WalCheck.check(File(folder.root, "db-wal").apply { writeBytes(bytes) }, pageSize)

    private fun assertUnverified(result: Result) = assertTrue("Expected Unverified, was $result", result is Result.Unverified)

    // ---- A WAL SQLite wrote --------------------------------------------------

    private val frameSize = 24 + 512

    /** Frame [n]'s offset in the file (1-based), plus [at]. */
    private fun offset(n: Int, at: Int = 0) = 32 + (n - 1) * frameSize + at

    private fun real(): ByteArray = Base64.getDecoder().decode(REAL_WAL)

    private fun ByteArray.flip(at: Int) = copyOf().also { it[at] = (it[at].toInt() xor 0x5A).toByte() }

    @Test
    fun aWalSqliteWroteIsReadInFull() {
        assertEquals(Result.Verified(frames = 5, validFrames = 5, committedFrames = 5, leftoverFrames = 0, partialBytes = 0), check(real()))
    }

    @Test
    fun aDamagedPageByteDropsEveryLaterCommitAndIsRefused() {
        // Frame 3, the first entry: SQLite would read only the table's creation.
        assertUnverified(check(real().flip(offset(3, 24 + 100))))
    }

    @Test
    fun aDamagedFrameAfterValidCommitsIsRefused() {
        // Frame 4 holds the second entry; frames 2 and 3 committed before it are fine.
        assertUnverified(check(real().flip(offset(4, 24 + 7))))
    }

    @Test
    fun aDamagedSaltIsRefused() {
        assertUnverified(check(real().flip(offset(3, 8))))
        assertUnverified(check(real().flip(offset(5, 15))))
    }

    @Test
    fun aDamagedChecksumFieldIsRefused() {
        assertUnverified(check(real().flip(offset(4, 17))))
    }

    @Test
    fun theLastCommitDamagedIsRefused() {
        assertUnverified(check(real().flip(offset(5, 24 + 300))))
        // Its header zeroed: no longer recognisable as a commit at all.
        assertUnverified(check(real().also { it.fill(0, offset(5), offset(5, 24)) }))
    }

    /**
     * Frames 1 and 2 are one transaction, the table's creation, and frame 2 is
     * its commit. Cut after frame 1, as a kill between the two frame writes
     * leaves it, or a cut that took the commit frame: SQLite would apply
     * nothing of it, and nothing tells which it was.
     */
    @Test
    fun aCommitCutBeforeItsCommitFrameIsRefused() {
        assertUnverified(check(real().copyOf(offset(2))))
    }

    /** Frames [frames] of [wal] with only their salts changed, all to the same new values; checksums and pages kept. */
    private fun saltsChanged(wal: ByteArray, frames: IntRange) = wal.copyOf().also { b ->
        frames.forEach { n -> byteArrayOf(0x11, 0x11, 0x11, 0x11, 0x22, 0x22, 0x22, 0x22).copyInto(b, offset(n, 8)) }
    }

    /**
     * Only the salts of the last frames changed: SQLite stops before them and
     * drops their commits. They still chain with each other, as leftovers do,
     * but the first continues this WAL's own chain, which a leftover never
     * does: refused.
     */
    @Test
    fun saltsChangedOnTheLastFramesAreRefused() {
        listOf(4..5, 3..5).forEach { frames ->
            val result = check(saltsChanged(real(), frames))
            assertUnverified(result)
            assertTrue(result.toString(), (result as Result.Unverified).reason.contains("continues this WAL's checksum chain"))
        }
    }

    @Test
    fun aSaltChangedOnlyOnTheLastFrameIsRefused() {
        assertUnverified(check(saltsChanged(real(), 5..5)))
    }

    /**
     * The limit of the check: if the first of those frames is damaged in its
     * page too, it no longer continues this WAL's chain, and nothing in the
     * checksums links the frames to this WAL. Kept as a test so the limit
     * stays visible.
     */
    @Test
    fun saltsChangedWithTheFirstPageDamagedTooAreNotCaught() {
        val wal = saltsChanged(real(), 4..5).flip(offset(4, 24 + 100))
        assertEquals(Result.Verified(frames = 5, validFrames = 3, committedFrames = 3, leftoverFrames = 2, partialBytes = 0), check(wal))
    }

    @Test
    fun aWalCutPartwayThroughAFrameIsRefused() {
        assertUnverified(check(real().copyOf(offset(5, 200))))
        assertUnverified(check(real().copyOf(offset(4, 10))))
    }

    /**
     * The limit no check of the file can pass: cut exactly after a commit
     * frame, a WAL looks like a shorter one. SQLite then sees two entries, not
     * three, and so does this.
     */
    @Test
    fun aWalCutAtAFrameBoundaryLooksLikeAShorterOne() {
        assertEquals(Result.Verified(4, 4, 4, 0, 0), check(real().copyOf(offset(5))))
    }

    @Test
    fun aHeaderSqliteWouldRejectIsRefused() {
        assertUnverified(check(real().flip(12))) // checkpoint sequence, under the header checksum
        assertUnverified(check(real().flip(0))) // magic
        assertUnverified(check(real().flip(6))) // version
        assertUnverified(check(real(), pageSize = 4096)) // not the database's page size
        assertUnverified(check(real().copyOf(20)))
    }

    @Test
    fun noWalOrAnEmptyOneHoldsNothing() {
        assertEquals(Result.NoWal, WalCheck.check(File(folder.root, "missing-wal"), 512))
        assertEquals(Result.NoWal, check(ByteArray(0)))
    }

    // ---- WAL files from the documented format -----------------------------------

    @Test
    fun theWriterHereAgreesWithSqlite() {
        // Same layout as the real one, same verdict: the writer is a fair stand-in.
        val wal = Writer(salt1 = 1, salt2 = 2).frame(1).frame(2, commit = 2).frame(2, commit = 2).bytes()
        assertEquals(Result.Verified(3, 3, 3, 0, 0), check(wal))
    }

    @Test
    fun severalCommitsAreRead() {
        val wal = Writer(salt1 = 7, salt2 = 9).frame(1).frame(2, commit = 3).frame(3, commit = 3).frame(2, commit = 3).bytes()
        assertEquals(Result.Verified(frames = 4, validFrames = 4, committedFrames = 4, leftoverFrames = 0, partialBytes = 0), check(wal))
    }

    @Test
    fun anUnfinishedTransactionAfterTheCommitsIsRefused() {
        // Valid frames, but the last transaction has no commit frame: SQLite
        // applies only the first four, and nothing proves the other two were
        // never committed.
        val wal = Writer(salt1 = 7, salt2 = 9)
            .frame(1).frame(2, commit = 3).frame(3, commit = 3).frame(2, commit = 3)
            .frame(3).frame(4)
            .bytes()
        assertUnverified(check(wal))
    }

    @Test
    fun anEmptyWalHeaderIsRead() {
        assertEquals(Result.Verified(0, 0, 0, 0, 0), check(Writer(salt1 = 5, salt2 = 6).bytes()))
    }

    @Test
    fun bigEndianChecksumsAreRead() {
        val wal = Writer(salt1 = 1, salt2 = 2, bigEndian = true).frame(1).frame(2, commit = 2).bytes()
        assertEquals(Result.Verified(2, 2, 2, 0, 0), check(wal))
        // The same frames under the little-endian magic do not check out.
        assertUnverified(check(wal.copyOf().also { it[3] = 0x82.toByte() }))
    }

    @Test
    fun aPageNumberOfZeroEndsWhatSqliteReads() {
        val wal = Writer(salt1 = 1, salt2 = 2).frame(1, commit = 1).frame(0, commit = 1).frame(1, commit = 1).bytes()
        assertUnverified(check(wal))
    }

    /** After a reset, frames 4 to 10 are what is left of the WAL before it, already in the database. */
    private fun afterAReset(oldFrames: Int, newFrames: Int): ByteArray {
        val old = Writer(salt1 = 100, salt2 = 0x1234).apply { repeat(oldFrames) { frame(it % 5 + 1, commit = if (it % 2 == 1) 5 else 0) } }.bytes()
        val new = Writer(salt1 = 101, salt2 = 0x7777).apply { repeat(newFrames) { frame(it % 5 + 1, commit = 5) } }.bytes()
        return new + old.copyOfRange(new.size, old.size)
    }

    @Test
    fun leftoversFromBeforeAResetAreProvenAndIgnored() {
        assertEquals(Result.Verified(frames = 10, validFrames = 3, committedFrames = 3, leftoverFrames = 7, partialBytes = 0), check(afterAReset(10, 3)))
    }

    @Test
    fun aNewHeaderBeforeItsFirstFrameLeavesOnlyLeftovers() {
        // A reset wrote the new header, and the process stopped before the
        // first new frame: every frame is a proven leftover, nothing is lost.
        val old = Writer(salt1 = 100, salt2 = 0x1234).apply { repeat(10) { frame(it % 5 + 1, commit = if (it % 2 == 1) 5 else 0) } }.bytes()
        val wal = Writer(salt1 = 101, salt2 = 0x7777).bytes() + old.copyOfRange(32, old.size)
        assertEquals(Result.Verified(frames = 10, validFrames = 0, committedFrames = 0, leftoverFrames = 10, partialBytes = 0), check(wal))
    }

    @Test
    fun anUnfinishedTransactionBeforeLeftoversIsRefused() {
        val old = Writer(salt1 = 100, salt2 = 0x1234).apply { repeat(10) { frame(it % 5 + 1, commit = if (it % 2 == 1) 5 else 0) } }.bytes()
        val new = Writer(salt1 = 101, salt2 = 0x7777).frame(1, commit = 5).frame(2, commit = 5).frame(3).bytes()
        assertUnverified(check(new + old.copyOfRange(new.size, old.size)))
    }

    @Test
    fun saltsChangedOnNewFramesBeforeGenuineLeftoversAreRefused() {
        // After a reset: the last two new frames take new salts; the genuine
        // leftovers behind them do not make them leftovers.
        val wal = afterAReset(10, 3)
        assertEquals(Result.Verified(10, 3, 3, 7, 0), check(wal))
        assertUnverified(check(saltsChanged(wal, 2..3)))
    }

    @Test
    fun aSingleLeftoverCannotBeProvenAndIsRefused() {
        assertUnverified(check(afterAReset(4, 3)))
    }

    @Test
    fun aLeftoverWithTheCurrentSaltIsRefused() {
        // A transaction rolled back after spilling frames 4 to 6; frame 4 was
        // then overwritten by a commit. Sound, but nothing proves frames 5
        // and 6 never committed.
        val rolledBack = Writer(salt1 = 5, salt2 = 6).frame(1).frame(2, commit = 2).frame(2, commit = 2).frame(3).frame(4).frame(5).bytes()
        val committed = Writer(salt1 = 5, salt2 = 6).frame(1).frame(2, commit = 2).frame(2, commit = 2).frame(2, commit = 2).bytes()
        assertUnverified(check(committed + rolledBack.copyOfRange(committed.size, rolledBack.size)))
    }

    @Test
    fun aLeftoverWithOneCurrentSaltIsRefused() {
        val wal = afterAReset(10, 3)
        // Frame 6's salt-1 made the current one: not a leftover any more.
        val salt1 = wal.copyOfRange(16, 20)
        salt1.copyInto(wal, offset(6, 8))
        assertUnverified(check(wal))
    }

    @Test
    fun aLeftoverWhoseChecksumDoesNotChainIsRefused() {
        val wal = afterAReset(10, 3)
        // Frame 10 is the last one: only frame 9 can prove it.
        assertUnverified(check(wal.flip(offset(10, 24 + 50))))
    }

    @Test
    fun aFileCutThroughALeftoverIsRead() {
        // As SQLite's journal_size_limit leaves it: the last leftover cut short.
        val wal = afterAReset(10, 3)
        assertEquals(Result.Verified(frames = 9, validFrames = 3, committedFrames = 3, leftoverFrames = 6, partialBytes = 100), check(wal.copyOf(offset(10, 100))))
        // Too little of it to see its salts.
        assertUnverified(check(wal.copyOf(offset(10, 12))))
    }

    @Test
    fun aFileCutRightAfterTheFramesSqliteReadsIsRefused() {
        val wal = Writer(salt1 = 1, salt2 = 2).frame(1).frame(2, commit = 2).bytes()
        val torn = Writer(salt1 = 1, salt2 = 2).frame(1).frame(2, commit = 2).frame(2, commit = 2).bytes().copyOf(wal.size + 100)
        assertUnverified(check(torn))
    }

    // ---- Padding SQLite writes after a commit --------------------------------

    /**
     * Written by the laptop's SQLite 3.53 with powersafe overwrite off
     * (`psow=0`) and `synchronous=FULL`, page size 512: a table created, then
     * one entry. Each transaction is a frame of page 1, its commit frame, and
     * six padding frames that repeat the commit frame, commit size included,
     * up to the 4096-byte sector: 16 frames. SQLite applies all of them and
     * sees the entry. Android's SQLite keeps powersafe overwrite on and writes
     * no padding (checked on API 24 to 36), but other builds might.
     */
    private fun padded(): ByteArray = javaClass.getResourceAsStream("/wal/padded.wal")!!.use { it.readBytes() }

    @Test
    fun paddingSqliteWritesAfterACommitIsRead() {
        val wal = padded()
        assertEquals(Result.Verified(frames = 16, validFrames = 16, committedFrames = 16, leftoverFrames = 0, partialBytes = 0), check(wal))
        // Cut anywhere inside a commit's padding: still ends on a commit.
        (2..8).forEach { frames -> assertEquals(Result.Verified(frames, frames, frames, 0, 0), check(wal.copyOf(offset(frames + 1)))) }
        (10..16).forEach { frames -> assertEquals(Result.Verified(frames, frames, frames, 0, 0), check(wal.copyOf(offset(frames + 1)))) }
        // Cut after a transaction's first frame, before its commit frame: refused.
        assertUnverified(check(wal.copyOf(offset(2))))
        assertUnverified(check(wal.copyOf(offset(10))))
    }

    /** A WAL as the documented format describes it: header, then frames whose checksums chain from it. */
    private class Writer(private val salt1: Int, private val salt2: Int, private val bigEndian: Boolean = false, private val pageSize: Int = 512) {
        private val out = ByteArrayOutputStream()
        private var s0 = 0
        private var s1 = 0

        init {
            val header = ByteArray(32)
            put(header, 0, if (bigEndian) 0x377f0683 else 0x377f0682)
            put(header, 4, 3007000)
            put(header, 8, pageSize)
            put(header, 16, salt1)
            put(header, 20, salt2)
            sum(header, 0, 24)
            put(header, 24, s0)
            put(header, 28, s1)
            out.write(header)
        }

        fun frame(page: Int, commit: Int = 0): Writer {
            val frame = ByteArray(24 + pageSize)
            put(frame, 0, page)
            put(frame, 4, commit)
            put(frame, 8, salt1)
            put(frame, 12, salt2)
            for (i in 24 until frame.size) frame[i] = (page * 31 + i * 7 + out.size()).toByte()
            sum(frame, 0, 8)
            sum(frame, 24, pageSize)
            put(frame, 16, s0)
            put(frame, 20, s1)
            out.write(frame)
            return this
        }

        fun bytes(): ByteArray = out.toByteArray()

        private fun sum(b: ByteArray, from: Int, length: Int) {
            for (i in from until from + length step 8) {
                s0 += word(b, i) + s1
                s1 += word(b, i + 4) + s0
            }
        }

        private fun word(b: ByteArray, i: Int): Int {
            val order = if (bigEndian) intArrayOf(0, 1, 2, 3) else intArrayOf(3, 2, 1, 0)
            return order.fold(0) { acc, k -> (acc shl 8) or (b[i + k].toInt() and 0xff) }
        }

        private fun put(b: ByteArray, at: Int, value: Int) {
            for (k in 0..3) b[at + k] = (value ushr (24 - 8 * k)).toByte()
        }
    }

    private companion object {
        /** The real WAL described in the class comment, base64. */
        const val REAL_WAL =
        "N38GggAt4hgAAAIAAAAAAFZviAiaOee23VNyaUwUg6gAAAABAAAAAFZviAiaOee25iW6rusdbpFTUUxpdGUgZm9ybWF0IDMA" +
            "AgACAgBAICAAAAACAAAAAgAAAAAAAAAAAAAAAQAAAAQAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAIALpXLDQAAAAEBwgABwgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAA8AQYXDw8BaXRhYmxldHQCQ1JFQVRFIFRBQkxFIHQoaWQgSU5URUdFUiBQUklNQVJZIEtFWSwgdiBURVhUKQAAAAIAAAAC" +
            "Vm+ICJo557Z7fXCsZumfHA0AAAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAAJWb4gImjnntpIOGtdJ2baXDQAAAAEB9AAB9AAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKAQMAG2VudHJ5IDAAAAACAAAAAlZviAiaOee2" +
            "SWwCGeYGxsENAAAAAgHoAAH0AegAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAoCAwAbZW50" +
            "cnkgMQoBAwAbZW50cnkgMAAAAAIAAAACVm+ICJo557b/hP1f1f4SMA0AAAADAdwAAfQB6AHcAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAACgMDABtlbnRyeSAyCgIDABtlbnRyeSAxCgEDABtlbnRyeSAw"
    }
}

package com.evergreen.trackora.data.local.database

import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile

/**
 * Checks a database's `-wal`, reading it only, for changes SQLite would leave
 * unread without a word. It follows the WAL format SQLite documents
 * (https://www.sqlite.org/fileformat2.html, "The Write-Ahead Log").
 *
 * SQLite reads a WAL's frames from the first and stops, with no error, at
 * the first frame that is not valid: one whose salts are not the header's,
 * whose page number is 0, or whose cumulative checksum does not match. Only
 * frames up to the last commit frame before that point count. A damaged frame
 * therefore drops every transaction after it, and `PRAGMA integrity_check`
 * on what is left still says "ok".
 *
 * Frames past that point are normal in one case only: leftovers from before a
 * reset. After a complete checkpoint SQLite writes the WAL again from its
 * start, with salt-1 incremented and salt-2 randomised, and the older frames
 * it has not yet overwritten stay in the file, already copied into the
 * database. So every complete frame past the valid ones must be proven to be
 * such a leftover: neither salt is the current one, and its checksum chains
 * with a neighbouring frame of the same older salts, which damage to either
 * would break. A file that ends partway through a frame is accepted only when
 * that frame continues a proven leftover; SQLite's `journal_size_limit` cuts
 * the file there. Anything else, and any header SQLite would reject, is
 * [Result.Unverified].
 *
 * What no check of the file can see: frames that are not in it at all,
 * because the file was cut exactly at a frame boundary, or because they never
 * reached storage (a power cut under `synchronous=NORMAL`). And some sound
 * files are refused: the frames of a transaction that was rolled back after
 * spilling into the WAL, or of one cut short while a frame was being written
 * or while one of its frames had been rewritten in place (SQLite 3.11 and
 * later, Android 8 on, fix such a frame's checksum only at commit), are
 * frames SQLite rightly ignores, but nothing in the file tells them apart
 * from a last commit whose header was destroyed.
 */
internal object WalCheck {

    sealed interface Result {
        /** There is no `-wal`, or it is empty. */
        data object NoWal : Result

        /**
         * Every frame SQLite will not read is a proven leftover from before a
         * reset. [committedFrames] is the index of the last commit frame SQLite
         * reads, 0 if none.
         */
        data class Verified(
            val frames: Int,
            val validFrames: Int,
            val committedFrames: Int,
            val leftoverFrames: Int,
            val partialBytes: Int,
        ) : Result

        /** The `-wal` holds data SQLite would not read that is not a proven leftover, or a header SQLite rejects. */
        data class Unverified(val reason: String) : Result
    }

    private const val HEADER = 32
    private const val FRAME_HEADER = 24
    private const val VERSION = 3007000

    /** Checks [wal] for a database whose pages are [databasePageSize] bytes. */
    fun check(wal: File, databasePageSize: Int): Result {
        if (!wal.exists() || wal.length() == 0L) return Result.NoWal
        return RandomAccessFile(wal, "r").use { file -> check(file, databasePageSize) }
    }

    private fun check(file: RandomAccessFile, databasePageSize: Int): Result {
        val length = file.length()
        if (length < HEADER) return Result.Unverified("The -wal is shorter than its header ($length bytes)")
        val header = ByteArray(HEADER).also { file.readFully(it) }
        val magic = int(header, 0)
        if (magic != 0x377f0682 && magic != 0x377f0683) return Result.Unverified("The -wal does not start with SQLite's WAL magic number")
        val bigEndian = magic and 1 == 1
        if (int(header, 4) != VERSION) return Result.Unverified("The -wal has format version ${int(header, 4)}, not $VERSION")
        val pageSize = int(header, 8)
        if (pageSize < 512 || pageSize > 65536 || pageSize and (pageSize - 1) != 0 || pageSize != databasePageSize) {
            return Result.Unverified("The -wal's page size, $pageSize, is not the database's, $databasePageSize")
        }
        val sum = IntArray(2)
        checksum(header, 0, 24, bigEndian, sum)
        if (sum[0] != int(header, 24) || sum[1] != int(header, 28)) {
            return Result.Unverified("The -wal's header checksum does not match: SQLite would ignore every frame in it")
        }
        val salt1 = int(header, 16)
        val salt2 = int(header, 20)

        val frameSize = FRAME_HEADER + pageSize
        val frames = ((length - HEADER) / frameSize).toInt()
        val partial = ((length - HEADER) % frameSize).toInt()
        val frame = ByteArray(frameSize)

        // The frames SQLite reads: each valid, chained from the header's checksum.
        var valid = 0
        var committed = 0
        while (valid < frames) {
            file.readFully(frame)
            val running = sum.copyOf()
            checksum(frame, 0, 8, bigEndian, running)
            checksum(frame, FRAME_HEADER, pageSize, bigEndian, running)
            val isValid = int(frame, 8) == salt1 && int(frame, 12) == salt2 && int(frame, 0) != 0 &&
                running[0] == int(frame, 16) && running[1] == int(frame, 20)
            if (!isValid) break
            running.copyInto(sum)
            valid++
            if (int(frame, 4) != 0) committed = valid
        }

        // Every complete frame after them must be a proven leftover. A frame is
        // proven by the next one chaining from it, or by its own chaining from
        // the one before; [frame] holds frame number [valid] + 1 here, if any.
        var previous: ByteArray? = null
        var previousProven = false
        for (number in valid + 1..frames) {
            val current = if (number == valid + 1) frame.copyOf() else ByteArray(frameSize).also { file.readFully(it) }
            if (!isLeftover(current, salt1, salt2)) {
                return Result.Unverified(
                    "Frame $number of the -wal, which SQLite would not read, is not a leftover from before a reset" +
                        if (int(current, 8) == salt1 || int(current, 12) == salt2) " (it has the current salt)" else ""
                )
            }
            val linked = previous != null && chains(previous, current, pageSize, bigEndian)
            if (previous != null && !previousProven && !linked) {
                return Result.Unverified("Frame ${number - 1} of the -wal looks like a leftover from before a reset, but its checksum chains with neither neighbour")
            }
            previous = current
            previousProven = linked
        }
        if (previous != null && !previousProven) {
            return Result.Unverified("Frame $frames of the -wal looks like a leftover from before a reset, but its checksum chains with neither neighbour")
        }

        // A file cut partway through a frame: only a continuation of a proven leftover.
        if (partial > 0) {
            val cut = previous
            if (partial < 16 || cut == null) {
                return Result.Unverified("The -wal ends partway through a frame that is not a leftover from before a reset")
            }
            val salts = ByteArray(16).also { file.readFully(it) }
            if (int(salts, 8) != int(cut, 8) || int(salts, 12) != int(cut, 12)) {
                return Result.Unverified("The -wal ends partway through a frame that is not a leftover from before a reset")
            }
        }
        return Result.Verified(frames, valid, committed, frames - valid, partial)
    }

    /** Neither salt is the header's, so the frame cannot be from the current WAL, and it names a page. */
    private fun isLeftover(frame: ByteArray, salt1: Int, salt2: Int) =
        int(frame, 0) != 0 && int(frame, 8) != salt1 && int(frame, 12) != salt2

    /** [next] carries [previous]'s salts and its checksum continues from [previous]'s stored one. */
    private fun chains(previous: ByteArray, next: ByteArray, pageSize: Int, bigEndian: Boolean): Boolean {
        if (int(previous, 8) != int(next, 8) || int(previous, 12) != int(next, 12)) return false
        val running = intArrayOf(int(previous, 16), int(previous, 20))
        checksum(next, 0, 8, bigEndian, running)
        checksum(next, FRAME_HEADER, pageSize, bigEndian, running)
        return running[0] == int(next, 16) && running[1] == int(next, 20)
    }

    /**
     * SQLite's WAL checksum over [length] bytes (a multiple of 8) of [bytes],
     * continuing from [sum]: 32-bit words in the byte order the magic number
     * names, s0 += x(i) + s1, s1 += x(i+1) + s0. Ints wrap as SQLite's u32 do.
     */
    private fun checksum(bytes: ByteArray, offset: Int, length: Int, bigEndian: Boolean, sum: IntArray) {
        var s0 = sum[0]
        var s1 = sum[1]
        var i = offset
        val end = offset + length
        while (i < end) {
            s0 += word(bytes, i, bigEndian) + s1
            s1 += word(bytes, i + 4, bigEndian) + s0
            i += 8
        }
        sum[0] = s0
        sum[1] = s1
    }

    private fun word(b: ByteArray, at: Int, bigEndian: Boolean): Int =
        if (bigEndian) int(b, at)
        else (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8) or
            ((b[at + 2].toInt() and 0xff) shl 16) or ((b[at + 3].toInt() and 0xff) shl 24)

    /** A big-endian 32-bit value, as every field of the WAL is stored. */
    private fun int(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xff) shl 24) or ((b[at + 1].toInt() and 0xff) shl 16) or
            ((b[at + 2].toInt() and 0xff) shl 8) or (b[at + 3].toInt() and 0xff)

    /** The page size in a database file's header (bytes 16-17, big-endian; 1 means 65536), or null if unreadable. */
    fun pageSizeOf(database: File): Int? = try {
        RandomAccessFile(database, "r").use { f ->
            f.seek(16)
            val size = (f.readUnsignedByte() shl 8) or f.readUnsignedByte()
            if (size == 1) 65536 else size
        }
    } catch (e: EOFException) {
        null
    }
}

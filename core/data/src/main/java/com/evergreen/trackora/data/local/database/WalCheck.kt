package com.evergreen.trackora.data.local.database

import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile

/**
 * Checks a database's `-wal`, reading it only, for frames SQLite would leave
 * unapplied without a word. It follows the WAL format SQLite documents
 * (https://www.sqlite.org/fileformat2.html, "The Write-Ahead Log").
 *
 * SQLite reads a WAL's frames from the first and stops, with no error, at
 * the first frame that is not valid: one whose salts are not the header's,
 * whose page number is 0, or whose cumulative checksum does not match. Of the
 * valid frames it applies only those up to the last commit frame. A damaged
 * frame therefore drops every transaction after it, and `PRAGMA
 * integrity_check` on what is left still says "ok".
 *
 * The check accepts a `-wal` only when every frame in it is either applied by
 * SQLite or a proven leftover from before a reset:
 *
 * - The valid frames must end on a commit frame. Frames after the last commit
 *   are a transaction SQLite never applies, and nothing in the file tells an
 *   interrupted or rolled-back one from a commit whose commit frame was cut
 *   off. The padding SQLite writes after a commit, when a commit syncs the
 *   WAL on storage without "powersafe overwrite", repeats the commit frame,
 *   commit size included, so it ends on a commit too. (Android's SQLite keeps
 *   powersafe overwrite on and writes no padding.)
 * - Every complete frame after the valid ones must be a leftover from before a
 *   reset. After a complete checkpoint SQLite writes the WAL again from its
 *   start, with salt-1 incremented and salt-2 randomised, and the older frames
 *   it has not yet overwritten stay in the file, already copied into the
 *   database. A leftover is proven when neither salt is the current one, its
 *   checksum chains with a neighbouring frame of the same older salts, and the
 *   first of them does not continue this WAL's own checksum chain. The
 *   checksum does not cover the salts, so frames of this WAL whose salts were
 *   changed still chain with each other; but they also continue this WAL's
 *   chain from its last valid frame, which an older WAL's frames, chained from
 *   a header that is gone, do not.
 * - A file that ends partway through a frame is accepted only when that frame
 *   continues a proven leftover; SQLite's `journal_size_limit` cuts the file
 *   there.
 * - The header must be one SQLite accepts, for the database's page size.
 *
 * Anything else is [Result.Unverified].
 *
 * What no check of the file can see: frames that are not in it at all,
 * because the file was cut exactly after a commit frame (between two
 * transactions, or within a commit's padding), or because they never reached
 * storage (a power cut under `synchronous=NORMAL`).
 *
 * What this check does not see: frames of this WAL whose salts were changed,
 * as above, when the first of them is damaged in its page or checksum too, so
 * that it no longer continues this WAL's chain. Nothing in the file's
 * checksums then links them to this WAL, and they pass as leftovers. It takes
 * the same new salts on two or more frames together with other damage to the
 * first; random damage practically never does that.
 *
 * And some sound files are refused, whose frames SQLite rightly leaves
 * unapplied but nothing in the file proves were never committed: the frames of
 * a transaction interrupted or rolled back after they reached the WAL (all
 * valid at its end, or overwritten in part by a later commit), of one cut
 * short while a frame was being written or after one of its frames was
 * rewritten in place (SQLite 3.11 and later, Android 8 on, fix such a frame's
 * checksum only at commit), a single leftover frame, and a partial frame right
 * after the valid ones.
 */
internal object WalCheck {

    sealed interface Result {
        /** There is no `-wal`, or it is empty. */
        data object NoWal : Result

        /**
         * SQLite applies every valid frame (the last is a commit frame), and
         * every other complete frame is a proven leftover from before a reset.
         * [validFrames] and [committedFrames] are therefore equal, 0 if there
         * is no frame of the current WAL.
         */
        data class Verified(
            val frames: Int,
            val validFrames: Int,
            val committedFrames: Int,
            val leftoverFrames: Int,
            val partialBytes: Int,
        ) : Result

        /** The `-wal` holds frames SQLite would not apply that are not proven leftovers, or a header SQLite rejects. */
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

        // The frames SQLite validates: each chained from the header's checksum.
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

        // They must end on a commit: frames after the last commit are a
        // transaction SQLite never applies, interrupted, rolled back, or a
        // commit whose commit frame was cut off, and nothing tells which.
        // SQLite's padding after a commit repeats the commit frame, commit
        // size included, so a padded commit ends on a commit too.
        if (committed != valid) {
            return Result.Unverified(
                "The last ${valid - committed} valid frame(s) of the -wal belong to a transaction with no commit frame: " +
                    "an interrupted or rolled-back one, or a commit whose commit frame was cut off"
            )
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
            // An older WAL's frames chain from a header that is gone. A frame
            // that continues this WAL's own chain from its last valid frame is
            // a frame of this WAL whose salts were changed.
            if (number == valid + 1) {
                val running = sum.copyOf()
                checksum(current, 0, 8, bigEndian, running)
                checksum(current, FRAME_HEADER, pageSize, bigEndian, running)
                if (running[0] == int(current, 16) && running[1] == int(current, 20)) {
                    return Result.Unverified(
                        "Frame $number of the -wal continues this WAL's checksum chain but has other salts: " +
                            "a frame of this WAL whose salts were changed, not a leftover from before a reset"
                    )
                }
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

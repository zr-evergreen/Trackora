package com.evergreen.trackora.backup

import java.io.IOException

/**
 * Why a backup was refused, or why the writer refused to make one. Each has
 * its own message on the restore screen, so they stay distinct.
 */
enum class BackupProblem {
    /** Not a ZIP file at all. */
    NOT_A_ZIP,

    /** A ZIP that is incomplete or damaged: cut short, a flipped byte, a broken structure. */
    DAMAGED,

    /** A ZIP using something the format excludes: ZIP64, encryption, comments, another compression method. */
    UNSUPPORTED_ZIP,

    /** A ZIP, but not a Trackora backup: no manifest, or someone else's. */
    NOT_A_TRACKORA_BACKUP,

    /** Made by a newer version of the app, in a format this one doesn't know. */
    NEWER_FORMAT,

    /** A file name that could reach outside the folder it is unpacked into. */
    UNSAFE_NAME,

    /** A file the format doesn't allow, or the manifest doesn't list. */
    UNEXPECTED_FILE,

    /** A file the manifest lists, or an entry uses, that isn't there. */
    MISSING_FILE,

    /** Two files with the same name, or names that differ only in capital letters. */
    DUPLICATE_FILE,

    /** Over one of the [BackupFormat] limits. */
    TOO_LARGE,

    /** A file whose size or SHA-256 differs from the manifest's. */
    HASH_MISMATCH,

    INVALID_MANIFEST,
    INVALID_SETTINGS,
    INVALID_ENTRY,

    /** The parts disagree: counts, photos no entry uses, the ID counter, file order. */
    INCONSISTENT,
}

/** A backup was refused, or couldn't be written, for [problem]. */
class BackupException(val problem: BackupProblem, message: String) : IOException(message)

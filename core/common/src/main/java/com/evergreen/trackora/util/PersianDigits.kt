package com.evergreen.trackora.util

/**
 * Conversion between Western (ASCII) digits and the Persian-Indic digits
 * Iranian users expect to read.
 *
 * ### Why this exists even though the JVM already parses Persian digits
 *
 * `Integer.parseInt("۱۲")` returns `12`, and `Character.isDigit('۱')` is
 * `true` — the JVM's digit handling is Unicode-aware, so Persian input already
 * reaches the database as a correct number today. That behaviour is easy to
 * mistake for "nothing to do here".
 *
 * Two reasons it is not enough:
 *
 * - **Display is the actual gap.** Nothing converts the other direction, so a
 *   Persian user sees `1234` everywhere. That is the single most recognisable
 *   tell of an app that was translated rather than built for the locale.
 * - **Parsing correctness should not rest on an incidental property of
 *   `parseInt`.** [toWestern] makes the normalisation explicit at the storage
 *   boundary, so the invariant "the database holds ASCII numerics" is enforced
 *   by this code rather than inherited from a JDK implementation detail that
 *   nothing in our test suite pins down.
 *
 * Arabic-Indic digits (`٠١٢`, U+0660–U+0669) are accepted on input as well as
 * Persian-Indic (`۰۱۲`, U+06F0–U+06F9). They are visually near-identical for
 * several values, and Arabic keyboards are common enough on Iranian phones
 * that rejecting them would produce mystifying validation failures.
 */
object PersianDigits {

    private const val PERSIAN_ZERO = '۰'
    private const val ARABIC_ZERO = '٠'
    private const val WESTERN_ZERO = '0'

    /** U+066C ARABIC THOUSANDS SEPARATOR — the grouping mark used in Persian. */
    const val PERSIAN_THOUSANDS_SEPARATOR = '٬'

    /** The grouping mark used in English. */
    private const val WESTERN_THOUSANDS_SEPARATOR = ','

    /**
     * Rewrites every Western digit in [input] as its Persian-Indic equivalent,
     * leaving all other characters untouched.
     *
     * Non-digit characters are deliberately preserved so this is safe to apply
     * to whole formatted strings — `"1404/05/23"` becomes `"۱۴۰۴/۰۵/۲۳"` with
     * the separators intact.
     */
    fun toPersian(input: String): String {
        if (input.isEmpty()) return input
        return buildString(input.length) {
            for (char in input) {
                append(
                    if (char in '0'..'9') {
                        PERSIAN_ZERO + (char - WESTERN_ZERO)
                    } else {
                        char
                    }
                )
            }
        }
    }

    /**
     * Rewrites every Persian-Indic *and* Arabic-Indic digit in [input] as its
     * Western equivalent, leaving all other characters untouched.
     *
     * Apply this before parsing or storing anything the user typed.
     */
    fun toWestern(input: String): String {
        if (input.isEmpty()) return input
        return buildString(input.length) {
            for (char in input) {
                append(
                    when (char) {
                        in '۰'..'۹' -> WESTERN_ZERO + (char - PERSIAN_ZERO)
                        in '٠'..'٩' -> WESTERN_ZERO + (char - ARABIC_ZERO)
                        else -> char
                    }
                )
            }
        }
    }

    /**
     * Formats [value] for display, grouping thousands and using the digit set
     * that matches the locale.
     *
     * Persian uses `٬` (U+066C) as the grouping mark, not the Latin comma:
     * `۱٬۲۳۴`. Using a comma there looks as wrong to a Persian reader as
     * `1.234` does to an English one.
     */
    fun format(value: Long, usePersianDigits: Boolean): String {
        val grouped = groupThousands(
            value = value,
            separator = if (usePersianDigits) {
                PERSIAN_THOUSANDS_SEPARATOR
            } else {
                WESTERN_THOUSANDS_SEPARATOR
            }
        )
        return if (usePersianDigits) toPersian(grouped) else grouped
    }

    /** Convenience overload for the `Int` counters used across the app. */
    fun format(value: Int, usePersianDigits: Boolean): String =
        format(value.toLong(), usePersianDigits)

    /**
     * Renders a number that should carry no grouping at all — years, day and
     * month numbers, version components. `۱۴۰۴` is a year; `۱٬۴۰۴` is a
     * quantity, and grouping it would read as a mistake.
     */
    fun formatUngrouped(value: Int, usePersianDigits: Boolean): String {
        val plain = value.toString()
        return if (usePersianDigits) toPersian(plain) else plain
    }

    private fun groupThousands(value: Long, separator: Char): String {
        // Split the sign off the rendered string rather than negating the
        // number: negating Long.MIN_VALUE overflows back to itself, and
        // toString() has already handled that case correctly.
        val rendered = value.toString()
        val negative = rendered.startsWith('-')
        val digits = if (negative) rendered.substring(1) else rendered

        val grouped = buildString(digits.length + digits.length / 3) {
            for ((index, char) in digits.withIndex()) {
                if (index > 0 && (digits.length - index) % 3 == 0) {
                    append(separator)
                }
                append(char)
            }
        }

        return if (negative) "-$grouped" else grouped
    }
}

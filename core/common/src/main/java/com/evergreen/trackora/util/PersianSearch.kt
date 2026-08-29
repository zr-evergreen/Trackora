package com.evergreen.trackora.util

/**
 * Text folding for search, written for Persian rather than adapted to it.
 *
 * A plain `contains()` fails constantly for Persian users, and always in ways
 * that look like the app is broken rather than like a spelling difference:
 *
 * **Arabic versus Persian letterforms.** Many keyboards — and most text pasted
 * from the web — use Arabic `ك` and `ي` where Persian uses `ک` and `ی`. The two
 * pairs look nearly identical on screen and are different code points, so a
 * user searching for «کار» finds nothing in an entry they typed as «كار».
 *
 * **The zero-width non-joiner.** «تکمیل‌شده» and «تکمیل شده» are the same word
 * to a reader; one has U+200C, the other a space. Whichever the user typed,
 * the other should still match.
 *
 * **Digits.** A quantity stored as `12` should be found by someone typing `۱۲`,
 * because that is what their keyboard produces.
 *
 * Folding both sides of the comparison through [normalize] makes all three
 * work. It is deliberately not a full Unicode collation: this is a local search
 * over one person's own work log, and the cost of being wrong is a missing row,
 * not a corrupted record.
 */
object PersianSearch {

    private const val ZWNJ = '‌'

    /** Arabic forms mapped to their Persian equivalents. */
    private val LETTER_FOLDING = mapOf(
        'ي' to 'ی', // ARABIC YEH -> FARSI YEH
        'ى' to 'ی', // ALEF MAKSURA -> FARSI YEH
        'ك' to 'ک', // ARABIC KAF -> KEHEH
        'ة' to 'ه', // TEH MARBUTA -> HEH
        'أ' to 'ا', // ALEF WITH HAMZA ABOVE -> ALEF
        'إ' to 'ا', // ALEF WITH HAMZA BELOW -> ALEF
        'آ' to 'ا', // ALEF WITH MADDA -> ALEF
        'ؤ' to 'و', // WAW WITH HAMZA -> WAW
        'ئ' to 'ی', // YEH WITH HAMZA -> FARSI YEH
    )

    /**
     * Harakat and tatweel. These are decorative in running text and are almost
     * never typed into a search box, so an entry that happens to contain one
     * must not become unfindable.
     */
    private val STRIPPED = setOf(
        ZWNJ,
        'ـ', // TATWEEL
        'ً', 'ٌ', 'ٍ', 'َ', 'ُ',
        'ِ', 'ّ', 'ْ', 'ٓ', 'ٔ', 'ٕ',
    )

    /**
     * Folds [text] into a form suitable for comparison.
     *
     * Digits become Western via [PersianDigits] so a query typed on a Persian
     * keyboard matches a quantity stored as a number. Case is folded for the
     * Latin half of a mixed-script log.
     */
    fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        for (char in text) {
            if (char in STRIPPED) continue
            builder.append(LETTER_FOLDING[char] ?: char)
        }
        return PersianDigits.toWestern(builder.toString()).lowercase().trim()
    }

    /**
     * Whether [haystack] contains [needle], with both sides folded.
     *
     * A blank query matches everything, so callers can pass the raw field
     * without special-casing an empty search box.
     */
    fun matches(haystack: String?, needle: String): Boolean {
        if (needle.isBlank()) return true
        if (haystack.isNullOrEmpty()) return false
        return normalize(haystack).contains(normalize(needle))
    }
}

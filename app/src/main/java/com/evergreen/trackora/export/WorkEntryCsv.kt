package com.evergreen.trackora.export

import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.settings.CustomFields
import java.time.format.DateTimeFormatter

/**
 * Renders work entries as CSV.
 *
 * Pure and Android-free so the escaping rules can be tested directly — the
 * parts of an exporter that break are the quoting and the encoding, and
 * neither needs a device to exercise.
 *
 * ### Why the byte order mark
 *
 * Excel on Windows does not detect UTF-8 in a `.csv` unless the file opens with
 * a BOM; without one it decodes as the system code page and every Persian
 * character becomes mojibake. The users this export is for will open it in
 * Excel, so [BOM] is prepended. Tools that do not need it ignore it.
 *
 * ### Why the dates are ISO, not Jalali
 *
 * The export is a data interchange format, not a screen. A spreadsheet can sort
 * and filter `2026-08-21`; it cannot do anything useful with «۳۰ مرداد ۱۴۰۵».
 * Digits stay Western for the same reason — a Persian-Indic digit in a
 * spreadsheet cell is text, not a number, and silently breaks every SUM.
 */
object WorkEntryCsv {

    /** Byte order mark. See the class note — this is what makes Excel read UTF-8. */
    // Written as an escape, not a literal: a raw BOM sitting mid-source is
    // mishandled by enough tooling that lint rejects it outright.
    const val BOM: String = "\uFEFF"

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * @param customFields the user's own names for the three optional columns,
     *   so the header says "Customer" rather than "Custom field 1". Blank names
     *   fall back to a positional label; the column is still emitted, because a
     *   hidden field may still hold data from before it was hidden.
     */
    fun format(
        entries: List<WorkEntry>,
        customFields: CustomFields = CustomFields(),
        headers: Headers = Headers()
    ): String {
        val rows = StringBuilder()
        rows.append(BOM)

        rows.appendRow(
            listOf(
                headers.date,
                headers.title,
                headers.description,
                headers.quantity,
                headers.status,
                customFields.field1Name.ifBlank { headers.customField1 },
                customFields.field2Name.ifBlank { headers.customField2 },
                customFields.field3Name.ifBlank { headers.customField3 },
            )
        )

        entries.forEach { entry ->
            rows.appendRow(
                listOf(
                    entry.date.format(DATE_FORMAT),
                    entry.title,
                    entry.description.orEmpty(),
                    entry.quantity?.toString().orEmpty(),
                    headers.statusLabel(entry.status),
                    entry.customField1.orEmpty(),
                    entry.customField2.orEmpty(),
                    entry.customField3.orEmpty(),
                )
            )
        }

        return rows.toString()
    }

    private fun StringBuilder.appendRow(values: List<String>) {
        values.joinTo(this, separator = ",") { escape(it) }
        // CRLF, which is what RFC 4180 specifies and what Excel expects.
        append("\r\n")
    }

    /**
     * RFC 4180 escaping: a field containing a comma, a quote, a newline or
     * leading/trailing space is wrapped in quotes, and embedded quotes are
     * doubled.
     *
     * Free-text notes routinely contain commas and newlines, so getting this
     * wrong silently shifts every following column in the row.
     */
    private fun escape(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            value != value.trim()

        return if (needsQuoting) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    /**
     * Column headings and status labels, passed in from the caller so they come
     * from string resources and the exported file is in the user's language.
     */
    data class Headers(
        val date: String = "Date",
        val title: String = "Title",
        val description: String = "Description",
        val quantity: String = "Quantity",
        val status: String = "Status",
        val customField1: String = "Custom field 1",
        val customField2: String = "Custom field 2",
        val customField3: String = "Custom field 3",
        val inProgress: String = "In Progress",
        val completed: String = "Completed",
        val delivered: String = "Delivered",
    ) {
        fun statusLabel(status: com.evergreen.trackora.domain.model.Status): String =
            when (status) {
                com.evergreen.trackora.domain.model.Status.IN_PROGRESS -> inProgress
                com.evergreen.trackora.domain.model.Status.COMPLETED -> completed
                com.evergreen.trackora.domain.model.Status.DELIVERED -> delivered
            }
    }
}

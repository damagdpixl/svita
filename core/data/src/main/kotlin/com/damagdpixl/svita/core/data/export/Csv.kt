package com.damagdpixl.svita.core.data.export

/**
 * RFC 4180 CSV encoding for [BackupManager.exportItemsCsv]: a field containing
 * a comma, double quote, CR or LF is wrapped in double quotes with inner
 * quotes doubled; rows are terminated with CRLF. Headers are English column
 * names; values are emitted as stored (except for the formula guard below),
 * without reformatting.
 */
internal object Csv {
    private val specialChars = charArrayOf(',', '"', '\r', '\n')

    /** A value whose first character spreadsheet apps would execute as a formula. */
    private val formulaLeads = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /**
     * Standard CSV formula-injection guard: a value starting with `=`, `+`,
     * `-`, `@`, TAB or CR gets a `'` prefix so Excel/LibreOffice load it as
     * text instead of executing it. This includes legitimate negative numbers
     * — the accepted trade-off of the guard (documented in
     * `docs/export_format.md`).
     */
    public fun guardFormula(value: String): String =
        if (value.isNotEmpty() && value[0] in formulaLeads) "'$value" else value

    public fun encodeField(value: String): String =
        if (value.indexOfAny(specialChars) >= 0) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    public fun encodeRow(fields: List<String>): String =
        fields.joinToString(separator = ",") { encodeField(it) } + "\r\n"

    /** Data row: formula guard first, then the regular RFC 4180 encoding. */
    public fun encodeDataRow(fields: List<String>): String =
        encodeRow(fields.map { guardFormula(it) })
}

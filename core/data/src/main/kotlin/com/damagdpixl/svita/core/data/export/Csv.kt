package com.damagdpixl.svita.core.data.export

/**
 * RFC 4180 CSV encoding for [BackupManager.exportItemsCsv]: a field containing
 * a comma, double quote, CR or LF is wrapped in double quotes with inner
 * quotes doubled; rows are terminated with CRLF. Headers are English column
 * names; values are emitted as stored, without reformatting.
 */
internal object Csv {
    private val specialChars = charArrayOf(',', '"', '\r', '\n')

    public fun encodeField(value: String): String =
        if (value.indexOfAny(specialChars) >= 0) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    public fun encodeRow(fields: List<String>): String =
        fields.joinToString(separator = ",") { encodeField(it) } + "\r\n"
}

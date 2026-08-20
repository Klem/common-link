package org.commonlink.util

/**
 * Identifies a file's real type from its leading bytes.
 *
 * **Why** — upload validation used to trust the `Content-Type` of the multipart part, which is
 * whatever the caller typed. The bytes are then stored and, for logos and campaign covers, served
 * back verbatim from a public endpoint. Nothing checked that a part announced as `image/png`
 * actually contained a PNG (security audit 2026-08-20, M9).
 *
 * The check is deliberately narrow: it answers "do these bytes match the type that was declared",
 * not "what is this file". Anything it cannot recognise is rejected by the callers rather than
 * guessed at.
 */
object FileTypeSniffer {

    /**
     * MIME types this sniffer can confirm, i.e. those accepted by any upload endpoint.
     *
     * OOXML formats (DOCX, XLSX) are ZIP containers and share one signature, so they are confirmed
     * as "a ZIP container" — telling a DOCX from an XLSX means reading the archive directory, which
     * buys nothing here: neither is ever served back as a document to a browser.
     */
    private val SIGNATURES: Map<String, List<ByteArray>> = mapOf(
        "application/pdf" to listOf(bytes(0x25, 0x50, 0x44, 0x46)),                     // %PDF
        "image/jpeg" to listOf(bytes(0xFF, 0xD8, 0xFF)),
        "image/png" to listOf(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
        "image/webp" to listOf(bytes(0x52, 0x49, 0x46, 0x46)),                          // RIFF….WEBP
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to
            listOf(bytes(0x50, 0x4B, 0x03, 0x04), bytes(0x50, 0x4B, 0x05, 0x06)),       // PK..
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to
            listOf(bytes(0x50, 0x4B, 0x03, 0x04), bytes(0x50, 0x4B, 0x05, 0x06)),
    )

    /** Longest signature; enough leading bytes for any check below. */
    const val HEADER_BYTES = 12

    /**
     * Image types the public endpoints may serve. Deliberately excludes SVG: it is an XSS vector
     * when served from our own origin, which is why the upload allow-lists exclude it too.
     */
    private val SERVED_IMAGE_MIMES = listOf("image/jpeg", "image/png", "image/webp")

    /**
     * Whether [content] really is of type [declaredMime].
     *
     * @param content File bytes; only the first [HEADER_BYTES] are read.
     * @param declaredMime MIME type announced by the caller, already checked against an allow-list.
     * @return `false` when the bytes contradict [declaredMime], or when [declaredMime] is not one
     *   this sniffer knows — an unknown type must not pass by default.
     */
    fun matches(content: ByteArray, declaredMime: String): Boolean {
        val signatures = SIGNATURES[declaredMime] ?: return false
        if (!signatures.any { content.startsWith(it) }) return false
        // WebP shares the RIFF container with other formats; the format tag sits at offset 8.
        if (declaredMime == "image/webp") {
            return content.size >= HEADER_BYTES &&
                String(content, 8, 4, Charsets.US_ASCII) == "WEBP"
        }
        return true
    }

    /**
     * MIME type of [content] when it is one of the three image formats this platform serves back to
     * browsers, `null` otherwise.
     *
     * Used by the public serving endpoints instead of the stored `contentType` column. Uploads are
     * byte-checked now, but rows written before that check was added never were: replaying a stored
     * declaration would still let a legacy row be served as an image type its bytes contradict
     * (security audit 2026-08-20, M9).
     *
     * @param content File bytes; only the first [HEADER_BYTES] are read.
     */
    fun detectImageMime(content: ByteArray): String? =
        SERVED_IMAGE_MIMES.firstOrNull { matches(content, it) }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { values[it].toByte() }
}

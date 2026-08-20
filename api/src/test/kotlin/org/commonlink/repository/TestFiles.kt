package org.commonlink.repository

/**
 * Byte payloads that pass [org.commonlink.util.FileTypeSniffer] for each accepted upload type.
 *
 * Upload validation checks the leading bytes against the declared MIME type
 * (security audit 2026-08-20, M9), so a test fixture can no longer be arbitrary bytes labelled
 * `image/png`. These builders produce the smallest payload that is genuinely of the claimed type as
 * far as the signature check is concerned.
 *
 * [mislabelled] is the negative case: real content under a false declaration.
 */
object TestFiles {

    /** PNG signature followed by [padding] filler bytes. */
    fun png(padding: Int = 8): ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(padding)

    /** JPEG SOI marker followed by [padding] filler bytes. */
    fun jpeg(padding: Int = 8): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(padding)

    /** RIFF container carrying the `WEBP` form tag at offset 8. */
    fun webp(padding: Int = 8): ByteArray =
        "RIFF".toByteArray(Charsets.US_ASCII) +
            ByteArray(4) +
            "WEBP".toByteArray(Charsets.US_ASCII) +
            ByteArray(padding)

    /** `%PDF` header followed by a minimal body. */
    fun pdf(): ByteArray = "%PDF-1.7\n%minimal test document\n".toByteArray(Charsets.US_ASCII)

    /** ZIP local-file header — the container signature shared by DOCX and XLSX. */
    fun ooxml(padding: Int = 8): ByteArray =
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(padding)

    /** HTML content — never a valid payload for any accepted type. */
    fun mislabelled(): ByteArray =
        "<html><script>alert(1)</script></html>".toByteArray(Charsets.US_ASCII)
}

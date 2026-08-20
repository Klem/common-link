package org.commonlink.util

import org.commonlink.repository.TestFiles
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers upload byte validation (security audit 2026-08-20, M9).
 *
 * The property under test: a declared MIME type is only accepted when the bytes agree with it, and
 * an unrecognised declaration is never accepted by default.
 */
class FileTypeSnifferTest {

    private val docx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    @Test
    fun `accepts each supported type when the bytes match`() {
        assertTrue(FileTypeSniffer.matches(TestFiles.png(), "image/png"))
        assertTrue(FileTypeSniffer.matches(TestFiles.jpeg(), "image/jpeg"))
        assertTrue(FileTypeSniffer.matches(TestFiles.webp(), "image/webp"))
        assertTrue(FileTypeSniffer.matches(TestFiles.pdf(), "application/pdf"))
        assertTrue(FileTypeSniffer.matches(TestFiles.ooxml(), docx))
    }

    @Test
    fun `rejects content that contradicts its declaration`() {
        assertFalse(FileTypeSniffer.matches(TestFiles.mislabelled(), "image/png"))
        assertFalse(FileTypeSniffer.matches(TestFiles.mislabelled(), "application/pdf"))
        assertFalse(FileTypeSniffer.matches(TestFiles.png(), "application/pdf"))
        assertFalse(FileTypeSniffer.matches(TestFiles.pdf(), "image/jpeg"))
    }

    @Test
    fun `rejects a RIFF container that is not WebP`() {
        // RIFF is shared with other formats; the WEBP form tag at offset 8 is what settles it.
        val riffWave = "RIFF".toByteArray() + ByteArray(4) + "WAVE".toByteArray() + ByteArray(8)

        assertFalse(FileTypeSniffer.matches(riffWave, "image/webp"))
    }

    @Test
    fun `rejects an unknown declared type rather than letting it through`() {
        assertFalse(FileTypeSniffer.matches("<svg/>".toByteArray(), "image/svg+xml"))
        assertFalse(FileTypeSniffer.matches(TestFiles.png(), "application/octet-stream"))
    }

    @Test
    fun `rejects content shorter than the signature it claims`() {
        assertFalse(FileTypeSniffer.matches(byteArrayOf(0x89.toByte(), 0x50), "image/png"))
        assertFalse(FileTypeSniffer.matches(ByteArray(0), "image/png"))
    }
}

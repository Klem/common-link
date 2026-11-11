package org.commonlink.util

import org.commonlink.exception.UnprocessableEntityException
import org.slf4j.LoggerFactory
import org.springframework.web.multipart.MultipartFile

/**
 * Checks an uploaded file before it is stored: size, declared type against an allow-list, and bytes matching the
 * declared type ([FileTypeSniffer]). Shared by every upload that keeps a file (association documents, curator release
 * evidence).
 */
object UploadedFileValidator {

    /** Default size limit of an uploaded file: 10 MB. */
    const val MAX_FILE_SIZE: Long = 10L * 1024 * 1024

    private val logger = LoggerFactory.getLogger(UploadedFileValidator::class.java)

    /**
     * Refuses [file] when it exceeds [maxSize], its declared type is not in [allowedMime], or its bytes contradict
     * that type. The declared type is caller-controlled and is replayed as the Content-Type when the file is
     * downloaded: the bytes must match what was announced (security audit 2026-08-20, M9).
     *
     * @throws UnprocessableEntityException on any refusal (HTTP 422).
     */
    fun validate(file: MultipartFile, allowedMime: Set<String>, maxSize: Long = MAX_FILE_SIZE) {
        if (file.size > maxSize) {
            throw UnprocessableEntityException("File size exceeds the ${maxSize / (1024 * 1024)} MB limit (${file.size} bytes)")
        }
        val mime = file.contentType ?: "application/octet-stream"
        if (mime !in allowedMime) {
            throw UnprocessableEntityException("File type '$mime' is not allowed. Accepted: ${allowedMime.joinToString()}")
        }
        if (!FileTypeSniffer.matches(file.bytes, mime)) {
            logger.warn("Rejected upload: bytes do not match declared type {}", mime)
            throw UnprocessableEntityException("File content does not match its declared type '$mime'")
        }
    }
}

package org.commonlink.exception

import org.springframework.http.HttpStatus

/**
 * Thrown when the client accepted a CGU/CGV version that is no longer the current one — a new text
 * was published between the moment it was displayed and the moment it was accepted (HTTP 409).
 *
 * Recording the current version instead would make the proof name a text the person never saw.
 * [GlobalExceptionHandler] adds `code: LEGAL_VERSION_OUTDATED` so the frontend reloads the text and
 * asks for acceptance again.
 */
class LegalDocumentVersionOutdatedException(message: String) :
    AppException(message, HttpStatus.CONFLICT)

/**
 * Thrown at startup when the landing site's CGU/CGV texts cannot be turned into a valid
 * [org.commonlink.dto.LegalTextV1] (missing key, unreadable effective date, unknown link tag, markup
 * left in a field), or when a version already stored carries a different text. Deliberately fatal:
 * the application must not start while it cannot say which text is in force.
 */
class LegalTextSourceException(message: String) : IllegalStateException(message)

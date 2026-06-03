package org.commonlink.exception

import org.springframework.http.HttpStatus

/**
 * Thrown when the stored Monerium refresh token is no longer valid (rotated, revoked, or expired)
 * and the connection has been flagged BROKEN. The frontend must re-trigger the PKCE flow to
 * reconnect the association; no automated recovery is possible because PKCE requires user
 * interaction. Returns HTTP 409 with a `code: MONERIUM_REAUTH_REQUIRED` property so the
 * frontend can branch without parsing the human-readable message.
 */
class MoneriumReauthRequiredException(
    message: String = "Monerium connection no longer valid — re-authentication required"
) : AppException(message, HttpStatus.CONFLICT)

package org.commonlink.exception

import org.springframework.http.HttpStatus

/**
 * Base class for all application-specific exceptions.
 *
 * Each subclass carries an [httpStatus] that [GlobalExceptionHandler] uses to build the
 * HTTP response, following the RFC 9457 Problem Detail format via [org.springframework.http.ProblemDetail].
 *
 * @param message Human-readable description of the error (included in the Problem Detail body).
 * @param httpStatus The HTTP status code to return to the client.
 */
abstract class AppException(
    message: String,
    val httpStatus: HttpStatus
) : RuntimeException(message)

/** Thrown when the caller is not authenticated or credentials are incorrect (HTTP 401). */
class AuthException(message: String = "Authentication required") :
    AppException(message, HttpStatus.UNAUTHORIZED)

/** Thrown when a requested user cannot be found in the database (HTTP 404). */
class UserNotFoundException(message: String = "User not found") :
    AppException(message, HttpStatus.NOT_FOUND)

/** Thrown when a token (JWT, magic-link, refresh, or email verification) has passed its expiry date (HTTP 401). */
class TokenExpiredException(message: String = "Expired token") :
    AppException(message, HttpStatus.UNAUTHORIZED)

/** Thrown when a token is structurally invalid, has a bad signature, or does not exist in the database (HTTP 401). */
class InvalidTokenException(message: String = "Invalid token") :
    AppException(message, HttpStatus.UNAUTHORIZED)

/** Thrown when a uniqueness constraint would be violated, e.g. duplicate email on registration (HTTP 409). */
class ConflictException(message: String) :
    AppException(message, HttpStatus.CONFLICT)

/** Thrown when a caller exceeds the allowed request rate for a sensitive operation (HTTP 429). */
class RateLimitException(message: String = "Rate limit exceeded. Try again later") :
    AppException(message, HttpStatus.TOO_MANY_REQUESTS)

/**
 * Thrown during email/password login when the account has no password hash set.
 *
 * This happens for accounts that were created exclusively via Google OAuth or magic-link.
 * The client should prompt the user to set a password or use an alternative login method.
 * Returns HTTP 401 with a `code: PASSWORD_NOT_SET` property for client-side branching.
 */
class PasswordNotSetException(
    message: String = "No password defined, Use Magic Link or Google"
) : AppException(message, HttpStatus.UNAUTHORIZED)

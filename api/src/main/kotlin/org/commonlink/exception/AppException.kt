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

/**
 * Thrown during email/password login when the account exists but its email is not yet verified.
 *
 * Returns HTTP 401 with a `code: EMAIL_NOT_VERIFIED` property so the frontend can surface a
 * distinct "verify your email" message instead of the generic "wrong credentials" one.
 */
class EmailNotVerifiedException(
    message: String = "Email non vérifié. Consultez votre boîte mail."
) : AppException(message, HttpStatus.UNAUTHORIZED)

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

/**
 * Thrown when an association sign-up carries a SIREN that is already registered.
 *
 * A plain [ConflictException] would be indistinguishable from "this email is already in use", which
 * the sign-up screen already renders on 409 — and the two call for opposite user actions: log in
 * versus contact the existing account holder. Returns HTTP 409 with a
 * `code: SIREN_ALREADY_REGISTERED` property for client-side branching.
 */
class SirenAlreadyRegisteredException(
    message: String = "SIREN already registered"
) : AppException(message, HttpStatus.CONFLICT)

/**
 * Thrown when a caller exceeds the allowed request rate for a sensitive operation (HTTP 429).
 *
 * @param retryAfterSeconds Sent as the `Retry-After` header by [org.commonlink.exception.GlobalExceptionHandler] —
 *   defaults to 600 (10 minutes), matching [org.commonlink.security.AuthRateLimiter]'s default window.
 */
class RateLimitException(
    message: String = "Rate limit exceeded. Try again later",
    val retryAfterSeconds: Long = 600,
) : AppException(message, HttpStatus.TOO_MANY_REQUESTS)

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

/** Thrown when a requested resource cannot be found (HTTP 404). */
class NotFoundException(message: String) :
    AppException(message, HttpStatus.NOT_FOUND)

/** Thrown when an upstream dependency (e.g. an external API) is unavailable or returns an error (HTTP 502). */
open class BadGatewayException(message: String) :
    AppException(message, HttpStatus.BAD_GATEWAY)

/**
 * Thrown when Bridge did not accept the request that would have created a payment link, so **no
 * link exists** — a refusal, a timeout, a network failure.
 *
 * Distinguished from a plain [BadGatewayException] because it decides whether a payout row is worth
 * keeping. Nothing was created at Bridge: no link, no authorisation URL, nothing that any later
 * notification could refer to. The row records only that a form failed to submit, so it is deleted
 * rather than left behind. Every other failure of the initiation keeps the payout, because a link
 * may exist — a destination read-back refused is the clearest case, and it is evidence of a control
 * that `docs/legal/verification-payee-iban.md` describes.
 *
 * Its own type rather than a test on the message: recognising this case by matching text would
 * break the day a wording changes, and what it gates is a deletion.
 */
open class BridgeInitiationNotStartedException(message: String) :
    BadGatewayException(message)

/**
 * Thrown when Bridge **answered** the creation request and refused it — a `4xx`.
 *
 * Still a [BridgeInitiationNotStartedException], so the payout row is dropped like any other
 * initiation that created nothing. What it changes is who gets woken up: Bridge is not
 * unavailable, it understood the request perfectly and said no, so this raises no technical alert.
 * On 2026-09-23 three e-mails went out because a tab character had been pasted into a payout's
 * label — noise of that kind is what makes a real outage go unnoticed.
 *
 * It stays loud in the logs. With the statement label now rendered before it is sent, a refusal
 * here means the integration disagrees with Bridge about what a valid request is, which is worth
 * reading — just not worth paging anyone at night.
 */
class BridgeRequestRefusedException(message: String) :
    BridgeInitiationNotStartedException(message)

/**
 * Thrown when Bridge read a payment link back with a destination it could not be vouched for.
 *
 * A link **exists** — unlike [BridgeInitiationNotStartedException] — and has been revoked on the
 * way out, so the payout row is kept as evidence that the control ran. What this type adds over a
 * plain [BadGatewayException] is the ability to tell an association *why* without reading the text
 * of a message: "we could not vouch for where this money was going" is not "the bank was busy".
 *
 * Same discipline as [BridgeRequestRefusedException]: the distinction lives in the type, because
 * recognising a cause from its wording breaks at the first rewording.
 */
class BridgeDestinationRefusedException(message: String) :
    BadGatewayException(message)

/** Thrown when a request is semantically invalid, e.g. attempting VOP on an IBAN that is not FORMAT_VALID (HTTP 422). */
class UnprocessableEntityException(message: String) :
    AppException(message, HttpStatus.UNPROCESSABLE_CONTENT)

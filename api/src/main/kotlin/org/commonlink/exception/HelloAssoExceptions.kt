package org.commonlink.exception

import org.springframework.http.HttpStatus

/**
 * Failures of a HelloAsso call ([org.commonlink.service.HelloAssoClient]).
 *
 * **Never carry a secret, a token, nor the HelloAsso response body** (which may echo them): the message holds only
 * the operation and the HTTP status. These exceptions are internal — callers map them to their own outcome.
 */
sealed class HelloAssoException(message: String) : RuntimeException(message)

/** HelloAsso refused our credentials (HTTP 401, or 400 `invalid_grant` on a token call): the token chain is broken. */
class HelloAssoUnauthorizedException(operation: String, status: Int) :
    HelloAssoException("HelloAsso refused the credentials ($operation, HTTP $status)")

/** HelloAsso refused the operation because the organization cannot collect (HTTP 409). */
class HelloAssoNotEligibleException(operation: String) :
    HelloAssoException("HelloAsso refused the operation: organization not eligible ($operation, HTTP 409)")

/**
 * HelloAsso understood the request and refused it (HTTP 400 or 422 on an authenticated call): the request we built
 * is wrong, HelloAsso is not down — a non-HTTPS return URL on a checkout did exactly that on 2026-11-05. The refused
 * fields are not reported: HelloAsso does not document its error body, and the body is never read.
 */
class HelloAssoRequestRefusedException(operation: String, status: Int) :
    HelloAssoException("HelloAsso refused the request ($operation, HTTP $status)")

/** Too many calls (HTTP 429 after bounded retries, or the local auth-call limit reached before any call). */
class HelloAssoRateLimitedException(operation: String) :
    HelloAssoException("HelloAsso rate limit reached ($operation)")

/** HelloAsso unreachable or failing (network error, HTTP 5xx, unexpected status or empty response). */
class HelloAssoUnavailableException(operation: String, status: Int? = null) :
    HelloAssoException("HelloAsso unavailable ($operation${status?.let { ", HTTP $it" } ?: ""})")

/**
 * Refusal of an association's HelloAsso linking step, returned to the dashboard with a machine-readable [code]
 * (ProblemDetail property) so the front can show the right message. Never carries a secret nor a token.
 */
sealed class HelloAssoLinkException(message: String, val status: HttpStatus, val code: String) : RuntimeException(message)

/** HelloAsso refused the API keys the association entered (HTTP 422). */
class HelloAssoInvalidKeysException :
    HelloAssoLinkException("HelloAsso refused these API keys", HttpStatus.UNPROCESSABLE_CONTENT, "HELLOASSO_INVALID_KEYS")

/** The keys do not resolve to exactly one HelloAsso organization (HTTP 422). */
class HelloAssoOrganizationUnresolvedException :
    HelloAssoLinkException(
        "These API keys do not resolve to exactly one HelloAsso organization",
        HttpStatus.UNPROCESSABLE_CONTENT,
        "HELLOASSO_ORGANIZATION_UNRESOLVED",
    )

/** The association's payment provider is not HELLOASSO: HelloAsso linking steps are refused (HTTP 409). */
class HelloAssoNotAssociationProviderException :
    HelloAssoLinkException(
        "This association does not collect donations through HelloAsso",
        HttpStatus.CONFLICT,
        "HELLOASSO_NOT_ASSOCIATION_PROVIDER",
    )

/** The mandate was not accepted, or not in the current wording version (HTTP 422). */
class HelloAssoMandateRequiredException :
    HelloAssoLinkException(
        "The current HelloAsso mandate must be accepted to link the account",
        HttpStatus.UNPROCESSABLE_CONTENT,
        "HELLOASSO_MANDATE_REQUIRED",
    )

/** The key verification is unknown, expired, already used or another association's (HTTP 422). */
class HelloAssoVerificationExpiredException :
    HelloAssoLinkException(
        "The HelloAsso key verification is expired or unknown; verify the keys again",
        HttpStatus.UNPROCESSABLE_CONTENT,
        "HELLOASSO_VERIFICATION_EXPIRED",
    )

/** The association has no ACTIVE HelloAsso link (HTTP 409). */
class HelloAssoNotLinkedException :
    HelloAssoLinkException("No active HelloAsso link for this association", HttpStatus.CONFLICT, "HELLOASSO_NOT_LINKED")

/** `app.helloasso.webhook-url` is blank: no notification URL can be shown (HTTP 503). */
class HelloAssoNotificationUrlNotConfiguredException :
    HelloAssoLinkException(
        "The HelloAsso notification URL is not configured on this server",
        HttpStatus.SERVICE_UNAVAILABLE,
        "HELLOASSO_NOTIFICATION_URL_NOT_CONFIGURED",
    )

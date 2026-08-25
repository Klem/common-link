package org.commonlink.exception

import jakarta.servlet.http.HttpServletRequest
import org.commonlink.service.TechnicalAlertKind
import org.commonlink.service.TechnicalAlertService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Centralised exception-to-HTTP-response mapping for all controllers.
 *
 * All responses follow the RFC 9457 Problem Detail format ([ProblemDetail]).
 * Token-related errors include a machine-readable `code` property so the frontend
 * can branch without parsing the human-readable `detail` string.
 *
 * The catch-all [handleGeneric] logs the full stack trace at ERROR level, which is
 * intentionally not exposed to the client to avoid information leakage.
 *
 * ## Which failures reach a developer
 * Most handlers below map a *user* mistake — a wrong password, a stale campaign state, a missing
 * resource — and are logged, at most, at WARN. A minority mean the platform itself is broken and
 * nobody would find out from a 500 body: those additionally call [TechnicalAlertService], which
 * owns the throttling and the e-mail. Three are reported on first occurrence ([handleGeneric],
 * [handleMolliePayment], [handleBadGateway]); two are reported only as a *rate*
 * ([handleAccessDenied], [handleRateLimit]), because one occurrence of either is ordinary traffic
 * and a burst is a probe. Adding an alert to any other handler means volunteering to be paged by
 * normal user behaviour.
 *
 * ## Logging levels
 * Client-caused outcomes are DEBUG or WARN, server-caused ones are ERROR. The distinction is what
 * makes an ERROR-level log watch meaningful: a 422 on a campaign form must never sit at the same
 * level as an unreachable payment gateway.
 *
 * @property technicalAlertServiceProvider Alerting collaborator, resolved lazily and optionally.
 *   `@RestControllerAdvice` beans are loaded into every `@WebMvcTest` slice, and none of those
 *   slices declares an alerting bean; an [ObjectProvider] lets the handler keep working — silently,
 *   without alerts — in a slice, instead of forcing a mock into twenty-odd unrelated tests.
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val technicalAlertServiceProvider: ObjectProvider<TechnicalAlertService>,
) : ResponseEntityExceptionHandler() {

    private val appLogger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Overrides the default 400 handler for `@Valid` / `@Validated` failures.
     *
     * Returns HTTP 422 instead of 400, with a list of field-level error messages under
     * the `errors` property. This gives the frontend enough detail to highlight specific
     * form fields without returning a generic bad-request response.
     */
    // Override parent's 400 handler → return 422 with field-level errors instead
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val errors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed")
        problem.setProperty("errors", errors)
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }

    /**
     * Handles [IllegalArgumentException] (malformed UUIDs, invalid enum values, etc.) (HTTP 400).
     *
     * The raw exception message is logged server-side but never sent to the client to avoid
     * leaking internal details such as UUID parse error messages.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> {
        appLogger.warn("Bad request — illegal argument: {}", ex.message)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Bad Request")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem)
    }

    /**
     * Handles [PasswordNotSetException] with a machine-readable `code: PASSWORD_NOT_SET` property (HTTP 401).
     *
     * The frontend uses this code to offer the user a "set password" or alternative login flow.
     */
    @ExceptionHandler(PasswordNotSetException::class)
    fun handlePasswordNotSet(ex: PasswordNotSetException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.message ?: "Password not set")
        problem.setProperty("code", "PASSWORD_NOT_SET")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem)
    }

    /**
     * Handles [EmailNotVerifiedException] with a machine-readable `code: EMAIL_NOT_VERIFIED` property (HTTP 401).
     *
     * The frontend uses this code to tell the user their email is unverified rather than
     * mislabelling it as a wrong password.
     */
    @ExceptionHandler(EmailNotVerifiedException::class)
    fun handleEmailNotVerified(ex: EmailNotVerifiedException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.message ?: "Email not verified")
        problem.setProperty("code", "EMAIL_NOT_VERIFIED")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem)
    }

    /**
     * Handles [TokenExpiredException] with a `code: TOKEN_EXPIRED` property (HTTP 401).
     *
     * The frontend uses this code to automatically attempt a token refresh before retrying.
     */
    @ExceptionHandler(TokenExpiredException::class)
    fun handleTokenExpired(ex: TokenExpiredException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.message ?: "Token expired")
        problem.setProperty("code", "TOKEN_EXPIRED")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem)
    }

    /**
     * Handles [InvalidTokenException] with a `code: TOKEN_INVALID` property (HTTP 401).
     */
    @ExceptionHandler(InvalidTokenException::class)
    fun handleInvalidToken(ex: InvalidTokenException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.message ?: "Invalid token")
        problem.setProperty("code", "TOKEN_INVALID")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem)
    }

    /**
     * Handles general [AuthException] (wrong credentials, missing account, etc.) (HTTP 401).
     */
    @ExceptionHandler(AuthException::class)
    fun handleAuth(ex: AuthException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.message ?: "Authentication required")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem)
    }

    /**
     * Handles [UserNotFoundException] (HTTP 404).
     */
    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(ex: UserNotFoundException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "User not found")
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    /**
     * Handles [ConflictException], e.g. duplicate email on registration (HTTP 409).
     */
    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Conflict")
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    /**
     * Handles [SirenAlreadyRegisteredException] with a `code: SIREN_ALREADY_REGISTERED` property
     * (HTTP 409), so the sign-up screen does not report a duplicate SIREN as a duplicate email.
     */
    @ExceptionHandler(SirenAlreadyRegisteredException::class)
    fun handleSirenAlreadyRegistered(ex: SirenAlreadyRegisteredException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "SIREN already registered")
        problem.setProperty("code", "SIREN_ALREADY_REGISTERED")
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    /**
     * Handles [CollectionCapExceededException] with a `code: COLLECTION_CAP_EXCEEDED` property and
     * the still-acceptable amount (HTTP 409).
     *
     * A plain [ConflictException] would be indistinguishable from "this association cannot collect
     * yet", which the widget already renders on 409 — and the two call for opposite donor actions:
     * come back later versus lower the amount.
     */
    @ExceptionHandler(CollectionCapExceededException::class)
    fun handleCollectionCapExceeded(ex: CollectionCapExceededException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Collection cap reached")
        problem.setProperty("code", "COLLECTION_CAP_EXCEEDED")
        problem.setProperty("remainingCapacity", ex.remainingCapacity)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem)
    }

    /**
     * Handles [RateLimitException] (HTTP 429).
     *
     * Includes a `Retry-After: 600` header (10 minutes) as guidance for clients and proxies.
     *
     * Alerted **on burst only**, for the same reason as [handleAccessDenied]: the limiter doing its
     * job on one impatient user is not news, whereas a sustained stream of 429s on the login path
     * is credential stuffing and on the widget path is scripted abuse.
     */
    @ExceptionHandler(RateLimitException::class)
    fun handleRateLimit(ex: RateLimitException, request: HttpServletRequest?): ResponseEntity<ProblemDetail> {
        appLogger.warn("Rate limit exceeded on {}: {}", path(request), ex.message)
        burst(TechnicalAlertKind.RATE_LIMIT_BURST, request)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.message ?: "Rate limit exceeded")
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", "600")
            .body(problem)
    }

    /**
     * Handles [AccessDeniedException] thrown by `@PreAuthorize` method security (HTTP 403).
     *
     * In Spring Security 7, `AuthorizationDeniedException` (a subclass) is thrown from AOP
     * interceptors inside the MVC dispatch — after `ExceptionTranslationFilter` has already run.
     * Without this handler it would reach [handleGeneric] and produce a 500.
     *
     * Alerted **on burst only**. A single 403 is a user on the wrong screen, or a stale tab after a
     * role change. A sustained stream of them is somebody enumerating endpoints against a token
     * they hold — the shape of the privilege-escalation attempt the 2026-08-20 audit closed (C1),
     * which is exactly the thing worth learning about while it is still happening.
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException, request: HttpServletRequest?): ResponseEntity<ProblemDetail> {
        appLogger.warn("Access denied on {}: {}", path(request), ex.message)
        burst(TechnicalAlertKind.ACCESS_DENIED_BURST, request)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied")
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem)
    }

    /**
     * Handles [NotFoundException] for any resource not found (HTTP 404).
     */
    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found")
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    /**
     * Handles [BadGatewayException] when an upstream dependency is unavailable (HTTP 502).
     *
     * Alerted: the throw sites are the INSEE registry lookup and the VOP payee check, so this
     * status means association onboarding and IBAN verification are both stalled — a state no user
     * can resolve and no user will report as anything but "the site is broken".
     */
    @ExceptionHandler(BadGatewayException::class)
    fun handleBadGateway(ex: BadGatewayException, request: HttpServletRequest?): ResponseEntity<ProblemDetail> {
        appLogger.error("Upstream dependency unavailable on {}: {}", path(request), ex.message)
        alert(TechnicalAlertKind.UPSTREAM_UNAVAILABLE, request, ex)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.message ?: "Bad gateway")
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem)
    }

    /**
     * Handles [UnprocessableEntityException], e.g. when VOP is attempted on an IBAN with invalid status (HTTP 422).
     *
     * Logged at DEBUG and never alerted. Every throw site is a business rule refusing a user
     * action — campaign state transitions, file size and MIME checks, publication guards — so this
     * is expected traffic. Its messages also interpolate user-supplied values, which is a second
     * reason to keep them out of an e-mail channel.
     */
    @ExceptionHandler(UnprocessableEntityException::class)
    fun handleUnprocessableEntity(ex: UnprocessableEntityException, request: HttpServletRequest?): ResponseEntity<ProblemDetail> {
        appLogger.debug("Unprocessable entity on {}: {}", path(request), ex.message)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Unprocessable entity")
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }

    /**
     * Handles [MolliePaymentException] when the Mollie payment gateway is unreachable or returns an error (HTTP 502).
     *
     * Alerted: this is the donation path failing. Every occurrence is a donor who tried to give and
     * could not, and the association hears about it before anybody on the team otherwise would.
     */
    @ExceptionHandler(MolliePaymentException::class)
    fun handleMolliePayment(ex: MolliePaymentException, request: HttpServletRequest?): ResponseEntity<ProblemDetail> {
        appLogger.error("Mollie payment error on {}: {}", path(request), ex.message)
        alert(TechnicalAlertKind.PAYMENT_GATEWAY_FAILURE, request, ex)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Payment gateway error")
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem)
    }

    /**
     * Catch-all handler for any unhandled [Exception] (HTTP 500).
     *
     * Logs the full stack trace at ERROR level but returns only a generic message to the
     * client to avoid leaking internal implementation details.
     *
     * Alerted: reaching here means an exception nobody anticipated escaped a controller, which is
     * by definition a defect. [TechnicalAlertService] filters out client disconnects, the one
     * routine visitor to this handler.
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest?): ResponseEntity<ProblemDetail> {
        appLogger.error("Unexpected error on {}", path(request), ex)
        alert(TechnicalAlertKind.UNHANDLED_EXCEPTION, request, ex)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }

    /**
     * Reports a failure worth an e-mail on first occurrence.
     *
     * Wrapped in a try/catch even though [TechnicalAlertService] already swallows its own errors:
     * the provider lookup happens here, and an exception escaping an exception handler replaces a
     * handled failure with an unhandled one — the single worst outcome available at this point.
     */
    private fun alert(kind: TechnicalAlertKind, request: HttpServletRequest?, ex: Throwable) {
        try {
            technicalAlertServiceProvider.ifAvailable?.reportFailure(kind, request?.method, path(request), ex)
        } catch (e: Exception) {
            appLogger.warn("Technical alert {} could not be raised: {}", kind, e.javaClass.simpleName)
        }
    }

    /** Records one occurrence of a rate-based signal. See [alert] for the try/catch rationale. */
    private fun burst(kind: TechnicalAlertKind, request: HttpServletRequest?) {
        try {
            technicalAlertServiceProvider.ifAvailable?.reportBurst(kind, request?.method, path(request))
        } catch (e: Exception) {
            appLogger.warn("Burst alert {} could not be raised: {}", kind, e.javaClass.simpleName)
        }
    }

    /**
     * Request path **without** the query string.
     *
     * Query strings carry verification tokens and e-mail addresses on this API, and these values
     * end up in logs and in alert e-mails — neither of which is an access-controlled channel.
     */
    private fun path(request: HttpServletRequest?): String = request?.requestURI ?: "(unknown)"
}

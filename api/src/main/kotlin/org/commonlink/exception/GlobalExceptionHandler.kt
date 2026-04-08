package org.commonlink.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
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
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

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
     * Handles [IllegalArgumentException], typically thrown for malformed UUIDs or enum values (HTTP 400).
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")
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
     * Handles [RateLimitException] (HTTP 429).
     *
     * Includes a `Retry-After: 600` header (10 minutes) as guidance for clients and proxies.
     */
    @ExceptionHandler(RateLimitException::class)
    fun handleRateLimit(ex: RateLimitException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.message ?: "Rate limit exceeded")
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", "600")
            .body(problem)
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
     */
    @ExceptionHandler(BadGatewayException::class)
    fun handleBadGateway(ex: BadGatewayException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.message ?: "Bad gateway")
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem)
    }

    /**
     * Handles [UnprocessableEntityException], e.g. when VOP is attempted on an IBAN with invalid status (HTTP 422).
     */
    @ExceptionHandler(UnprocessableEntityException::class)
    fun handleUnprocessableEntity(ex: UnprocessableEntityException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Unprocessable entity")
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem)
    }

    /**
     * Catch-all handler for any unhandled [Exception] (HTTP 500).
     *
     * Logs the full stack trace at ERROR level but returns only a generic message to the
     * client to avoid leaking internal implementation details.
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ProblemDetail> {
        appLogger.error("Unexpected error", ex)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }
}

package org.commonlink.exception

/**
 * Thrown when the Mollie payment gateway returns an error or is unreachable.
 * Maps to HTTP 502 Bad Gateway via [GlobalExceptionHandler].
 */
class MolliePaymentException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

package org.commonlink.exception

import org.springframework.http.HttpStatus

abstract class AppException(
    message: String,
    val httpStatus: HttpStatus
) : RuntimeException(message)

class AuthException(message: String = "Authentication required") :
    AppException(message, HttpStatus.UNAUTHORIZED)

class UserNotFoundException(message: String = "User not found") :
    AppException(message, HttpStatus.NOT_FOUND)

class TokenExpiredException(message: String = "Expired token") :
    AppException(message, HttpStatus.UNAUTHORIZED)

class InvalidTokenException(message: String = "Invalid token") :
    AppException(message, HttpStatus.UNAUTHORIZED)

class ConflictException(message: String) :
    AppException(message, HttpStatus.CONFLICT)

class RateLimitException(message: String = "Rate limit exceeded. Try again later") :
    AppException(message, HttpStatus.TOO_MANY_REQUESTS)

class PasswordNotSetException(
    message: String = "No password defined, Use Magic Link or Google"
) : AppException(message, HttpStatus.UNAUTHORIZED)

package org.commonlink.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Builds the `cl-refresh` cookie carrying the refresh token.
 *
 * Shared so that every endpoint rotating the refresh token emits an identical cookie — same path,
 * same flags, same lifetime. A cookie written with a different `path` would not overwrite the
 * previous one but sit alongside it, and the browser would keep sending a revoked token.
 *
 * `HttpOnly` keeps the token out of reach of scripts; `SameSite` and `Secure` come from
 * configuration because they depend on whether the frontend and the API share a site.
 */
@Component
class RefreshCookieFactory(
    @Value("\${app.cookies.secure:true}") private val secure: Boolean,
    @Value("\${app.cookies.same-site:Strict}") private val sameSite: String,
) {

    /**
     * Cookie carrying [token], valid for 30 days — the refresh token's own lifetime.
     *
     * @param token Raw refresh token. Never logged, never returned in a response body.
     */
    fun build(token: String): ResponseCookie =
        base(token).maxAge(Duration.ofDays(30)).build()

    /** Cookie that expires the current one, for logout. */
    fun clear(): ResponseCookie =
        base("").maxAge(Duration.ZERO).build()

    private fun base(value: String) =
        ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path(COOKIE_PATH)

    companion object {
        const val COOKIE_NAME = "cl-refresh"

        /** Scoped to the auth routes: no other endpoint has any use for the refresh token. */
        const val COOKIE_PATH = "/api/auth"
    }
}

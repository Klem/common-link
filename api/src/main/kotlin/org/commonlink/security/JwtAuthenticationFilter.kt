package org.commonlink.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.commonlink.exception.AppException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Spring Security filter that authenticates requests by extracting and validating the JWT
 * from the `Authorization: Bearer <token>` header.
 *
 * Runs once per request (extends [OncePerRequestFilter]). If the header is absent or the
 * token is invalid/expired, the filter simply continues the chain without setting an
 * authentication — Spring Security will then enforce access rules and return 401/403 as
 * appropriate for the requested resource.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsServiceImpl
) : OncePerRequestFilter() {

    /**
     * Extracts the JWT from the `Authorization` header, validates it, and populates the
     * [SecurityContextHolder] with an authenticated token if successful.
     *
     * Security decisions in this method:
     * - Missing or non-Bearer header: passes through without authentication (public endpoints work normally).
     * - Valid token + no existing authentication: loads [UserDetailsServiceImpl] by userId and sets the context.
     * - Any [AppException] (expired, invalid): clears the security context to prevent partial auth state.
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        // Skip filter entirely for requests without a Bearer token (e.g. public auth endpoints).
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.removePrefix("Bearer ")
        try {
            val userId = jwtService.extractUserId(token).toString()
            // Only set authentication if not already established (e.g. by an earlier filter).
            if (SecurityContextHolder.getContext().authentication == null) {
                val userDetails = userDetailsService.loadUserByUsername(userId)
                val authentication = UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.authorities
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
            }
        } catch (e: AppException) {
            // Token is expired or invalid — clear any partial context and let the chain continue.
            // The downstream security rules will reject the unauthenticated request.
            SecurityContextHolder.clearContext()
        }

        filterChain.doFilter(request, response)
    }
}

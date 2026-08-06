package org.commonlink.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Logs every successful request under the /api/compliance/ prefix at INFO level.
 *
 * Registered inside the Spring Security filter chain (after [AuthorizationFilter]) so it
 * only fires for authenticated, authorized requests — rejected calls never reach it.
 * Request and response bodies are never logged (they contain suspicious-activity data).
 */
class ComplianceAccessLogFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ComplianceAccessLogFilter::class.java)

    /**
     * Emits an INFO trace containing the caller's identifier and the requested route
     * for every request routed under the /api/compliance/ prefix.
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.requestURI.startsWith("/api/compliance/")) {
            val userId = SecurityContextHolder.getContext().authentication?.name ?: "anonymous"
            log.info("[COMPLIANCE_ACCESS] user={} route={}", userId, request.requestURI)
        }
        filterChain.doFilter(request, response)
    }
}

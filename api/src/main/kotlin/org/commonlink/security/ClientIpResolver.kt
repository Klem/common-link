package org.commonlink.security

import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletRequestWrapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Resolves the client IP address of a request for rate-limiting purposes.
 *
 * **Why not simply read `X-Forwarded-For`** — that header is client-controlled. Taking its first
 * entry, as this application used to, let a caller mint a fresh identity on every request and walk
 * straight through every IP-keyed quota (security audit 2026-08-20, M2).
 *
 * **How the chain is read** — a reverse proxy *appends* the address it saw to `X-Forwarded-For`.
 * Whatever the client sent stays to the left of that; the rightmost entries are the only ones our
 * own infrastructure wrote. So the real client sits at `size - trustedProxyCount`, counted from the
 * left, and everything to its left is untrusted input to be discarded.
 *
 * Set [trustedProxyCount] to the number of proxies that append to the header between the client and
 * the application: `1` for a single Clever Cloud reverse proxy. Set it to `0` to ignore the header
 * entirely and always use the socket address — correct when the application is reached directly.
 *
 * Any malformed chain falls back to [HttpServletRequest.getRemoteAddr], which cannot be forged.
 *
 * Header and socket address are always read on the request as the container received it, beneath any
 * filter wrapper — see [containerRequest] for why `ForwardedHeaderFilter` would otherwise defeat all of
 * the above.
 */
@Component
class ClientIpResolver(
    @Value("\${app.security.trusted-proxy-count:1}") private val trustedProxyCount: Int,
) {

    /**
     * Returns the address to key rate limiting on.
     *
     * @param request Incoming request.
     * @return A syntactically plausible IP literal, or the socket address when the forwarded chain
     *   is absent, too short, or not an IP literal.
     */
    fun resolve(request: HttpServletRequest): String {
        val original = containerRequest(request)
        if (trustedProxyCount <= 0) return original.remoteAddr

        val chain = original.getHeader(FORWARDED_FOR)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: return original.remoteAddr

        // Index of the entry written by the outermost proxy we trust. Negative when the client sent
        // fewer entries than expected — i.e. the header cannot be trusted to hold the client address.
        val index = chain.size - trustedProxyCount
        val candidate = chain.getOrNull(index) ?: return original.remoteAddr

        return if (isIpLiteral(candidate)) candidate else original.remoteAddr
    }

    /**
     * Returns the request as the servlet container received it, beneath every filter wrapper.
     *
     * On the deployed profiles `server.forward-headers-strategy: framework` installs Spring's
     * `ForwardedHeaderFilter` first in the chain. It wraps the request so that `X-Forwarded-For` is no
     * longer visible and `getRemoteAddr()` returns the header's *first* entry — the client-controlled
     * one. Reading the wrapper would therefore hand the rate limiter a forgeable identity. The container's
     * own request still carries the raw header and the TCP peer address, which is what this resolver's
     * rightmost-trusted rule needs. `X-Forwarded-Proto` handling (and thus HSTS) is left to the filter.
     *
     * Only holds while forwarded headers are processed by a wrapping filter: a container-level rewrite
     * (`forward-headers-strategy: native`) would alter the original request itself.
     */
    private fun containerRequest(request: HttpServletRequest): HttpServletRequest {
        var current: ServletRequest = request
        while (current is ServletRequestWrapper) current = current.request
        return current as? HttpServletRequest ?: request
    }

    /**
     * Cheap syntactic check on an IPv4/IPv6 literal.
     *
     * Not a validator — its job is to keep arbitrary caller-supplied strings from becoming
     * rate-limiter map keys, which would turn the limiter itself into a memory-growth vector.
     */
    private fun isIpLiteral(value: String): Boolean =
        value.length in 3..MAX_IP_LENGTH && value.all { it.isDigit() || it in "abcdefABCDEF.:" }

    private companion object {
        const val FORWARDED_FOR = "X-Forwarded-For"

        /** An IPv6 literal with a zone index stays well under this. */
        const val MAX_IP_LENGTH = 45
    }
}

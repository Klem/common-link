package org.commonlink.security

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
        if (trustedProxyCount <= 0) return request.remoteAddr

        val chain = request.getHeader(FORWARDED_FOR)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: return request.remoteAddr

        // Index of the entry written by the outermost proxy we trust. Negative when the client sent
        // fewer entries than expected — i.e. the header cannot be trusted to hold the client address.
        val index = chain.size - trustedProxyCount
        val candidate = chain.getOrNull(index) ?: return request.remoteAddr

        return if (isIpLiteral(candidate)) candidate else request.remoteAddr
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

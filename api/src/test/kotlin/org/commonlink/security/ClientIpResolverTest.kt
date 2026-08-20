package org.commonlink.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

/**
 * Covers the rate-limiter identity resolution (security audit 2026-08-20, M2).
 *
 * The property under test: nothing a caller writes into `X-Forwarded-For` can change the bucket its
 * requests land in.
 */
class ClientIpResolverTest {

    private val behindOneProxy = ClientIpResolver(trustedProxyCount = 1)
    private val direct = ClientIpResolver(trustedProxyCount = 0)

    private fun request(forwardedFor: String? = null, remoteAddr: String = "10.0.0.1") =
        MockHttpServletRequest().apply {
            this.remoteAddr = remoteAddr
            forwardedFor?.let { addHeader("X-Forwarded-For", it) }
        }

    @Test
    fun `takes the entry appended by the trusted proxy, not the one the client sent`() {
        // The client forged the left-hand entry; the proxy appended the address it actually saw.
        val resolved = behindOneProxy.resolve(request(forwardedFor = "1.2.3.4, 203.0.113.7"))

        assertEquals("203.0.113.7", resolved)
    }

    @Test
    fun `a forged chain cannot produce a fresh identity per request`() {
        val first = behindOneProxy.resolve(request(forwardedFor = "9.9.9.1, 203.0.113.7"))
        val second = behindOneProxy.resolve(request(forwardedFor = "9.9.9.2, 203.0.113.7"))
        val third = behindOneProxy.resolve(request(forwardedFor = "a, b, c, 203.0.113.7"))

        assertEquals(first, second, "Varying the forged prefix must not change the bucket")
        assertEquals(first, third, "Padding the chain must not change the bucket")
    }

    @Test
    fun `falls back to the socket address when the header is absent`() {
        assertEquals("10.0.0.1", behindOneProxy.resolve(request()))
    }

    @Test
    fun `falls back to the socket address when the chain is too short to be trustworthy`() {
        // A single entry means nothing was appended by a proxy — it can only be the client's own.
        assertEquals("10.0.0.1", behindOneProxy.resolve(request(forwardedFor = "")))
    }

    @Test
    fun `falls back to the socket address when the selected entry is not an IP literal`() {
        assertEquals("10.0.0.1", behindOneProxy.resolve(request(forwardedFor = "1.2.3.4, <script>")))
    }

    @Test
    fun `ignores the header entirely when no proxy is trusted`() {
        assertEquals("10.0.0.1", direct.resolve(request(forwardedFor = "1.2.3.4, 203.0.113.7")))
    }

    @Test
    fun `resolves an IPv6 literal`() {
        assertEquals("2001:db8::1", behindOneProxy.resolve(request(forwardedFor = "1.2.3.4, 2001:db8::1")))
    }
}

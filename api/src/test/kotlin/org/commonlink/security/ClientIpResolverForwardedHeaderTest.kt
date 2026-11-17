package org.commonlink.security

import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.filter.ForwardedHeaderFilter

/**
 * [ClientIpResolver] behind the real [ForwardedHeaderFilter], as installed on the deployed profiles by
 * `server.forward-headers-strategy: framework`.
 *
 * That filter hides `X-Forwarded-For` from the request it hands down and replaces the remote address with
 * the header's *first* entry — the one the client controls. [ClientIpResolverTest] cannot see this: it
 * builds the request by hand, without the filter.
 */
class ClientIpResolverForwardedHeaderTest {

    /** Echoes what the application sees: the resolved client IP and whether the request is secure. */
    @RestController
    class EchoController(private val resolver: ClientIpResolver) {
        @GetMapping("/ip")
        fun ip(request: HttpServletRequest): String = resolver.resolve(request)

        @GetMapping("/secure")
        fun secure(request: HttpServletRequest): String = request.isSecure.toString()
    }

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(EchoController(ClientIpResolver(trustedProxyCount = 1)))
        .addFilters<StandaloneMockMvcBuilder>(ForwardedHeaderFilter())
        .build()

    private fun fromProxy(path: String, vararg headers: Pair<String, String>) =
        get(path).with { it.remoteAddr = PROXY_ADDRESS; it }.apply {
            headers.forEach { (name, value) -> header(name, value) }
        }

    @Test
    fun `a forged left-hand entry does not become the client IP`() {
        mockMvc.perform(fromProxy("/ip", "X-Forwarded-For" to "6.6.6.6, 203.0.113.7"))
            .andExpect(content().string("203.0.113.7"))
    }

    @Test
    fun `an unusable chain falls back to the proxy socket address, not the forged value`() {
        // The entry at the trusted position is not an IP literal: the resolver must fall back to the
        // address of the TCP peer, never to whatever the filter derived from the header.
        mockMvc.perform(fromProxy("/ip", "X-Forwarded-For" to "6.6.6.6, not-an-ip"))
            .andExpect(content().string(PROXY_ADDRESS))
    }

    @Test
    fun `X-Forwarded-Proto is still honoured, so HSTS keeps being emitted behind the proxy`() {
        mockMvc.perform(fromProxy("/secure", "X-Forwarded-Proto" to "https"))
            .andExpect(content().string("true"))
    }

    private companion object {
        const val PROXY_ADDRESS = "10.0.0.1"
    }
}

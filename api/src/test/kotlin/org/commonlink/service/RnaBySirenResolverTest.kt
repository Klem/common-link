package org.commonlink.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

/** [RnaBySirenResolver]: RNA read under `complements`, exact SIREN only, never throws. */
class RnaBySirenResolverTest {

    private val resolver = RnaBySirenResolver(ObjectMapper(), "https://registry.test")
    private lateinit var server: MockRestServiceServer
    private val url = "https://registry.test/search?q=402328694&per_page=10"

    @BeforeEach
    fun setUp() {
        server = MockRestServiceServer.bindTo(resolver.restTemplate).build()
    }

    private fun respond(body: String) {
        server.expect(requestTo(url)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `returns the RNA held under complements, as the real API sends it`() {
        respond("""{"results":[{"siren":"402328694","complements":{"identifiant_association":"W061006975","est_association":true}}]}""")

        assertEquals("W061006975", resolver.resolve("402328694"))
    }

    @Test
    fun `skips a fuzzy top hit with another SIREN`() {
        respond(
            """{"results":[{"siren":"999999999","complements":{"identifiant_association":"W999999999"}},""" +
                """{"siren":"402328694","complements":{"identifiant_association":"w061006975"}}]}"""
        )

        assertEquals("W061006975", resolver.resolve("402328694"))
    }

    @Test
    fun `null when the entity has no RNA, or a value not shaped like one`() {
        respond("""{"results":[{"siren":"402328694","complements":{"est_association":true}}]}""")
        assertNull(resolver.resolve("402328694"))

        server.reset()
        respond("""{"results":[{"siren":"402328694","complements":{"identifiant_association":"ASS123"}}]}""")
        assertNull(resolver.resolve("402328694"))
    }

    @Test
    fun `null when no entry carries the SIREN`() {
        respond("""{"results":[]}""")

        assertNull(resolver.resolve("402328694"))
    }

    @Test
    fun `null, never an exception, when the registry fails or answers garbage`() {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        assertNull(resolver.resolve("402328694"))

        server.reset()
        respond("not json")
        assertNull(resolver.resolve("402328694"))
    }
}

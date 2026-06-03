package org.commonlink.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.commonlink.config.MoneriumConfig
import org.commonlink.dto.monerium.LinkAddressRequest
import org.commonlink.dto.monerium.MoneriumAddressDto
import org.commonlink.dto.monerium.MoneriumIbanDto
import org.commonlink.dto.monerium.MoneriumProfileDto
import org.commonlink.dto.monerium.MoneriumProfileListDto
import org.commonlink.dto.monerium.RequestIbanRequest
import org.commonlink.exception.MoneriumReauthRequiredException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.util.UUID

class MoneriumApiClientTest {

    private val config = MoneriumConfig(
        clientId = "test-client-id",
        baseUrl = "https://api.monerium.dev",
        redirectUri = "http://localhost:8080/api/monerium/callback",
    )

    private val moneriumService: MoneriumService = mockk()
    private val restTemplate: RestTemplate = mockk()
    private val client = MoneriumApiClient(moneriumService, config, restTemplate)

    private val associationId = UUID.fromString("00000000-0000-0000-0000-000000000bbb")

    @Test
    fun `listProfiles - returns profiles from envelope and sets bearer + accept headers`() {
        every { moneriumService.getValidAccessToken(associationId) } returns "access-token-abc"
        val captured = slot<HttpEntity<*>>()
        every {
            restTemplate.exchange(
                eq("https://api.monerium.dev/profiles"),
                eq(HttpMethod.GET),
                capture(captured),
                eq(MoneriumProfileListDto::class.java),
            )
        } returns ResponseEntity.ok(
            MoneriumProfileListDto(
                profiles = listOf(MoneriumProfileDto(id = "p1", kind = "corporate", name = "Asso", state = "approved"))
            )
        )

        val result = client.listProfiles(associationId)

        assertEquals(1, result.size)
        assertEquals("p1", result[0].id)
        val headers = captured.captured.headers
        assertEquals("Bearer access-token-abc", headers.getFirst("Authorization"))
        assertEquals("application/vnd.monerium.api-v2+json", headers.getFirst("Accept"))
    }

    @Test
    fun `getProfile - calls correct path`() {
        every { moneriumService.getValidAccessToken(associationId) } returns "tok"
        every {
            restTemplate.exchange(
                eq("https://api.monerium.dev/profiles/profile-xyz"),
                eq(HttpMethod.GET),
                any<HttpEntity<*>>(),
                eq(MoneriumProfileDto::class.java),
            )
        } returns ResponseEntity.ok(MoneriumProfileDto(id = "profile-xyz", kind = "corporate", name = null, state = null))

        val result = client.getProfile(associationId, "profile-xyz")

        assertEquals("profile-xyz", result.id)
    }

    @Test
    fun `linkAddress - POSTs body to addresses endpoint`() {
        every { moneriumService.getValidAccessToken(associationId) } returns "tok"
        val req = LinkAddressRequest(
            profileId = "p1",
            address = "0xabc",
            chain = "gnosis",
            message = "I hereby...",
            signature = "0xdeadbeef",
        )
        every {
            restTemplate.exchange(
                eq("https://api.monerium.dev/addresses"),
                eq(HttpMethod.POST),
                any<HttpEntity<*>>(),
                eq(MoneriumAddressDto::class.java),
            )
        } returns ResponseEntity.ok(MoneriumAddressDto(id = "a1", profile = "p1", address = "0xabc", chain = "gnosis"))

        val result = client.linkAddress(associationId, req)

        assertEquals("0xabc", result.address)
    }

    @Test
    fun `requestIban - POSTs body to ibans endpoint`() {
        every { moneriumService.getValidAccessToken(associationId) } returns "tok"
        every {
            restTemplate.exchange(
                eq("https://api.monerium.dev/ibans"),
                eq(HttpMethod.POST),
                any<HttpEntity<*>>(),
                eq(MoneriumIbanDto::class.java),
            )
        } returns ResponseEntity.ok(
            MoneriumIbanDto(iban = "EE12...", bic = null, profile = "p1", address = "0xabc", chain = "gnosis", state = "requested")
        )

        val result = client.requestIban(associationId, RequestIbanRequest(address = "0xabc", chain = "gnosis"))

        assertEquals("EE12...", result.iban)
    }

    @Test
    fun `401 triggers forceRefresh + one retry, second 401 propagates`() {
        every { moneriumService.getValidAccessToken(associationId) } returns "tok-stale"
        every { moneriumService.forceRefreshAccessToken(associationId) } returns "tok-fresh"
        every {
            restTemplate.exchange(
                any<String>(), eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(MoneriumProfileListDto::class.java),
            )
        } throws HttpClientErrorException(HttpStatus.UNAUTHORIZED)

        assertThrows<HttpClientErrorException> {
            client.listProfiles(associationId)
        }

        verify(exactly = 1) { moneriumService.getValidAccessToken(associationId) }
        verify(exactly = 1) { moneriumService.forceRefreshAccessToken(associationId) }
        verify(exactly = 2) {
            restTemplate.exchange(any<String>(), eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(MoneriumProfileListDto::class.java))
        }
    }

    @Test
    fun `401 then 200 succeeds after refresh`() {
        every { moneriumService.getValidAccessToken(associationId) } returns "tok-stale"
        every { moneriumService.forceRefreshAccessToken(associationId) } returns "tok-fresh"
        every {
            restTemplate.exchange(
                any<String>(), eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(MoneriumProfileListDto::class.java),
            )
        } throws HttpClientErrorException(HttpStatus.UNAUTHORIZED) andThen
            ResponseEntity.ok(MoneriumProfileListDto(profiles = emptyList()))

        val result = client.listProfiles(associationId)

        assertEquals(0, result.size)
    }

    @Test
    fun `reauth required bubbles up from getValidAccessToken`() {
        every { moneriumService.getValidAccessToken(associationId) } throws MoneriumReauthRequiredException()

        assertThrows<MoneriumReauthRequiredException> {
            client.listProfiles(associationId)
        }
    }
}

private inline fun <reified T : Any> slot() = io.mockk.slot<T>()

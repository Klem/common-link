package org.commonlink.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HelloAssoRedirectUrlGuardTest {

    private fun guard(redirectBaseUrl: String, mock: Boolean) =
        HelloAssoRedirectUrlGuard(HelloAssoProperties(redirectBaseUrl = redirectBaseUrl, mock = mock))

    @Test
    fun `refuses an http return URL with the real HelloAsso`() {
        val ex = assertThrows(IllegalStateException::class.java) { guard("http://localhost:3000", mock = false).check() }
        assert(ex.message!!.contains("HELLOASSO_REDIRECT_BASE_URL"))
    }

    @Test
    fun `refuses an unresolved placeholder with the real HelloAsso`() {
        assertThrows(IllegalStateException::class.java) {
            guard("\${HELLOASSO_REDIRECT_BASE_URL}", mock = false).check()
        }
    }

    @Test
    fun `accepts an https return URL, localhost included`() {
        assertDoesNotThrow { guard("https://localhost:3000", mock = false).check() }
        assertDoesNotThrow { guard("https://app.common-link.org", mock = false).check() }
    }

    @Test
    fun `accepts any URL in mock mode`() {
        assertDoesNotThrow { guard("http://localhost:3000", mock = true).check() }
    }
}

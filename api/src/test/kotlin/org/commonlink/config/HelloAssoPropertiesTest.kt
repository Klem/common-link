package org.commonlink.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.io.ClassPathResource
import java.time.Duration

/**
 * Binding of [HelloAssoProperties]: a context with no `app.helloasso` value starts on safe sandbox defaults, while the
 * base `application.yml` gives no default to the URLs and IPs (decision 2026-11-05): each environment sets them.
 */
class HelloAssoPropertiesTest {

    @EnableConfigurationProperties(HelloAssoProperties::class)
    private class Config

    private fun assertSandboxDefaults(props: HelloAssoProperties) {
        assertEquals("https://api.helloasso-sandbox.com/v5", props.apiBaseUrl)
        assertEquals("https://api.helloasso-sandbox.com/oauth2/token", props.oauthTokenUrl)
        assertEquals("https://localhost:3000", props.redirectBaseUrl)
        assertEquals("", props.webhookUrl)
        assertEquals(listOf("4.233.135.234"), props.webhookAllowedIps)
        assertFalse(props.mock)
        assertEquals(Duration.ofDays(7), props.tokenRefreshInterval)
        assertEquals(Duration.ofHours(2), props.eligibilityPendingInterval)
        assertEquals(Duration.ofDays(1), props.eligibilityReadyInterval)
        assertEquals(Duration.ofMinutes(15), props.reconcilerStaleAfter)
        assertEquals(Duration.ofMinutes(45), props.checkoutAbandonAfter)
        assertEquals(Duration.ofMinutes(1), props.eligibilityRefreshRateLimit)
    }

    @Test
    fun `a context without any helloasso value starts on the sandbox defaults`() {
        ApplicationContextRunner()
            .withUserConfiguration(Config::class.java)
            .run { context -> assertSandboxDefaults(context.getBean(HelloAssoProperties::class.java)) }
    }

    @Test
    fun `the base application yml requires the URLs and IPs, with no default`() {
        val source = YamlPropertySourceLoader().load("base", ClassPathResource("application.yml")).single()
        fun prop(key: String) = source.getProperty("app.helloasso.$key").toString()

        assertEquals("\${HELLOASSO_API_BASE_URL}", prop("api-base-url"))
        assertEquals("\${HELLOASSO_OAUTH_TOKEN_URL}", prop("oauth-token-url"))
        assertEquals("\${HELLOASSO_REDIRECT_BASE_URL}", prop("redirect-base-url"))
        assertEquals("\${HELLOASSO_WEBHOOK_ALLOWED_IPS}", prop("webhook-allowed-ips"))
        assertEquals("\${HELLOASSO_MOCK:false}", prop("mock"))
    }
}

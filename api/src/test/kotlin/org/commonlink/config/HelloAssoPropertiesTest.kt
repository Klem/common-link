package org.commonlink.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import java.time.Duration

/**
 * Binding of [HelloAssoProperties]: a context with no `app.helloasso` value starts on safe sandbox defaults, and the
 * base `application.yml` binds to the same values when no environment variable is set.
 */
class HelloAssoPropertiesTest {

    @EnableConfigurationProperties(HelloAssoProperties::class)
    private class Config

    private fun assertSandboxDefaults(props: HelloAssoProperties) {
        assertEquals("https://api.helloasso-sandbox.com/v5", props.apiBaseUrl)
        assertEquals("https://api.helloasso-sandbox.com/oauth2/token", props.oauthTokenUrl)
        assertEquals(listOf("4.233.135.234"), props.allowedNotificationIps)
        assertFalse(props.mock)
        assertEquals("", props.notificationBaseUrl)
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
    fun `the base application yml binds to the sandbox defaults when no variable is set`() {
        val sources = YamlPropertySourceLoader().load("base", ClassPathResource("application.yml"))
        ApplicationContextRunner()
            .withInitializer { ctx -> sources.forEach { ctx.environment.propertySources.addLast(it) } }
            .withUserConfiguration(Config::class.java)
            .run { context -> assertSandboxDefaults(context.getBean(HelloAssoProperties::class.java)) }
    }
}

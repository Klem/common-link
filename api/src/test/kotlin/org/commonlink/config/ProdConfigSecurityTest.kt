package org.commonlink.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.ClassPathResource
import java.time.Duration

class ProdConfigSecurityTest {

    private val effectiveProps: Map<String, Any?> by lazy {
        val loader = YamlPropertySourceLoader()
        val merged = mutableMapOf<String, Any?>()

        fun load(name: String, path: String) {
            loader.load(name, ClassPathResource(path))
                .filterIsInstance<EnumerablePropertySource<*>>()
                .forEach { source ->
                    source.propertyNames.forEach { key -> merged[key] = source.getProperty(key) }
                }
        }

        load("base", "application.yml")
        load("prod", "application-prod.yml")
        merged
    }

    private fun prop(key: String): Any? = effectiveProps[key]

    @Test
    fun `vop demo-mode is false in prod`() {
        assertEquals(false, prop("app.vop.demo-mode"))
    }

    @Test
    fun `monerium skip-kyc is false in prod`() {
        assertEquals(false, prop("app.monerium.skip-kyc"))
    }

    @Test
    fun `springdoc api-docs disabled in prod`() {
        assertEquals(false, prop("springdoc.api-docs.enabled"))
    }

    @Test
    fun `springdoc swagger-ui disabled in prod`() {
        assertEquals(false, prop("springdoc.swagger-ui.enabled"))
    }

    @Test
    fun `ddl-auto is validate in prod`() {
        assertEquals("validate", prop("spring.jpa.hibernate.ddl-auto"))
    }

    @Test
    fun `show-sql is false in prod`() {
        assertEquals(false, prop("spring.jpa.show-sql"))
    }

    @Test
    fun `commonlink log level is not DEBUG in prod`() {
        assertNotEquals("DEBUG", prop("logging.level.org.commonlink"))
    }

    @Test
    fun `jwt secret has no insecure dev default`() {
        // TODO(security-sprint): base exposes insecure literal fallback via COMMON_LINK_SECRET; prod must supply secret via env only (no default)
        val secret = prop("app.jwt.secret") as? String ?: ""
        assertFalse(secret.contains("commonlink-dev-secret-key"))
    }

    @Disabled("onchain.mock intentionally stays true in prod until the blockchain component " +
        "is actually deployed — re-enable this test once it goes live.")
    @Test
    fun `onchain mock is disabled in prod`() {
        val mock = prop("onchain.mock")
        assertTrue(mock == null || mock == false,
            "onchain.mock must be absent or false in prod, was: $mock")
    }

    @Test
    fun `mollie allow-fake-completion is false in prod`() {
        // C1: base defaults this true; prod must override it explicitly or the self-service
        // fake-KYC route ships active in production.
        assertEquals(false, prop("app.mollie.connect.allow-fake-completion"))
    }

    @Test
    fun `mollie connect mock is false in prod`() {
        assertEquals(false, prop("app.mollie.connect.mock"))
    }

    @Test
    fun `curator email is required with no empty default in prod`() {
        assertEquals("\${APP_CURATOR_EMAIL}", prop("app.curator.email"))
    }

    @Test
    fun `curator password is required with no empty default in prod`() {
        assertEquals("\${APP_CURATOR_PASSWORD}", prop("app.curator.password"))
    }

    @Test
    fun `compliance officer email is required with no empty default in prod`() {
        assertEquals("\${APP_COMPLIANCE_OFFICER_EMAIL}", prop("app.compliance-officer.email"))
    }

    @Test
    fun `compliance officer password is required with no empty default in prod`() {
        assertEquals("\${APP_COMPLIANCE_OFFICER_PASSWORD}", prop("app.compliance-officer.password"))
    }

    @Test
    fun `mollie test-mode is false in prod`() {
        // T1: base defaults test-mode true for local Connect sandbox; prod must pin false or live donations route to Mollie test mode
        assertEquals(false, prop("app.mollie.test-mode"))
    }

    @Test
    fun `mollie api-key does not inherit the committed test key in prod`() {
        // M1: base hardcodes a Mollie test key as the default; prod must require MOLLIE_API_KEY.
        val apiKey = prop("app.mollie.api-key") as? String ?: ""
        assertFalse(apiKey.contains("test_"), "prod must not inherit the base test key, was: $apiKey")
        assertEquals("\${MOLLIE_API_KEY}", apiKey)
    }

    @Test
    fun `monerium token-enc-key is required with no plaintext-inheriting default in prod`() {
        // H4: base/staging default this empty (→ plaintext tokens); prod must require the key.
        assertEquals("\${MONERIUM_TOKEN_ENC_KEY}", prop("app.monerium.token-enc-key"))
    }

    @Test
    fun `access-token-expiration is at most 1 hour in prod`() {
        val raw = prop("app.jwt.access-token-expiration") as? String ?: ""
        val duration = Duration.parse(raw)
        assertTrue(duration <= Duration.ofHours(1),
            "app.jwt.access-token-expiration must be ≤ PT1H in prod, was: $raw")
    }
}

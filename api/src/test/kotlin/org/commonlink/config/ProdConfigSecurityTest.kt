package org.commonlink.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.ClassPathResource

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

    @Disabled("security-sprint prompt 3: springdoc hardening pending")
    @Test
    fun `springdoc api-docs disabled in prod`() {
        assertEquals(false, prop("springdoc.api-docs.enabled"))
    }

    @Disabled("security-sprint prompt 3: springdoc hardening pending")
    @Test
    fun `springdoc swagger-ui disabled in prod`() {
        assertEquals(false, prop("springdoc.swagger-ui.enabled"))
    }

    @Disabled("security-sprint prompt 3: ddl-auto hardening pending")
    @Test
    fun `ddl-auto is validate in prod`() {
        assertEquals("validate", prop("spring.jpa.hibernate.ddl-auto"))
    }

    @Disabled("security-sprint prompt 3: show-sql hardening pending")
    @Test
    fun `show-sql is false in prod`() {
        assertEquals(false, prop("spring.jpa.show-sql"))
    }

    @Disabled("security-sprint prompt 3: log level hardening pending")
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
}

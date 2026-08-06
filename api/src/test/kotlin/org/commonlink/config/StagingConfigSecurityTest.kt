package org.commonlink.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.ClassPathResource

class StagingConfigSecurityTest {

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
        load("staging", "application-staging.yml")
        merged
    }

    private fun prop(key: String): Any? = effectiveProps[key]

    @Test
    fun `curator email is required with no empty default in staging`() {
        assertEquals("\${APP_CURATOR_EMAIL}", prop("app.curator.email"))
    }

    @Test
    fun `curator password is required with no empty default in staging`() {
        assertEquals("\${APP_CURATOR_PASSWORD}", prop("app.curator.password"))
    }

    @Test
    fun `compliance officer email is required with no empty default in staging`() {
        assertEquals("\${APP_COMPLIANCE_OFFICER_EMAIL}", prop("app.compliance-officer.email"))
    }

    @Test
    fun `compliance officer password is required with no empty default in staging`() {
        assertEquals("\${APP_COMPLIANCE_OFFICER_PASSWORD}", prop("app.compliance-officer.password"))
    }
}

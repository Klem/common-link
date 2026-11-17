package org.commonlink.config

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Refuses to start when the real HelloAsso is used (`app.helloasso.mock=false`) with a non-HTTPS
 * `app.helloasso.redirect-base-url`: HelloAsso refuses every checkout whose return URLs are not HTTPS (HTTP 400,
 * observed 2026-11-05), so every HelloAsso donation would fail at runtime. `https://localhost:3000` is accepted.
 * Mock mode makes no HelloAsso call, so any URL is fine there.
 */
@Component
class HelloAssoRedirectUrlGuard(private val properties: HelloAssoProperties) {
    @PostConstruct
    fun check() {
        check(properties.mock || properties.redirectBaseUrl.trim().startsWith("https://", ignoreCase = true)) {
            "app.helloasso.redirect-base-url (HELLOASSO_REDIRECT_BASE_URL) must be an https URL: " +
                "HelloAsso refuses non-HTTPS return URLs"
        }
    }
}

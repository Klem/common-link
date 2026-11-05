package org.commonlink.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

/**
 * Live probe of the HelloAsso **sandbox**: captures the full body of a 400 on `POST checkout-intents`, a format
 * dev.helloasso.com does not document. Skipped unless `HELLOASSO_SANDBOX_ACCESS_TOKEN` and `HELLOASSO_SANDBOX_SLUG`
 * are set, so it never runs in the suite.
 *
 * The request is invalid on purpose (zero amount, non-HTTPS return URLs) so that no checkout-intent is ever created.
 * The body sent holds no real data; the access token is never logged.
 */
@EnabledIfEnvironmentVariable(named = "HELLOASSO_SANDBOX_ACCESS_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HELLOASSO_SANDBOX_SLUG", matches = ".+")
class HelloAssoSandboxErrorFormatProbeTest {

    private val log = LoggerFactory.getLogger(javaClass)

    @Test
    fun `logs the full body of a refused checkout-intent`() {
        val token = System.getenv("HELLOASSO_SANDBOX_ACCESS_TOKEN")
        val slug = System.getenv("HELLOASSO_SANDBOX_SLUG")
        val body = """
            {"totalAmount":0,"initialAmount":0,"itemName":"probe",
             "backUrl":"http://localhost:3000/back","errorUrl":"http://localhost:3000/error",
             "returnUrl":"http://localhost:3000/return","containsDonation":true}
        """.trimIndent()

        val ex = try {
            RestClient.create().post()
                .uri("https://api.helloasso-sandbox.com/v5/organizations/$slug/checkout-intents")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity()
            error("HelloAsso accepted an invalid checkout-intent")
        } catch (ex: RestClientResponseException) {
            ex
        }

        log.info("HelloAsso status: {}", ex.statusCode.value())
        log.info("HelloAsso content-type: {}", ex.responseHeaders?.contentType)
        log.info("HelloAsso body: {}", ex.responseBodyAsString)
        assertEquals(400, ex.statusCode.value())
    }
}

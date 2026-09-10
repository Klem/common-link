package org.commonlink.service

import org.commonlink.config.BridgeProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Covers [BridgeWebhookSignatureVerifier] against Bridge's documented format: header
 * `BridgeApi-Signature: v1=<hex>[,v1=<hex>]`, HMAC-SHA256 over the raw body, hex uppercase.
 */
class BridgeWebhookSignatureVerifierTest {

    private fun sign(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    private fun verifier(secret: String = "current-secret", previous: String = "") =
        BridgeWebhookSignatureVerifier(
            BridgeProperties(webhookSecret = secret, webhookSecretPrevious = previous)
        )

    @Test
    fun `accepts a valid v1 signature over the raw body`() {
        val body = """{"type":"payment.link.updated","payment_link_id":"pl_1"}"""
        val header = "v1=${sign(body, "current-secret")}"

        assertTrue(verifier().isValid(body, header))
    }

    @Test
    fun `rejects a signature computed with the wrong secret`() {
        val body = """{"type":"payment.link.updated"}"""
        val header = "v1=${sign(body, "wrong-secret")}"

        assertFalse(verifier().isValid(body, header))
    }

    @Test
    fun `rejects a body that does not match what was signed`() {
        val header = "v1=${sign("original-body", "current-secret")}"

        assertFalse(verifier().isValid("tampered-body", header))
    }

    @Test
    fun `rejects a missing header when a secret is configured`() {
        assertFalse(verifier().isValid("{}", null))
    }

    @Test
    fun `rejects a header carrying only a non-v1 scheme`() {
        val body = "{}"
        assertFalse(verifier().isValid(body, "v2=${sign(body, "current-secret")}"))
    }

    @Test
    fun `accepts the previous secret during a 24h rotation window`() {
        val body = """{"type":"payment.transaction.updated"}"""
        val header = "v1=${sign(body, "old-secret")}"

        assertTrue(verifier(secret = "new-secret", previous = "old-secret").isValid(body, header))
    }

    @Test
    fun `accepts when one of several comma-separated v1 values matches`() {
        val body = "{}"
        val header = "v1=${sign(body, "old-secret")},v1=${sign(body, "current-secret")}"

        assertTrue(verifier().isValid(body, header))
    }

    @Test
    fun `skips verification and accepts when no secret is configured yet`() {
        // Optional bootstrap, same rationale as curator/compliance-officer: the endpoint must keep
        // working before the webhook is created on Bridge's dashboard and its secret retrieved.
        assertTrue(verifier(secret = "").isValid("{}", null))
        assertTrue(verifier(secret = "").isValid("{}", "v1=garbage"))
    }
}

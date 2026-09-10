package org.commonlink.service

import org.commonlink.config.BridgeProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies the `BridgeApi-Signature` header Bridge attaches to every webhook call.
 *
 * Format: one or more comma-separated `scheme=value` pairs, e.g. `v1=A,v1=B` — a second `v1`
 * value appears for up to 24h after a secret rotation, signed with the outgoing secret. Only the
 * `v1` scheme (HMAC-SHA256, hex, uppercase, over the raw request body) is accepted; any other
 * scheme is a downgrade attempt and is ignored.
 *
 * Verification is intentionally permissive when no secret is configured yet
 * ([BridgeProperties.webhookSecret] blank): this mirrors the project's other optional bootstraps
 * (curator, compliance-officer) so the endpoint keeps working — without this defence-in-depth
 * layer — before the webhook is created on Bridge's dashboard and its secret retrieved. It does
 * **not** widen what a forged call can do: [BridgeWebhookService] never trusts the body regardless
 * of signature outcome, re-reading the authoritative state from Bridge itself.
 */
@Service
class BridgeWebhookSignatureVerifier(
    private val props: BridgeProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * @param rawBody The exact bytes Bridge sent, unmodified — signing covers the raw body, so
     *   re-serialising a parsed object would not reproduce it.
     * @param signatureHeader Value of the `BridgeApi-Signature` header, or null if absent.
     * @return true if signature checking is disabled (no secret configured) or at least one
     *   `v1` value matches a configured secret; false otherwise.
     */
    fun isValid(rawBody: String, signatureHeader: String?): Boolean {
        if (props.webhookSecret.isBlank()) {
            log.warn("app.bridge.webhook-secret is not configured — accepting Bridge webhook without signature verification")
            return true
        }

        if (signatureHeader.isNullOrBlank()) {
            log.warn("Bridge webhook rejected: missing BridgeApi-Signature header")
            return false
        }

        val presented = signatureHeader.split(",")
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2 && parts[0].trim() == SCHEME) parts[1].trim() else null
            }

        if (presented.isEmpty()) {
            log.warn("Bridge webhook rejected: BridgeApi-Signature carries no {} value", SCHEME)
            return false
        }

        val secrets = listOfNotNull(
            props.webhookSecret.takeIf { it.isNotBlank() },
            props.webhookSecretPrevious.takeIf { it.isNotBlank() },
        )
        val expected = secrets.map { sign(rawBody, it) }

        val ok = presented.any { candidate -> expected.any { constantTimeEquals(it, candidate) } }
        if (!ok) {
            log.warn("Bridge webhook rejected: no BridgeApi-Signature value matches a configured secret")
        }
        return ok
    }

    private fun sign(rawBody: String, secret: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM))
        return mac.doFinal(rawBody.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    private companion object {
        const val SCHEME = "v1"
        const val ALGORITHM = "HmacSHA256"
    }
}

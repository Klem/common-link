package org.commonlink.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.commonlink.entity.VopResult
import org.commonlink.exception.BadGatewayException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Result of a single VOP (Verification of Payee) check.
 *
 * @param result The raw VOP outcome mapped to [VopResult].
 * @param suggestedName Account holder name suggested by the bank (only for [VopResult.CLOSE_MATCH]).
 * @param rawResponse Full raw JSON response from the VOP service, or a JSON description of the
 *   simulation result in demo mode. Stored for audit purposes.
 */
data class VopVerificationResult(
    val result: VopResult,
    val suggestedName: String?,
    val rawResponse: String?
)

/**
 * Service responsible for verifying IBANs via Mollie's Verify Payee API
 * (`POST /v2/business-accounts/payee-verifications` — a Mollie Business Accounts beta feature,
 * see https://docs.mollie.com/reference/verify-payee).
 *
 * When [demoMode] is `true` (default), no real verification is performed: [verify] always
 * returns [VopResult.MATCH] with no API call, so the feature can be exercised without real bank
 * credentials or a Mollie Business Account. Format validation (mod-97, see
 * [PayeeService.addIban]) remains the only real guarantee in this mode — the VOP step itself is
 * a no-op that unconditionally passes. When [demoMode] is `false`, the real Mollie Verify Payee
 * endpoint is called.
 *
 * @param demoMode Whether to skip real verification and unconditionally return MATCH.
 * @param apiUrl Mollie Verify Payee endpoint URL.
 * @param apiToken Bearer token for the Mollie API, scoped `business-account-payee-verifications.write`
 *   (empty string in demo mode).
 * @param objectMapper Jackson mapper used to parse the real API response.
 */
@Service
class VopService(
    @Value("\${app.vop.demo-mode:false}") private val demoMode: Boolean,
    @Value("\${app.vop.api-url:https://api.mollie.com/v2/business-accounts/payee-verifications}") private val apiUrl: String,
    @Value("\${app.vop.api-token:}") private val apiToken: String,
    private val objectMapper: ObjectMapper,
    restClientBuilder: RestClient.Builder = RestClient.builder()
) {
    private val log = LoggerFactory.getLogger(VopService::class.java)
    private val restClient = restClientBuilder.build()

    init {
        if (!demoMode) {
            require(apiToken.isNotBlank()) {
                "app.vop.api-token is required when demo-mode is false — set the VOP_API_TOKEN env var"
            }
        }
    }

    /**
     * Verifies the given IBAN against the payee name using VOP.
     *
     * Returns an unconditional [VopResult.MATCH] when [demoMode] is active, otherwise calls
     * [callVerifyPayeeApi].
     *
     * @param iban The IBAN to verify (normalised, no spaces).
     * @param payeeName The expected account holder name.
     * @return [VopVerificationResult] with the outcome and optional suggested name.
     */
    fun verify(iban: String, payeeName: String): VopVerificationResult {
        return if (demoMode) {
            log.debug("VOP demo mode — skipping real verification for IBAN, returning MATCH unconditionally")
            VopVerificationResult(
                result = VopResult.MATCH,
                suggestedName = null,
                rawResponse = """{"simulation":true,"verification":"skipped"}"""
            )
        } else {
            log.debug("VOP real mode — calling Mollie Verify Payee API for IBAN {}", iban)
            callVerifyPayeeApi(iban, payeeName)
        }
    }

    /**
     * Calls Mollie's Verify Payee API to verify the IBAN against the payee name.
     *
     * Sends `POST {apiUrl}` with JSON body
     * `{"creditorBankAccount": {"accountHolderName": "...", "format": "iban", "accountNumber": "..."}}`
     * and `Authorization: Bearer {apiToken}` (an advanced access token or API key scoped
     * `business-account-payee-verifications.write`). Maps the `verificationResult.outcome`
     * field of the response to [VopResult]; any unrecognised value is treated as
     * [VopResult.NOT_POSSIBLE]. No request signing is required for this endpoint (unlike
     * Mollie's `create-transfer`, which needs `X-Client-Signature`).
     *
     * @param iban The IBAN to verify.
     * @param payeeName The expected account holder name.
     * @return [VopVerificationResult] parsed from the Mollie response.
     * @throws BadGatewayException if the API call fails or returns an error.
     */
    private fun callVerifyPayeeApi(iban: String, payeeName: String): VopVerificationResult {
        val requestBody = VerifyPayeeRequestJson(
            creditorBankAccount = CreditorBankAccountJson(
                accountHolderName = payeeName,
                accountNumber = iban
            )
        )
        val rawResponse: String = try {
            restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer $apiToken")
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String::class.java) ?: "{}"
        } catch (ex: RestClientException) {
            log.error("Mollie Verify Payee API call failed for IBAN {}: {}", iban, ex.message)
            throw BadGatewayException("VOP service unavailable: ${ex.message}")
        }

        val parsed: VerifyPayeeResponseJson? = try {
            objectMapper.readValue(rawResponse, VerifyPayeeResponseJson::class.java)
        } catch (ex: Exception) {
            log.warn("Failed to parse Mollie Verify Payee response: {}", ex.message)
            null
        }

        val outcome = parsed?.verificationResult?.outcome
        val suggestedName = parsed?.verificationResult?.accountHolderName

        val vopResult = when (outcome) {
            "match" -> VopResult.MATCH
            "close-match" -> VopResult.CLOSE_MATCH
            "no-match" -> VopResult.NO_MATCH
            else -> VopResult.NOT_POSSIBLE
        }

        return VopVerificationResult(
            result = vopResult,
            suggestedName = suggestedName,
            rawResponse = rawResponse
        )
    }
}

// ── Mollie Verify Payee wire format ──────────────────────────────────────────

private data class CreditorBankAccountJson(
    val accountHolderName: String,
    val format: String = "iban",
    val accountNumber: String
)

private data class VerifyPayeeRequestJson(
    val creditorBankAccount: CreditorBankAccountJson
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class VerificationResultJson(
    val outcome: String?,
    val accountHolderName: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class VerifyPayeeResponseJson(
    val verificationResult: VerificationResultJson?
)

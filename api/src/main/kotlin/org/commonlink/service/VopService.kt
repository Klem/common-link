package org.commonlink.service

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
 * Service responsible for verifying IBANs via the Qonto VOP (Verification of Payee) API.
 *
 * When [demoMode] is `true` (default), verification is simulated based on the last
 * alphanumeric character of the IBAN so the feature can be exercised without real bank
 * credentials. When [demoMode] is `false`, the real Qonto SEPA VOP endpoint is called.
 *
 * @param demoMode Whether to use demo simulation instead of the real API.
 * @param apiUrl Qonto VOP endpoint URL.
 * @param apiToken Bearer token for the Qonto API (empty string in demo mode).
 * @param objectMapper Jackson mapper used to parse the real API response.
 */
@Service
class VopService(
    @Value("\${app.vop.demo-mode:true}") private val demoMode: Boolean,
    @Value("\${app.vop.api-url:https://thirdparty.qonto.com/v2/sepa/verify_payee}") private val apiUrl: String,
    @Value("\${app.vop.api-token:}") private val apiToken: String,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(VopService::class.java)
    private val restClient = RestClient.create()

    /**
     * Verifies the given IBAN against the payee name using VOP.
     *
     * Routes to [simulateVop] when [demoMode] is active, otherwise calls [callQontoApi].
     *
     * @param iban The IBAN to verify (normalised, no spaces).
     * @param payeeName The expected account holder name.
     * @return [VopVerificationResult] with the outcome and optional suggested name.
     */
    fun verify(iban: String, payeeName: String): VopVerificationResult {
        return if (demoMode) {
            log.debug("VOP demo mode — simulating for IBAN ending '{}'", iban.lastOrNull())
            simulateVop(iban, payeeName)
        } else {
            log.debug("VOP real mode — calling Qonto API for IBAN {}", iban)
            callQontoApi(iban, payeeName)
        }
    }

    /**
     * Simulates a VOP check based on the last alphanumeric character of the IBAN.
     *
     * Outcome mapping (last char of stripped IBAN):
     * - `0,2,4,6,8` → [VopResult.MATCH]
     * - `1,3,5` → [VopResult.CLOSE_MATCH] — [suggestedName] = words of [payeeName] reversed
     * - `7,9` → [VopResult.NO_MATCH]
     * - anything else (e.g. letter) → [VopResult.NOT_POSSIBLE]
     *
     * A 500 ms delay simulates real network latency. This delay is present **only** in this
     * demo path; [callQontoApi] has no artificial delay.
     *
     * @param iban The IBAN to simulate against.
     * @param payeeName Payee name used to produce the reversed close-match suggestion.
     * @return Simulated [VopVerificationResult].
     */
    private fun simulateVop(iban: String, payeeName: String): VopVerificationResult {
        Thread.sleep(500)
        val stripped = iban.replace(Regex("[^A-Za-z0-9]"), "")
        val lastChar = stripped.lastOrNull()

        return when (lastChar) {
            '0', '2', '4', '6', '8' -> VopVerificationResult(
                result = VopResult.MATCH,
                suggestedName = null,
                rawResponse = """{"simulation":true,"match_result":"MATCH","last_char":"$lastChar"}"""
            )
            '1', '3', '5' -> {
                val suggested = payeeName.split(" ").reversed().joinToString(" ")
                VopVerificationResult(
                    result = VopResult.CLOSE_MATCH,
                    suggestedName = suggested,
                    rawResponse = """{"simulation":true,"match_result":"CLOSE_MATCH","last_char":"$lastChar","suggested_name":"$suggested"}"""
                )
            }
            '7', '9' -> VopVerificationResult(
                result = VopResult.NO_MATCH,
                suggestedName = null,
                rawResponse = """{"simulation":true,"match_result":"NO_MATCH","last_char":"$lastChar"}"""
            )
            else -> VopVerificationResult(
                result = VopResult.NOT_POSSIBLE,
                suggestedName = null,
                rawResponse = """{"simulation":true,"match_result":"NOT_POSSIBLE","last_char":"$lastChar"}"""
            )
        }
    }

    /**
     * Calls the real Qonto SEPA VOP API to verify the IBAN against the payee name.
     *
     * Sends `POST {apiUrl}` with JSON body `{"iban": "...", "name": "..."}` and
     * `Authorization: Bearer {apiToken}`. Maps the `match_result` field of the response
     * to [VopResult]; any unrecognised value is treated as [VopResult.NOT_POSSIBLE].
     *
     * @param iban The IBAN to verify.
     * @param payeeName The expected account holder name.
     * @return [VopVerificationResult] parsed from the Qonto response.
     * @throws BadGatewayException if the API call fails or returns an error.
     */
    @Suppress("UNCHECKED_CAST")
    private fun callQontoApi(iban: String, payeeName: String): VopVerificationResult {
        val requestBody = mapOf("iban" to iban, "name" to payeeName)
        val rawResponse: String = try {
            restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer $apiToken")
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String::class.java) ?: "{}"
        } catch (ex: RestClientException) {
            log.error("Qonto VOP API call failed for IBAN {}: {}", iban, ex.message)
            throw BadGatewayException("VOP service unavailable: ${ex.message}")
        }

        val responseMap: Map<String, Any?> = try {
            objectMapper.readValue(rawResponse, Map::class.java) as Map<String, Any?>
        } catch (ex: Exception) {
            log.warn("Failed to parse Qonto VOP response: {}", ex.message)
            emptyMap()
        }

        val matchResult = responseMap["match_result"] as? String
        val suggestedName = responseMap["suggested_name"] as? String

        val vopResult = when (matchResult?.uppercase()) {
            "MATCH" -> VopResult.MATCH
            "CLOSE_MATCH" -> VopResult.CLOSE_MATCH
            "NO_MATCH" -> VopResult.NO_MATCH
            else -> VopResult.NOT_POSSIBLE
        }

        return VopVerificationResult(
            result = vopResult,
            suggestedName = suggestedName,
            rawResponse = rawResponse
        )
    }
}

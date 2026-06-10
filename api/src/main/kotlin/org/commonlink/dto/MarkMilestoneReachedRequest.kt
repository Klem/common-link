package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request body for marking a campaign milestone as reached.
 *
 * @param proofUrl URL of the proof document; its keccak256 hash is stored on-chain.
 */
data class MarkMilestoneReachedRequest(
    @field:NotBlank
    @field:Size(max = 2048)
    val proofUrl: String,
)

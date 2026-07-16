package org.commonlink.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Public confirmation status of a donation, visible to the donor on the return page.")
enum class DonationPublicStatus {
    /** Payment initiated but not yet confirmed by the Mollie webhook. */
    PENDING,

    /** Payment confirmed — on-chain recording job enqueued. */
    CONFIRMED,
}

data class DonationStatusDto(
    @Schema(description = "Current status of the donation payment.")
    val status: DonationPublicStatus,
)

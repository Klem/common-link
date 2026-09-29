package org.commonlink.dto

import jakarta.validation.constraints.Size

/**
 * Request body for updating a donor's editable profile fields.
 *
 * All fields are optional (null = keep existing value). Only provided non-null values are applied.
 * Constraints are enforced when a field is present (non-null); null skips validation.
 *
 * The list is deliberately exhaustive: no other donor field is writable through this endpoint.
 *
 * @param firstName Civil first name of the donor (max 128 characters, matches the column).
 * @param lastName Civil last name of the donor (max 128 characters, matches the column).
 * @param displayName Public display name of the donor (max 255 characters).
 * @param anonymous Whether to hide the donor's identity on leaderboards.
 * @param notifyMonthlyReport Whether to receive the monthly impact report by email.
 * @param notifyNewPayout Whether to be notified when a supported association publishes a payout.
 * @param notifyGoalReached Whether to be notified when a supported campaign reaches its goal.
 * @param notifySuggestions Whether to accept campaign suggestions by email.
 */
data class UpdateDonorProfileRequest(

    @field:Size(max = 128, message = "First name must not exceed 128 characters")
    val firstName: String? = null,

    @field:Size(max = 128, message = "Last name must not exceed 128 characters")
    val lastName: String? = null,

    @field:Size(max = 255, message = "Display name must not exceed 255 characters")
    val displayName: String? = null,

    val anonymous: Boolean? = null,

    val notifyMonthlyReport: Boolean? = null,

    val notifyNewPayout: Boolean? = null,

    val notifyGoalReached: Boolean? = null,

    val notifySuggestions: Boolean? = null,
)

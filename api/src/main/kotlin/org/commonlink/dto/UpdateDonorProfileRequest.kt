package org.commonlink.dto

import jakarta.validation.constraints.Size

/**
 * Request body for updating a donor's editable profile fields.
 *
 * All fields are optional (null = keep existing value). Only provided non-null values are applied.
 * Constraints are enforced when a field is present (non-null); null skips validation.
 *
 * @param displayName Public display name of the donor (max 255 characters).
 * @param anonymous Whether to hide the donor's identity on leaderboards.
 */
data class UpdateDonorProfileRequest(

    @field:Size(max = 255, message = "Display name must not exceed 255 characters")
    val displayName: String?,

    val anonymous: Boolean?
)

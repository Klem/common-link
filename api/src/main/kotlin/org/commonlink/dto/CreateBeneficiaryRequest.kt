package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Request body for creating a new beneficiary under the authenticated association.
 *
 * All fields that are also validated in the frontend must be validated here as well —
 * every click can be replayed as a direct API call, so server-side validation is mandatory.
 *
 * @param name Official registered name of the beneficiary organisation.
 * @param identifier1 French SIREN identifier (exactly 9 digits).
 * @param identifier2 French SIRET identifier (exactly 14 digits). Null when only SIREN is known.
 * @param activityCode NAF/APE activity code (up to 10 characters).
 * @param category Legal or administrative category of the beneficiary.
 * @param city City where the beneficiary is headquartered.
 * @param postalCode Postal code of the beneficiary's registered address.
 * @param active Whether this beneficiary is immediately active. Defaults to true.
 */
data class CreateBeneficiaryRequest(
    @field:NotBlank
    val name: String,

    @field:NotBlank
    @field:Pattern(regexp = "^[0-9]{9}$", message = "identifier1 must be exactly 9 digits")
    val identifier1: String,

    @field:Pattern(regexp = "^[0-9]{14}$", message = "identifier2 must be exactly 14 digits")
    val identifier2: String? = null,

    @field:Size(max = 10)
    val activityCode: String? = null,

    @field:Size(max = 100)
    val category: String? = null,

    @field:Size(max = 255)
    val city: String? = null,

    @field:Size(max = 10)
    val postalCode: String? = null,

    val active: Boolean = true
)

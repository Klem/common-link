package org.commonlink.dto

import org.commonlink.entity.UserRole
import org.commonlink.validation.SelfAssignableRole
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class MagicLinkRequestDto(
    @field:Email
    @field:NotBlank
    val email: String,

    /**
     * Requested role, required for a sign-up and `null` for the login of an existing account.
     * Restricted to [UserRole.SELF_ASSIGNABLE] — back-office roles are refused.
     */
    @field:SelfAssignableRole
    val role: UserRole? = null,

    @field:Valid
    val associationProfile: AssociationProfileRequestDto? = null
)

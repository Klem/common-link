package org.commonlink.dto

import org.commonlink.entity.UserRole
import org.commonlink.validation.SelfAssignableRole
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class RegisterRequestDto(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 8)
    val password: String,

    /** Requested role. Restricted to [UserRole.SELF_ASSIGNABLE] — back-office roles are refused. */
    @field:NotNull
    @field:SelfAssignableRole
    val role: UserRole,

    @field:Valid
    val associationProfile: AssociationProfileRequestDto? = null
)

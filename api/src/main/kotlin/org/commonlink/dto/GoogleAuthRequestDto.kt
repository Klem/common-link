package org.commonlink.dto

import org.commonlink.entity.UserRole
import org.commonlink.validation.SelfAssignableRole
import jakarta.validation.constraints.NotBlank

data class GoogleAuthRequestDto(
    @field:NotBlank
    val idToken: String,

    /**
     * Requested role, required on sign-up and `null` on login.
     * Restricted to [UserRole.SELF_ASSIGNABLE] — back-office roles are refused.
     */
    @field:SelfAssignableRole
    val role: UserRole? = null
)

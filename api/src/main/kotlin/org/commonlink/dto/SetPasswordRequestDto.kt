package org.commonlink.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Body of `PATCH /api/user/me/password`.
 *
 * @property password New password.
 * @property confirmPassword Must equal [password].
 * @property currentPassword Existing password. Required when the account already has one — omitting
 *   it is refused. Left null by the flows this endpoint was built for (adding a first password after
 *   a Google or magic-link sign-up), where there is nothing to prove yet.
 */
data class SetPasswordRequestDto(
    @field:NotBlank
    @field:Size(min = 8)
    val password: String,

    @field:NotBlank
    val confirmPassword: String,

    val currentPassword: String? = null,
)

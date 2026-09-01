package org.commonlink.dto

import com.fasterxml.jackson.annotation.JsonInclude

data class AuthResponseDto(
    val accessToken: String,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val refreshToken: String?,
    val user: UserDto,
    /**
     * True exactly once: when this response is the moment a guest donor row (created by a widget
     * donation) was claimed into an ASSOCIATION account. Lets the frontend show a one-time notice
     * explaining why donor history appears under a brand-new association account, instead of the
     * claim happening silently.
     */
    val donorHistoryClaimed: Boolean = false,
    /**
     * True when this response comes from verifying a "forgot password" link: the frontend must
     * send the caller straight to the set-password screen instead of the dashboard, since
     * [org.commonlink.service.AuthService.setPassword] will accept one password change without
     * `currentPassword` while the grace window this flags is open.
     */
    val passwordResetPending: Boolean = false
)

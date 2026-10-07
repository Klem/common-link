package org.commonlink.dto

import org.commonlink.entity.DonorProfile
import java.util.UUID

/**
 * Donor profile returned by `GET`/`PATCH /api/donor/me`.
 *
 * Carries both identities: [firstName]/[lastName] are civil, [displayName] is the public pseudonym
 * shown on donation listings — and is hidden altogether when [anonymous] is true.
 */
data class DonorProfileDto(
    val id: UUID,
    val firstName: String?,
    val lastName: String?,
    val displayName: String?,
    val anonymous: Boolean,
    val notifyMonthlyReport: Boolean,
    val notifyNewPayout: Boolean,
    val notifyGoalReached: Boolean,
    val notifySuggestions: Boolean,
)

fun DonorProfile.toDto() = DonorProfileDto(
    id = id!!,
    firstName = firstName,
    lastName = lastName,
    displayName = displayName,
    anonymous = anonymous,
    notifyMonthlyReport = notifyMonthlyReport,
    notifyNewPayout = notifyNewPayout,
    notifyGoalReached = notifyGoalReached,
    notifySuggestions = notifySuggestions,
)

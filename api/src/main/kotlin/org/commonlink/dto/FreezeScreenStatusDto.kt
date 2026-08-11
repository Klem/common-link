package org.commonlink.dto

import java.time.Instant

/**
 * Four-state indicator of the most recent onboarding freeze screening for an association,
 * returned by `GET /api/admin/verifications/{id}/freeze-screen-status`.
 *
 * Deliberately contains no information that could identify a match or trigger tipping-off
 * (article L.561-29 CMF). No matched name, no registry entry ID, no alert identifier.
 */
enum class FreezeScreenStatus { NOT_PERFORMED, PASSED, HIT, UNAVAILABLE }

/**
 * Response body for `GET /api/admin/verifications/{id}/freeze-screen-status`.
 *
 * @param status  Derived status of the last onboarding freeze-screening run.
 * @param checkedAt  Timestamp of the first event in the last run, or null if NOT_PERFORMED.
 */
data class FreezeScreenStatusDto(
    val status: FreezeScreenStatus,
    val checkedAt: Instant?,
)

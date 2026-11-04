package org.commonlink.exception

import org.springframework.http.HttpStatus

/**
 * Thrown when a donation targets an association whose payment provider cannot collect right now (link missing or
 * broken, onboarding incomplete, or a provider with no registered implementation — defensive; every provider has one
 * today).
 *
 * Returns HTTP 409 with a `code: COLLECTION_UNAVAILABLE` property so the widget can tell "come back later" apart
 * from the collection cap. Raised before anything is created: no guest donor, no screening journal entry.
 */
class CollectionUnavailableException(
    message: String = "Donations are temporarily unavailable for this campaign",
) : AppException(message, HttpStatus.CONFLICT)

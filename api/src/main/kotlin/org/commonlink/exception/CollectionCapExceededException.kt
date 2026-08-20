package org.commonlink.exception

import org.springframework.http.HttpStatus
import java.math.BigDecimal

/**
 * Thrown when a donation would take a campaign past its collection cap.
 *
 * The refusal happens at payment initiation, before any money moves: a donation collected above the
 * cap could only be handed back, and a refund after collection is exactly what must never happen —
 * the receipt is already numbered, hashed on-chain and emailed to the donor.
 *
 * Returns HTTP 409 with a `code: COLLECTION_CAP_EXCEEDED` property so the widget can branch without
 * parsing the human-readable message; [remainingCapacity] is exposed alongside it so the form can
 * tell the donor what amount would still go through (0 = the campaign is full).
 */
class CollectionCapExceededException(
    val remainingCapacity: BigDecimal,
    message: String = "Donation refused: campaign collection cap reached",
) : AppException(message, HttpStatus.CONFLICT)

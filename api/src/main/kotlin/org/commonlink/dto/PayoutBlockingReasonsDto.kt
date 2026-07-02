package org.commonlink.dto

import org.commonlink.entity.PayoutBlockingReason

/**
 * Active blocking reasons preventing a payout from being issued, returned to the frontend
 * so it can display an explanation pill per reason below the "Émettre le paiement" button.
 *
 * @param reasons Empty when the payout is not blocked.
 */
data class PayoutBlockingReasonsDto(
    val reasons: List<PayoutBlockingReason>,
)

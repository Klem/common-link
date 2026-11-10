package org.commonlink.exception

import org.commonlink.entity.PayoutBlockingReason
import java.math.BigDecimal

/**
 * Thrown when a payout is refused for one reason only: the campaign's available donations do not cover it, at
 * creation ([org.commonlink.service.PayoutService.create]) or at confirmation
 * ([org.commonlink.service.PayoutConfirmer.reserve]). Applies to every payment provider.
 *
 * Returns HTTP 409 with a `code: FUNDS_NOT_AVAILABLE` property and [disbursable], the amount a payout could still
 * use at the moment of the refusal (never negative), so the Payments tab can branch without parsing the message.
 * The `inTransit` and `atProvider` properties (HelloAsso donations paid out but not yet arrived, and donations still
 * held by HelloAsso) are added with the HelloAsso payout tracking (FD5-01).
 *
 * A [ConflictException], so every caller handling a balance refusal as a conflict keeps doing so; the message is the
 * one the refusal carried before it was coded.
 */
class FundsNotAvailableException(
    val disbursable: BigDecimal,
    message: String = "Payout blocked: ${PayoutBlockingReason.INSUFFICIENT_BALANCE}",
) : ConflictException(message)

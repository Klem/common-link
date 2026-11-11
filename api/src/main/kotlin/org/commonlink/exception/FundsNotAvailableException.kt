package org.commonlink.exception

import org.commonlink.entity.PayoutBlockingReason
import java.math.BigDecimal

/**
 * Thrown when a payout is refused for one reason only: the campaign's available donations do not cover it, at
 * creation ([org.commonlink.service.PayoutService.create]) or at confirmation
 * ([org.commonlink.service.PayoutConfirmer.reserve]). Applies to every payment provider.
 *
 * Returns HTTP 409 with a `code: FUNDS_NOT_AVAILABLE` property and:
 * - [disbursable], the amount a payout could still use at the moment of the refusal (never negative);
 * - [inTransit], the campaign's HelloAsso donations paid out by HelloAsso but not arrived yet (settlement delay);
 * - [atProvider], its HelloAsso donations still held by HelloAsso (not paid out yet, payout in preparation included).
 * Both are zero for a Mollie campaign. The Payments tab branches on them without parsing the message.
 *
 * A [ConflictException], so every caller handling a balance refusal as a conflict keeps doing so; the message is the
 * one the refusal carried before it was coded.
 */
class FundsNotAvailableException(
    val disbursable: BigDecimal,
    val inTransit: BigDecimal = BigDecimal.ZERO,
    val atProvider: BigDecimal = BigDecimal.ZERO,
    message: String = "Payout blocked: ${PayoutBlockingReason.INSUFFICIENT_BALANCE}",
) : ConflictException(message)

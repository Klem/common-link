package org.commonlink.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal
import java.time.Duration

/**
 * Configuration of the campaign collection cap enforced before any payment is initiated.
 *
 * **Why a cap at all** — a donation collected beyond what the campaign asked for can only be
 * given back, and a refund after collection is precisely what must never happen: the receipt is
 * already numbered, hashed on-chain and emailed. The cap is therefore checked at payment
 * initiation, when refusing is still free.
 *
 * **Margin** — a hard cap at exactly the goal would reject the last donation of a campaign for a
 * few euros of overshoot, which reads as a bug to the donor. [marginPercent] widens the cap to
 * `goal × (1 + marginPercent / 100)`. Set it to `0` for a strict cap at the goal.
 *
 * **Reservation TTL** — donations whose payment session is still open are counted against the cap
 * for [reservationTtl], so two donors checking out simultaneously cannot collectively overshoot.
 * The default sits above Mollie's ~15-minute expiry of an untouched payment: shorter than the
 * provider's own window would release capacity that is still payable.
 */
@ConfigurationProperties(prefix = "app.donation.cap")
data class DonationCapProperties(
    val marginPercent: BigDecimal = BigDecimal("10"),
    val reservationTtl: Duration = Duration.ofMinutes(30),
)

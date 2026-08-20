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
 *
 * **Pending session ceiling** — because a pending donation *is* a reservation, and because the
 * widget endpoint is unauthenticated, an outsider could reserve a campaign's entire remaining
 * capacity and keep it frozen (security audit 2026-08-20, M6). [maxPendingSessions] bounds how many
 * open sessions a single campaign carries at once, independently of any per-caller rate limit.
 *
 * @property marginPercent Widening of the cap above the goal, in percent.
 * @property reservationTtl How long an open payment session holds capacity.
 * @property maxPendingSessions Open payment sessions allowed per campaign within [reservationTtl].
 */
@ConfigurationProperties(prefix = "app.donation.cap")
data class DonationCapProperties(
    val marginPercent: BigDecimal = BigDecimal("10"),
    val reservationTtl: Duration = Duration.ofMinutes(30),
    val maxPendingSessions: Int = 50,
)

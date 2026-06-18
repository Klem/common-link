package org.commonlink.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Represents a confirmed donation from a [DonorProfile] to a [Campaign].
 *
 * [providerRef] identifies the payment provider transaction (e.g. "stripe:pi_..." or "monerium:...").
 * [confirmedAt] is set when the payment is confirmed and the on-chain recording job is enqueued.
 */
@Entity
@Table(name = "donations")
class Donation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    val donor: DonorProfile,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    val campaign: Campaign,

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    /** Payment provider transaction reference. Format: "stripe:pi_..." or "monerium:<uuid>". */
    @Column(name = "provider_ref", nullable = false, length = 255)
    val providerRef: String,

    /** Set when payment is confirmed and on-chain recording job is enqueued. */
    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,

    /** French plan comptable prefix used for budget variance reporting. Default "74" (subventions). */
    @Column(name = "type_code", nullable = false, length = 50)
    var typeCode: String = "74",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

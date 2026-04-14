package org.commonlink.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Represents a fundraising campaign created and managed by an [AssociationProfile].
 *
 * A campaign has a financial goal, a lifecycle [status], an optional blockchain [contractAddress],
 * a hierarchical budget ([budgetSections] → items), and progress [milestones].
 *
 * Donations update the [raised] field; the campaign transitions from [CampaignStatus.DRAFT]
 * to [CampaignStatus.LIVE] once published, and to [CampaignStatus.ENDED] when closed.
 */
@Entity
@Table(name = "campaigns")
class Campaign(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The association that owns this campaign. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    val association: AssociationProfile,

    /** Display name of the campaign. */
    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    /** Emoji used as the campaign's visual icon. */
    @Column(name = "emoji", nullable = false, length = 10)
    var emoji: String = "🌍",

    /** Detailed description of the campaign's purpose and objectives. */
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    /** Total fundraising goal in euros. */
    @Column(name = "goal", nullable = false, precision = 12, scale = 2)
    var goal: BigDecimal = BigDecimal.ZERO,

    /** Amount raised so far; updated as donations are confirmed. */
    @Column(name = "raised", nullable = false, precision = 12, scale = 2)
    var raised: BigDecimal = BigDecimal.ZERO,

    /** Current lifecycle status of the campaign. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: CampaignStatus = CampaignStatus.DRAFT,

    /** Date when the campaign officially starts accepting donations. */
    @Column(name = "start_date")
    var startDate: LocalDate? = null,

    /** Date when the campaign stops accepting donations. */
    @Column(name = "end_date")
    var endDate: LocalDate? = null,

    /** Ethereum/EVM contract address once the campaign is deployed on-chain. Null before deployment. */
    @Column(name = "contract_address", length = 255)
    var contractAddress: String? = null,

    /** Timestamp of record creation; immutable after insert. */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /** Timestamp of last modification; updated on every save. */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    /**
     * Budget sections (EXPENSE and REVENUE) that make up the campaign's prévisionnel.
     *
     * Managed via cascade: adding or removing sections persists automatically.
     * Orphaned [CampaignBudgetSection] records are deleted when removed from the collection.
     */
    @OneToMany(mappedBy = "campaign", cascade = [CascadeType.ALL], orphanRemoval = true)
    val budgetSections: MutableList<CampaignBudgetSection> = mutableListOf()

    /**
     * Ordered list of milestones tracking progress towards the campaign goal.
     *
     * Managed via cascade: adding or removing milestones persists automatically.
     * Orphaned [CampaignMilestone] records are deleted when removed from the collection.
     */
    @OneToMany(mappedBy = "campaign", cascade = [CascadeType.ALL], orphanRemoval = true)
    val milestones: MutableList<CampaignMilestone> = mutableListOf()
}

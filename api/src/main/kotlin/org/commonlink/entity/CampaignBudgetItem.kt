package org.commonlink.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

/**
 * Represents a single line item within a [CampaignBudgetSection].
 *
 * Each item has a [label] describing the expense or revenue, and an [amount] in euros.
 * Display order within the section is controlled by [sortOrder].
 */
@Entity
@Table(name = "campaign_budget_items")
class CampaignBudgetItem(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The budget section this item belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    val section: CampaignBudgetSection,

    /** Description of this budget line (e.g. "Impression flyers", "Subvention mairie"). */
    @Column(name = "label", nullable = false, length = 255)
    var label: String,

    /** Estimated amount for this budget line in euros. */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO,

    /** Position of this item within the section display; lower value = displayed first. */
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
)

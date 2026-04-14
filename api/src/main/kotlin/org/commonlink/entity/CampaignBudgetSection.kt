package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Represents a section within the budget prévisionnel of a [Campaign].
 *
 * Sections are categorised by [side] (EXPENSE or REVENUE) and identified by a short [code]
 * (e.g. "CHARGES_EXT", "SUBVENTIONS"). Each section holds an ordered list of [items].
 *
 * Display order across sections is controlled by [sortOrder].
 */
@Entity
@Table(name = "campaign_budget_sections")
class CampaignBudgetSection(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The campaign this budget section belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    val campaign: Campaign,

    /** Whether this section represents expenses (charges) or revenues (produits). */
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    val side: BudgetSide,

    /** Short unique code identifying the section within the campaign (e.g. "CHARGES_EXT"). */
    @Column(name = "code", nullable = false, length = 50)
    var code: String,

    /** Human-readable label displayed in the budget UI. */
    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    /** Position of this section within the budget display; lower value = displayed first. */
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    /** Timestamp of record creation; immutable after insert. */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    /**
     * Line items belonging to this budget section.
     *
     * Managed via cascade: adding or removing items persists automatically.
     * Orphaned [CampaignBudgetItem] records are deleted when removed from the collection.
     */
    @OneToMany(mappedBy = "section", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<CampaignBudgetItem> = mutableListOf()
}

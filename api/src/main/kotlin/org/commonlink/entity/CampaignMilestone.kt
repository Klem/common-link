package org.commonlink.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Represents a fundraising milestone within a [Campaign].
 *
 * Milestones are ordered targets (e.g. "50% funded — buy equipment") that track progress
 * towards the campaign goal. Only one milestone can be [MilestoneStatus.CURRENT] at a time;
 * it becomes [MilestoneStatus.REACHED] once [targetAmount] is hit, and [reachedAt] is set.
 *
 * Display order is controlled by [sortOrder].
 */
@Entity
@Table(name = "campaign_milestones")
class CampaignMilestone(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The campaign this milestone belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    val campaign: Campaign,

    /** Emoji icon representing this milestone visually. */
    @Column(name = "emoji", nullable = false, length = 10)
    var emoji: String = "🎯",

    /** Short title describing what will be achieved at this milestone. */
    @Column(name = "title", nullable = false, length = 255)
    var title: String,

    /** Optional detailed description of the milestone deliverable. */
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    /** Amount of donations required to reach this milestone. */
    @Column(name = "target_amount", nullable = false, precision = 12, scale = 2)
    var targetAmount: BigDecimal = BigDecimal.ZERO,

    /** Current progress status of this milestone. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: MilestoneStatus = MilestoneStatus.LOCKED,

    /** Position of this milestone in the ordered list; lower value = shown first. */
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    /** Timestamp when the milestone target was reached. Null until [MilestoneStatus.REACHED]. */
    @Column(name = "reached_at")
    var reachedAt: Instant? = null,

    /** Timestamp of record creation; immutable after insert. */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

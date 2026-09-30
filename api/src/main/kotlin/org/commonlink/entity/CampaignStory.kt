package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * A free-text impact narrative written by the association for one campaign (D5, option C).
 *
 * One-to-one with [Campaign] -- at most one story per campaign, replaced in place on re-save
 * (no version history in this sprint). Photos and testimonial are deliberately not modeled yet:
 * the donor-facing UI renders them as a "not yet published" placeholder until a later sprint.
 *
 * [publishedAt] gates donor visibility: null means draft, visible only in the association editor.
 */
@Entity
@Table(name = "campaign_stories")
class CampaignStory(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false, unique = true)
    val campaign: Campaign,

    @Column(name = "story_text", nullable = false, columnDefinition = "TEXT")
    var storyText: String,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

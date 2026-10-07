package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * A rich-text impact narrative written by the association for one campaign (D5, option C).
 *
 * One-to-one with [Campaign] -- at most one story per campaign, replaced in place on re-save
 * (no version history). Images referenced inside [storyText] are stored separately as
 * [CampaignStoryImage] rows, served publicly, and referenced by `<img src>` inside the HTML.
 *
 * [storyText] is sanitized server-side ([org.commonlink.service.CampaignStoryService]) before
 * ever reaching this entity -- it is safe to render as-is with `dangerouslySetInnerHTML` on the
 * frontend, but must never be trusted as pre-sanitized when read from anywhere else.
 *
 * [storySummary] is the short, plain-text counterpart: the only text the impact gallery and the
 * share card ever compose into the collective wording (D6) -- [storyText]'s HTML is never
 * interpolated into that sentence.
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

    /** Sanitized rich-text HTML -- see class KDoc. */
    @Column(name = "story_text", nullable = false, columnDefinition = "TEXT")
    var storyText: String,

    /** Plain text, no HTML -- what the gallery/share card render. */
    @Column(name = "story_summary", nullable = false, length = 220)
    var storySummary: String,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

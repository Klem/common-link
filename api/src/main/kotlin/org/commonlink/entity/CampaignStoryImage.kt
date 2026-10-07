package org.commonlink.entity

import jakarta.persistence.Basic
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Binary content of one image embedded in a campaign's story ([CampaignStory]).
 *
 * Own table with its own UUID primary key -- unlike [CampaignCoverImage] (one image per
 * campaign, shared PK), a story can reference several images. Served publicly at
 * `/api/public/campaigns/{campaignId}/story-images/{id}` and referenced by `<img src>` inside
 * the sanitized [CampaignStory.storyText] HTML.
 */
@Entity
@Table(name = "campaign_story_images")
class CampaignStoryImage(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    @Column(name = "campaign_id", nullable = false, updatable = false)
    val campaignId: UUID,

    /** Raw image content stored as BYTEA. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false)
    val data: ByteArray,

    /** MIME type as declared at upload -- the actual serving type is re-sniffed from bytes. */
    @Column(name = "content_type", nullable = false, length = 100)
    val contentType: String,

    /** Image size in bytes. */
    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

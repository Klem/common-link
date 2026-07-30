package org.commonlink.entity

import jakarta.persistence.Basic
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Binary content of a campaign's cover image.
 *
 * Stored in its own table keyed by the campaign id (shared primary key) rather than as a
 * column on `campaigns`: fetching a campaign must never drag the image bytes along, and a
 * `@Basic(LAZY)` bytea column would only stay lazy with bytecode enhancement enabled.
 *
 * [Campaign.coverImage] holds the public serving path of this image
 * (`/api/public/campaigns/{id}/cover`); this entity holds the bytes.
 */
@Entity
@Table(name = "campaign_cover_images")
class CampaignCoverImage(

    /** Primary key — also the id of the campaign the image belongs to. */
    @Id
    @Column(name = "campaign_id", nullable = false, updatable = false)
    val campaignId: UUID,

    /** Raw image content stored as BYTEA. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false)
    val data: ByteArray,

    /** MIME type of the image (image/jpeg, image/png or image/webp). */
    @Column(name = "content_type", nullable = false, length = 100)
    val contentType: String,

    /** Image size in bytes. */
    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    /** Timestamp of the upload. */
    @Column(name = "uploaded_at", nullable = false)
    val uploadedAt: Instant,
)

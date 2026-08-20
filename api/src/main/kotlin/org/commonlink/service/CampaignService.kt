package org.commonlink.service

import org.commonlink.dto.CampaignDto
import org.commonlink.dto.CampaignSummaryDto
import org.commonlink.dto.CreateCampaignRequest
import org.commonlink.dto.CreateMilestoneRequest
import org.commonlink.dto.MilestoneDto
import org.commonlink.dto.ReorderMilestonesRequest
import org.commonlink.dto.SaveBudgetRequest
import org.commonlink.dto.UpdateCampaignRequest
import org.commonlink.dto.UpdateMilestoneRequest
import org.commonlink.dto.toDto
import org.commonlink.dto.toSummaryDto
import org.commonlink.entity.BudgetSide
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignBudgetItem
import org.commonlink.entity.CampaignCoverImage
import org.commonlink.entity.CampaignBudgetSection
import org.commonlink.entity.CampaignMilestone
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.MilestoneStatus
import org.commonlink.entity.MollieConnectionState
import org.commonlink.entity.MollieOnboardingStatus
import org.commonlink.entity.OnchainJobAction
import org.commonlink.entity.VerificationStatus
import org.commonlink.exception.NotFoundException
import org.commonlink.exception.UnprocessableEntityException
import org.commonlink.exception.UserNotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.CampaignBudgetSectionRepository
import org.commonlink.repository.CampaignCoverImageRepository
import org.commonlink.repository.CampaignMilestoneRepository
import org.commonlink.repository.CampaignRepository
import org.commonlink.repository.MollieConnectionRepository
import org.commonlink.util.FileTypeSniffer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Maximum accepted cover image size — mirrored in the frontend upload zone (rule 8). */
private const val MAX_COVER_IMAGE_SIZE = 5L * 1024 * 1024

/** Accepted cover image MIME types — mirrored in the frontend upload zone (rule 8). */
private val COVER_IMAGE_ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp")

/**
 * Minimum length of the expected outcome ([Campaign.impactGoals]) required to publish.
 * Mirrored in `PrePublishModal.tsx` (rule 8).
 */
private const val MIN_IMPACT_GOALS_LENGTH = 20

/** Public serving path of a campaign cover image; stored in [Campaign.coverImage]. */
private fun coverImagePath(campaignId: UUID): String = "/api/public/campaigns/$campaignId/cover"

/**
 * Business logic for managing fundraising campaigns of an association.
 *
 * All operations are scoped to the authenticated association: the [userId] from the JWT is
 * resolved to an [org.commonlink.entity.AssociationProfile] id before any data access.
 * This prevents cross-association data leaks.
 *
 * Covers full campaign CRUD, bulk budget replacement, and milestone management.
 */
@Service
class CampaignService(
    private val campaignRepository: CampaignRepository,
    private val campaignBudgetSectionRepository: CampaignBudgetSectionRepository,
    private val campaignMilestoneRepository: CampaignMilestoneRepository,
    private val campaignCoverImageRepository: CampaignCoverImageRepository,
    private val associationProfileRepository: AssociationProfileRepository,
    private val mollieConnectionRepository: MollieConnectionRepository,
    private val budgetHasher: CampaignBudgetHasher,
    private val outbox: OnchainOutboxService,
) {

    private val logger = LoggerFactory.getLogger(CampaignService::class.java)

    /** Returns true if a campaign with the given id exists (admin use — no association scoping). */
    fun existsById(id: UUID): Boolean = campaignRepository.existsById(id)

    /**
     * Applies an admin-initiated off-chain status transition and saves the campaign.
     *
     * Used by [org.commonlink.controller.AdminOnchainController] — not scoped to any association.
     * The caller is responsible for enqueueing the corresponding on-chain job afterwards.
     *
     * REVERT_TO_DRAFT is only allowed when [Campaign.raised] is zero, mirroring the
     * contract's `CampaignHasDonations` revert guard.
     *
     * @param id UUID of the campaign.
     * @param target The target [CampaignStatus].
     * @throws org.commonlink.exception.NotFoundException if the campaign does not exist.
     * @throws UnprocessableEntityException if the transition is invalid or REVERT_TO_DRAFT on funded campaign.
     */
    @Transactional
    fun adminTransition(id: UUID, target: CampaignStatus) {
        val campaign = campaignRepository.findById(id)
            .orElseThrow { NotFoundException("Campaign not found: $id") }
        if (target == CampaignStatus.DRAFT) {
            if (campaign.status != CampaignStatus.REVERT_REQUESTED) {
                throw UnprocessableEntityException("Campaign must be in REVERT_REQUESTED state to revert to draft (current: ${campaign.status})")
            }
            if (campaign.raised > BigDecimal.ZERO) {
                throw UnprocessableEntityException("Cannot revert to draft: campaign has raised ${campaign.raised}")
            }
        } else {
            validateStatusTransition(campaign.status, target)
        }
        campaign.status = target
        campaignRepository.save(campaign)
        logger.info("Admin transition: campaignId={}, newStatus={}", id, target)
    }

    /**
     * Returns all campaigns for the authenticated association, sorted by creation date descending.
     *
     * @param userId UUID of the authenticated association user.
     * @return List of [CampaignSummaryDto], possibly empty.
     * @throws UserNotFoundException if no association profile exists for this user.
     */
    fun listCampaigns(userId: UUID): List<CampaignSummaryDto> {
        val associationId = resolveAssociationId(userId)
        return campaignRepository.findAllWithMilestonesByAssociationIdOrderByCreatedAtDesc(associationId)
            .map { it.toSummaryDto() }
    }

    /**
     * Returns full campaign detail including budget sections (with items) and milestones.
     *
     * Uses an [org.springframework.data.jpa.repository.EntityGraph] to load all nested
     * collections in a single query — avoids LazyInitializationException outside a transaction.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign to retrieve.
     * @return [CampaignDto] with all nested data.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     */
    @Transactional(readOnly = true)
    fun getCampaign(userId: UUID, campaignId: UUID): CampaignDto {
        val associationId = resolveAssociationId(userId)
        return resolveCampaignWithDetails(campaignId, associationId).toDto()
    }

    /**
     * Creates a new campaign under the authenticated association.
     *
     * @param userId UUID of the authenticated association user.
     * @param req Creation request with campaign details.
     * @return [CampaignDto] of the persisted campaign.
     * @throws UserNotFoundException if no association profile exists for this user.
     */
    @Transactional
    fun createCampaign(userId: UUID, req: CreateCampaignRequest): CampaignDto {
        val association = associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
        val campaign = Campaign(
            association = association,
            name = req.name,
            emoji = req.emoji ?: "🌍",
            description = req.description,
            goal = req.goal ?: BigDecimal.ZERO,
            startDate = req.startDate,
            endDate = req.endDate
        )
        val saved = campaignRepository.save(campaign)
        logger.info("Campaign created: id={}, name={}, associationId={}", saved.id, saved.name, association.id)
        return saved.toDto()
    }

    /**
     * Partially updates a campaign — only non-null fields in [req] are applied.
     *
     * Status transitions are validated: DRAFT→LIVE and LIVE→ENDED are allowed;
     * backwards transitions (e.g. ENDED→LIVE) are rejected with a 422.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign to update.
     * @param req Update request; null fields are ignored.
     * @return Updated [CampaignDto].
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     * @throws UnprocessableEntityException if the requested status transition is invalid.
     */
    @Transactional
    fun updateCampaign(userId: UUID, campaignId: UUID, req: UpdateCampaignRequest): CampaignDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)

        if (req.name != null) campaign.name = req.name
        if (req.emoji != null) campaign.emoji = req.emoji
        if (req.description != null) campaign.description = req.description
        if (req.goal != null) campaign.goal = req.goal
        if (req.startDate != null) campaign.startDate = req.startDate
        if (req.endDate != null) campaign.endDate = req.endDate
        if (req.category != null) campaign.category = req.category
        if (req.reason != null) campaign.reason = req.reason
        if (req.impactGoals != null) campaign.impactGoals = req.impactGoals
        if (req.coverImage != null) campaign.coverImage = req.coverImage

        val effectiveStart = campaign.startDate
        val effectiveEnd = campaign.endDate
        if (effectiveStart != null && effectiveEnd != null && effectiveEnd < effectiveStart.plusDays(7)) {
            throw UnprocessableEntityException("End date must be at least 7 days after start date")
        }

        val previousStatus = campaign.status
        if (req.status != null) {
            validateStatusTransition(campaign.status, req.status)
            if (previousStatus == CampaignStatus.DRAFT && req.status == CampaignStatus.LIVE) {
                preparePublish(campaign, associationId)
            }
            if (req.status == CampaignStatus.REVERT_REQUESTED && campaign.raised > BigDecimal.ZERO) {
                throw UnprocessableEntityException("Cannot revert to draft: campaign has raised ${campaign.raised}")
            }
            campaign.status = req.status
        }
        campaign.updatedAt = Instant.now()
        campaignRepository.save(campaign)

        if (req.status != null && req.status != previousStatus) {
            enqueueForTransition(previousStatus, req.status, campaign)
        }

        logger.debug("Campaign updated: id={}, associationId={}", campaignId, associationId)
        return resolveCampaignWithDetails(campaignId, associationId).toDto()
    }

    /**
     * Stores (or replaces) the cover image of a campaign.
     *
     * Mirror of the frontend validation in `CampaignInfoTab` (rule 8): only JPEG, PNG and WebP
     * are accepted, up to [MAX_COVER_IMAGE_SIZE]. On success, [Campaign.coverImage] is set to the
     * public serving path of the image so every consumer (dashboard, widget, minisite) resolves
     * it the same way.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign.
     * @param file Uploaded image.
     * @return Updated [CampaignDto].
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     * @throws UnprocessableEntityException if the file is empty, too large, or not an accepted image type.
     */
    @Transactional
    fun uploadCoverImage(userId: UUID, campaignId: UUID, file: MultipartFile): CampaignDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        validateCoverImage(file)

        // Shared primary key: saving over an existing row replaces the previous image.
        campaignCoverImageRepository.save(
            CampaignCoverImage(
                campaignId = campaignId,
                data = file.bytes,
                contentType = file.contentType!!,
                sizeBytes = file.size,
                uploadedAt = Instant.now(),
            )
        )
        campaign.coverImage = coverImagePath(campaignId)
        campaign.updatedAt = Instant.now()
        campaignRepository.save(campaign)

        logger.info(
            "Campaign cover image uploaded: campaignId={}, associationId={}, size={}",
            campaignId, associationId, file.size,
        )
        return resolveCampaignWithDetails(campaignId, associationId).toDto()
    }

    /**
     * Removes the cover image of a campaign. No-op if the campaign has no image.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign.
     * @return Updated [CampaignDto] with a null `coverImage`.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     */
    @Transactional
    fun deleteCoverImage(userId: UUID, campaignId: UUID): CampaignDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)

        campaignCoverImageRepository.deleteById(campaignId)
        campaign.coverImage = null
        campaign.updatedAt = Instant.now()
        campaignRepository.save(campaign)

        logger.info("Campaign cover image deleted: campaignId={}, associationId={}", campaignId, associationId)
        return resolveCampaignWithDetails(campaignId, associationId).toDto()
    }

    /**
     * Returns the raw cover image of a campaign, for the unauthenticated serving endpoint.
     *
     * Deliberately not scoped to an association and not gated on [CampaignStatus]: an `<img>` tag
     * cannot carry a Bearer token, so the same URL must work for the owner's draft preview and for
     * the public widget. A cover image carries no confidential data and the campaign UUID is not
     * enumerable.
     *
     * The bytes are read inside the transaction and returned as a plain pair, so the caller never
     * touches the lazily-mapped `data` field outside the persistence context.
     *
     * @param campaignId UUID of the campaign.
     * @return MIME type and raw bytes of the image.
     * @throws NotFoundException if the campaign has no cover image.
     */
    @Transactional(readOnly = true)
    fun getCoverImage(campaignId: UUID): Pair<String, ByteArray> {
        val image = campaignCoverImageRepository.findById(campaignId)
            .orElseThrow { NotFoundException("No cover image for campaign $campaignId") }
        // Same rule as the association logo: the served Content-Type comes from the bytes, not from
        // the stored declaration (security audit 2026-08-20, M9).
        val contentType = FileTypeSniffer.detectImageMime(image.data)
            ?: run {
                logger.warn("Refusing to serve cover image of campaign {} — bytes are not a supported image", campaignId)
                throw NotFoundException("No cover image for campaign $campaignId")
            }
        return contentType to image.data
    }

    /** Rejects empty files, oversized files, non-image MIME types, and mislabelled bytes. */
    private fun validateCoverImage(file: MultipartFile) {
        if (file.isEmpty) {
            throw UnprocessableEntityException("Cover image file is empty")
        }
        if (file.size > MAX_COVER_IMAGE_SIZE) {
            throw UnprocessableEntityException("Cover image exceeds the maximum allowed size of 5 MB")
        }
        val mime = file.contentType ?: ""
        if (mime !in COVER_IMAGE_ALLOWED_MIME) {
            throw UnprocessableEntityException(
                "Unsupported cover image type '$mime'; allowed types: ${COVER_IMAGE_ALLOWED_MIME.joinToString(", ")}"
            )
        }
        // Served back verbatim from a public endpoint, so the bytes must match the declared type
        // (security audit 2026-08-20, M9).
        if (!FileTypeSniffer.matches(file.bytes, mime)) {
            logger.warn("Rejected cover image upload: bytes do not match declared type {}", mime)
            throw UnprocessableEntityException("Cover image content does not match its declared type '$mime'")
        }
    }

    /**
     * Deletes a campaign and all its related data (budget sections, items, milestones via cascade).
     *
     * Only a campaign still in [CampaignStatus.DRAFT] can be deleted — once published, its data
     * (donations, milestones) must be preserved for transparency and on-chain consistency.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign to delete.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     * @throws UnprocessableEntityException if the campaign is not in DRAFT status.
     */
    @Transactional
    fun deleteCampaign(userId: UUID, campaignId: UUID) {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        if (campaign.status != CampaignStatus.DRAFT) {
            throw UnprocessableEntityException(
                "Cannot delete campaign in status ${campaign.status}; only DRAFT campaigns can be deleted"
            )
        }
        campaignRepository.delete(campaign)
        logger.info("Campaign deleted: id={}, associationId={}", campaignId, associationId)
    }

    /**
     * Replaces the entire budget structure for a campaign in a single atomic operation.
     *
     * All existing sections and items are deleted before the new structure is persisted.
     * Sections and items are ordered according to the [sortOrder] fields in [req].
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign whose budget is being replaced.
     * @param req New budget structure (sections → items).
     * @return Updated [CampaignDto] with the new budget.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     */
    @Transactional
    fun saveBudget(userId: UUID, campaignId: UUID, req: SaveBudgetRequest): CampaignDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)

        // Clear the parent collection so orphanRemoval deletes all existing sections and items
        campaign.budgetSections.clear()
        campaignRepository.saveAndFlush(campaign)

        req.sections.forEach { sectionReq ->
            val section = CampaignBudgetSection(
                campaign = campaign,
                side = sectionReq.side,
                code = sectionReq.code,
                name = sectionReq.name,
                sortOrder = sectionReq.sortOrder
            )
            sectionReq.items.forEach { itemReq ->
                val item = CampaignBudgetItem(
                    section = section,
                    label = itemReq.label,
                    amount = itemReq.amount,
                    sortOrder = itemReq.sortOrder
                )
                section.items.add(item)
            }
            // Keep the parent collection in sync so orphanRemoval does not delete the new section
            campaign.budgetSections.add(section)
        }

        campaign.updatedAt = Instant.now()
        campaignRepository.saveAndFlush(campaign)

        val newHash = budgetHasher.hash(campaign)
        if (newHash != campaign.budgetHash) {
            campaign.budgetHash = newHash
            campaignRepository.save(campaign)
            if (campaign.status != CampaignStatus.DRAFT) {
                outbox.enqueue(
                    OnchainJobAction.UPDATE_CAMPAIGN_BUDGET,
                    UpdateCampaignBudgetPayload(campaign.id!!, newHash),
                    correlationKey = null,
                )
                logger.info("UPDATE_CAMPAIGN_BUDGET enqueued: campaignId={}", campaign.id)
            }
        }

        logger.debug("Budget saved for campaign: id={}, sections={}", campaignId, req.sections.size)
        return resolveCampaignWithDetails(campaignId, associationId).toDto()
    }

    /**
     * Adds a new milestone to a campaign.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign to add the milestone to.
     * @param req Milestone creation request.
     * @return [MilestoneDto] of the persisted milestone.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign is not found under this association.
     */
    @Transactional
    fun addMilestone(userId: UUID, campaignId: UUID, req: CreateMilestoneRequest): MilestoneDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        val milestone = CampaignMilestone(
            campaign = campaign,
            emoji = req.emoji ?: "🎯",
            title = req.title,
            description = req.description,
            transparencyCommitment = req.transparencyCommitment,
            targetAmount = req.targetAmount,
            sortOrder = req.sortOrder
        )
        val saved = campaignMilestoneRepository.save(milestone)
        logger.info("Milestone added: id={}, campaignId={}, title={}", saved.id, campaignId, saved.title)
        return saved.toDto()
    }

    /**
     * Partially updates a milestone — only non-null fields in [req] are applied.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign that owns the milestone.
     * @param milestoneId UUID of the milestone to update.
     * @param req Update request; null fields are ignored.
     * @return Updated [MilestoneDto].
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign or milestone is not found.
     * @throws UnprocessableEntityException if the milestone is not LOCKED (CURRENT/REACHED milestones are immutable).
     */
    @Transactional
    fun updateMilestone(
        userId: UUID,
        campaignId: UUID,
        milestoneId: UUID,
        req: UpdateMilestoneRequest
    ): MilestoneDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        val milestone = campaignMilestoneRepository.findByIdAndCampaignId(milestoneId, campaign.id!!)
            .orElseThrow { NotFoundException("Milestone not found") }

        if (milestone.status != MilestoneStatus.LOCKED) {
            throw UnprocessableEntityException(
                "Cannot modify milestone in status ${milestone.status}; only LOCKED milestones can be edited"
            )
        }

        if (req.title != null) milestone.title = req.title
        if (req.emoji != null) milestone.emoji = req.emoji
        if (req.description != null) milestone.description = req.description
        if (req.transparencyCommitment != null) milestone.transparencyCommitment = req.transparencyCommitment
        if (req.targetAmount != null) milestone.targetAmount = req.targetAmount
        if (req.status != null) milestone.status = req.status
        if (req.sortOrder != null) milestone.sortOrder = req.sortOrder

        val saved = campaignMilestoneRepository.save(milestone)
        logger.debug("Milestone updated: id={}, campaignId={}", milestoneId, campaignId)
        return saved.toDto()
    }

    /**
     * Deletes a milestone from a campaign.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign that owns the milestone.
     * @param milestoneId UUID of the milestone to delete.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign or milestone is not found.
     * @throws UnprocessableEntityException if the milestone is not LOCKED (CURRENT/REACHED milestones cannot be deleted).
     */
    @Transactional
    fun deleteMilestone(userId: UUID, campaignId: UUID, milestoneId: UUID) {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        val milestone = campaignMilestoneRepository.findByIdAndCampaignId(milestoneId, campaign.id!!)
            .orElseThrow { NotFoundException("Milestone not found") }

        if (milestone.status != MilestoneStatus.LOCKED) {
            throw UnprocessableEntityException(
                "Cannot delete milestone in status ${milestone.status}; only LOCKED milestones can be deleted"
            )
        }

        campaignMilestoneRepository.delete(milestone)
        logger.info("Milestone deleted: id={}, campaignId={}", milestoneId, campaignId)
    }

    /**
     * Marks a milestone as reached, sets its [CampaignMilestone.reachedAt] timestamp, and
     * enqueues a [OnchainJobAction.MARK_MILESTONE_REACHED] job with the keccak256 hash of [proofUrl].
     *
     * The milestone's [CampaignMilestone.sortOrder] is used as the 0-based on-chain index — it
     * must match the position the milestone occupied when [OnchainJobAction.CREATE_CAMPAIGN] was
     * dispatched.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign that owns the milestone.
     * @param milestoneId UUID of the milestone to mark as reached.
     * @param proofUrl URL of the proof document. Its keccak256 hash is stored on-chain.
     * @return Updated [MilestoneDto].
     * @throws NotFoundException if the campaign or milestone is not found.
     * @throws UnprocessableEntityException if the milestone is already reached.
     */
    @Transactional
    fun markMilestoneReached(userId: UUID, campaignId: UUID, milestoneId: UUID, proofUrl: String): MilestoneDto {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        val milestone = campaignMilestoneRepository.findByIdAndCampaignId(milestoneId, campaign.id!!)
            .orElseThrow { NotFoundException("Milestone not found") }

        if (milestone.status == MilestoneStatus.REACHED) {
            throw UnprocessableEntityException("Milestone is already reached")
        }

        milestone.status = MilestoneStatus.REACHED
        milestone.reachedAt = Instant.now()
        campaignMilestoneRepository.save(milestone)

        val proofHashHex = Numeric.toHexString(Hash.sha3(proofUrl.toByteArray(Charsets.UTF_8)))
        outbox.enqueue(
            OnchainJobAction.MARK_MILESTONE_REACHED,
            MilestonePayload(campaignId, milestone.sortOrder, proofHashHex),
            correlationKey = "MILESTONE:${campaignId}:${milestone.sortOrder}",
        )
        logger.info("MARK_MILESTONE_REACHED enqueued: campaignId={}, index={}", campaignId, milestone.sortOrder)
        return milestone.toDto()
    }

    /**
     * Reorders milestones for a campaign by reassigning [CampaignMilestone.sortOrder] based on
     * the position of each milestone ID in [req.milestoneIds].
     *
     * The first ID in the list gets sortOrder=0, the second gets sortOrder=1, and so on.
     *
     * @param userId UUID of the authenticated association user.
     * @param campaignId UUID of the campaign whose milestones are being reordered.
     * @param req Ordered list of milestone UUIDs.
     * @return Updated list of [MilestoneDto] in the new order.
     * @throws UserNotFoundException if no association profile exists for this user.
     * @throws NotFoundException if the campaign or any milestone is not found.
     */
    @Transactional
    fun reorderMilestones(userId: UUID, campaignId: UUID, req: ReorderMilestonesRequest): List<MilestoneDto> {
        val associationId = resolveAssociationId(userId)
        val campaign = resolveCampaign(campaignId, associationId)
        val milestones = campaignMilestoneRepository.findAllByCampaignIdOrderBySortOrder(campaign.id!!)
        val milestoneMap = milestones.associateBy { it.id }

        req.milestoneIds.forEachIndexed { index, milestoneId ->
            val milestone = milestoneMap[milestoneId]
                ?: throw NotFoundException("Milestone not found: $milestoneId")
            milestone.sortOrder = index
        }

        campaignMilestoneRepository.saveAll(milestones)
        logger.debug("Milestones reordered for campaign: id={}", campaignId)

        return campaignMilestoneRepository.findAllByCampaignIdOrderBySortOrder(campaign.id!!)
            .map { it.toDto() }
    }

    /**
     * Resolves the association profile id for the given user.
     *
     * @param userId UUID of the authenticated user.
     * @return UUID of the corresponding [org.commonlink.entity.AssociationProfile].
     * @throws UserNotFoundException if no profile exists for this user.
     */
    private fun resolveAssociationId(userId: UUID): UUID =
        associationProfileRepository.findByUserId(userId)
            .orElseThrow { UserNotFoundException("Association profile not found for user $userId") }
            .id!!

    /**
     * Resolves a campaign by its id, verifying it belongs to the given association.
     * Collections are not eagerly loaded — use [resolveCampaignWithDetails] when DTO mapping is needed.
     *
     * @param campaignId UUID of the campaign.
     * @param associationId UUID of the owning association.
     * @return The [Campaign] entity.
     * @throws NotFoundException if the campaign does not exist or belongs to a different association.
     */
    private fun resolveCampaign(campaignId: UUID, associationId: UUID): Campaign =
        campaignRepository.findByIdAndAssociationId(campaignId, associationId)
            .orElseThrow { NotFoundException("Campaign not found") }

    /**
     * Resolves a campaign with all nested collections eagerly loaded via [org.springframework.data.jpa.repository.EntityGraph].
     *
     * Fetches budgetSections, budgetSections.items, and milestones in a single query.
     * Use this when you need to call [org.commonlink.dto.toDto] outside a transaction.
     *
     * @param campaignId UUID of the campaign.
     * @param associationId UUID of the owning association.
     * @return The [Campaign] entity with all collections initialised.
     * @throws NotFoundException if the campaign does not exist or belongs to a different association.
     */
    private fun resolveCampaignWithDetails(campaignId: UUID, associationId: UUID): Campaign =
        campaignRepository.findWithDetailsByIdAndAssociationId(campaignId, associationId)
            .orElseThrow { NotFoundException("Campaign not found") }

    /**
     * Validates that the requested status transition is allowed.
     *
     * @param current Current status of the campaign.
     * @param next Requested new status.
     * @throws UnprocessableEntityException if the transition is not allowed.
     */
    private fun validateStatusTransition(current: CampaignStatus, next: CampaignStatus) {
        val allowed = when (current) {
            CampaignStatus.DRAFT            -> next == CampaignStatus.LIVE || next == CampaignStatus.ENDED
            CampaignStatus.LIVE             -> next == CampaignStatus.PAUSED || next == CampaignStatus.CANCELLED ||
                                               next == CampaignStatus.COMPLETED || next == CampaignStatus.ENDED ||
                                               next == CampaignStatus.REVERT_REQUESTED
            CampaignStatus.PAUSED           -> next == CampaignStatus.LIVE || next == CampaignStatus.CANCELLED ||
                                               next == CampaignStatus.COMPLETED ||
                                               next == CampaignStatus.REVERT_REQUESTED
            CampaignStatus.REVERT_REQUESTED -> false  // terminal for association; only CURATOR can move it
            CampaignStatus.ENDED            -> false
            CampaignStatus.CANCELLED        -> false
            CampaignStatus.COMPLETED        -> false
        }
        if (!allowed) {
            throw UnprocessableEntityException("Invalid status transition from $current to $next")
        }
    }

    /**
     * Pre-save checks and preparation for the DRAFT→LIVE publish transition.
     * Sets [Campaign.budgetHash] on the campaign instance (persisted by the caller's save).
     *
     * A balanced budget prévisionnel ([requireBalancedBudget]) and a stated expected outcome
     * ([Campaign.impactGoals], at least [MIN_IMPACT_GOALS_LENGTH] characters) are publication
     * blockers, not recommendations: a donor is asked for money against a costed plan and a
     * declared result. Both predicates mirror `PrePublishModal.tsx` exactly (rule 8).
     *
     * The KYB guard re-checks [org.commonlink.entity.AssociationProfile.verificationStatus] at publish
     * time. The onboarding chain already implies it transitively (a signed mandate requires VERIFIED,
     * and a Mollie connection requires a signed mandate), but only *at the time each step was taken* —
     * a dossier revoked afterwards would otherwise still publish. LCB-FT requires the gate to hold at
     * the moment of publication, so the check is explicit and independent of the Mollie state.
     *
     * The bank-account guard delegates to [MollieConnection.canCollectDonations], which mirrors the
     * `BankSetupStatus.COMPLETED` condition the frontend uses to enable the publish button (see
     * `app/src/lib/bankSetupStatus.ts`). The two preceding checks only exist to give a specific
     * message — "not connected" and "broken link" call for different user actions than
     * "KYC incomplete".
     */
    private fun preparePublish(campaign: Campaign, associationId: UUID) {
        if (campaign.goal <= BigDecimal.ZERO) {
            throw UnprocessableEntityException("Campaign goal must be greater than zero before publishing")
        }
        requireBalancedBudget(campaign)
        if ((campaign.impactGoals?.trim()?.length ?: 0) < MIN_IMPACT_GOALS_LENGTH) {
            throw UnprocessableEntityException(
                "Expected outcome (impactGoals) must be at least $MIN_IMPACT_GOALS_LENGTH characters before publishing"
            )
        }
        val profile = associationProfileRepository.findById(associationId)
            .orElseThrow { UserNotFoundException("Association profile not found: $associationId") }
        if (profile.verificationStatus != VerificationStatus.VERIFIED) {
            throw UnprocessableEntityException("Association KYB must be verified before going live")
        }
        val connection = mollieConnectionRepository.findByAssociationId(associationId)
            ?: throw UnprocessableEntityException("Association must connect a Mollie account before going live")
        if (connection.state == MollieConnectionState.BROKEN) {
            throw UnprocessableEntityException("Mollie connection is broken — re-authorization required before going live")
        }
        if (!connection.canCollectDonations()) {
            throw UnprocessableEntityException("Association must complete Mollie KYC before going live")
        }
        campaign.budgetHash = budgetHasher.hash(campaign)
        logger.debug("Publish prepared: campaignId={}, budgetHash={}", campaign.id, campaign.budgetHash)
    }

    /**
     * Rejects publication unless the budget prévisionnel is balanced.
     *
     * Exact mirror of the `budgetBalanced` predicate in `app/src/components/campaign/PrePublishModal.tsx`
     * (rule 8), **tolerance included**: both sides must be non-empty and differ by strictly less than
     * one euro. A looser backend would let the publish button enable and then answer 422; a stricter
     * one would reject a campaign the UI declared publishable.
     *
     * `campaign.budgetSections` is initialised in the caller's transaction — [budgetHasher] walks the
     * same collection right after.
     */
    private fun requireBalancedBudget(campaign: Campaign) {
        val expenses = sumBudgetSide(campaign, BudgetSide.EXPENSE)
        val revenues = sumBudgetSide(campaign, BudgetSide.REVENUE)
        val balanced = expenses > BigDecimal.ZERO &&
            revenues > BigDecimal.ZERO &&
            (revenues - expenses).abs() < BigDecimal.ONE
        if (!balanced) {
            throw UnprocessableEntityException(
                "Budget prévisionnel must be balanced before publishing (expenses=$expenses, revenues=$revenues)"
            )
        }
    }

    private fun sumBudgetSide(campaign: Campaign, side: BudgetSide): BigDecimal =
        campaign.budgetSections
            .filter { it.side == side }
            .flatMap { it.items }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }

    /**
     * Enqueues on-chain jobs for the given status transition.
     * Must be called after the campaign has been saved so [Campaign.id] is stable.
     *
     * DRAFT→LIVE has no arm here: it used to enqueue CREATE_CAMPAIGN/PUBLISH_CAMPAIGN gated on the
     * association's Monerium wallet address, the only source of an on-chain association address.
     * Monerium was removed (V67) with no replacement wallet-provisioning mechanism, so publishing a
     * campaign is now off-chain only — see `.tasks/todo.md` history for the removal scope.
     */
    private fun enqueueForTransition(from: CampaignStatus, to: CampaignStatus, campaign: Campaign) {
        val id = campaign.id!!
        when {
            to == CampaignStatus.PAUSED ->
                outbox.enqueue(OnchainJobAction.PAUSE_CAMPAIGN, CampaignIdPayload(id), null)
            from == CampaignStatus.PAUSED && to == CampaignStatus.LIVE ->
                outbox.enqueue(OnchainJobAction.UNPAUSE_CAMPAIGN, CampaignIdPayload(id), null)
            to == CampaignStatus.CANCELLED ->
                outbox.enqueue(OnchainJobAction.CANCEL_CAMPAIGN, CampaignIdPayload(id), null)
            to == CampaignStatus.COMPLETED || to == CampaignStatus.ENDED ->
                outbox.enqueue(OnchainJobAction.COMPLETE_CAMPAIGN, CampaignIdPayload(id), null)
            to == CampaignStatus.REVERT_REQUESTED ->
                outbox.enqueue(OnchainJobAction.REVERT_CAMPAIGN_TO_DRAFT, CampaignIdPayload(id), "REVERT_CAMPAIGN_TO_DRAFT:$id")
        }
    }
}

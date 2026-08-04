package org.commonlink.service

import org.commonlink.config.MollieProperties
import org.commonlink.dto.CreateGuestDonationRequest
import org.commonlink.dto.CreateGuestDonationResponse
import org.commonlink.dto.DonationPublicStatus
import org.commonlink.dto.DonationStatusDto
import org.commonlink.dto.PublicLandingDto
import org.commonlink.dto.PublicWidgetDto
import org.commonlink.dto.buildBudgetProjection
import org.commonlink.dto.toDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.MolliePaymentException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.commonlink.repository.MollieConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Public widget service — resolves tokens and orchestrates guest donation creation.
 */
@Service
class PublicWidgetService(
    private val associationProfileRepository: AssociationProfileRepository,
    private val donationRepository: DonationRepository,
    private val donorProfileRepository: DonorProfileRepository,
    private val guestDonorService: GuestDonorService,
    private val mollieClient: MollieClient,
    private val mollieProperties: MollieProperties,
    private val donationService: DonationService,
    private val mollieConnectionRepository: MollieConnectionRepository,
    private val mollieConnectTokenManager: MollieConnectTokenManager,
    private val taxRateService: TaxRateService,
    private val landingPreviewTokenService: LandingPreviewTokenService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getWidget(widgetToken: String): PublicWidgetDto {
        val (association, campaign) = resolveWidget(widgetToken)
        return PublicWidgetDto(
            associationName = association.name,
            campaignId = campaign.id!!,
            campaignName = campaign.name,
            campaignEmoji = campaign.emoji,
            campaignDescription = campaign.description,
            goal = campaign.goal,
            raised = campaign.raised,
            campaignCoverImage = campaign.coverImage,
            widgetAllowedOrigin = association.widgetAllowedOrigin,
        )
    }

    /**
     * Orchestrates a guest donation:
     * 1. Resolves widget token to a LIVE campaign
     * 2. Provisions (or retrieves) the guest donor
     * 3. Creates a Mollie hosted-checkout payment
     * 4. Persists a pending [org.commonlink.entity.Donation] with identity snapshot
     *
     * The donation remains pending until confirmed by the Mollie webhook (B6).
     * The Mollie paymentId is returned to the frontend for sessionStorage before redirect.
     */
    fun createDonation(widgetToken: String, request: CreateGuestDonationRequest): CreateGuestDonationResponse {
        val (association, campaign) = resolveWidget(widgetToken)

        val assocToken = resolveMollieToken(association)
        val assocProfileId = mollieClient.getFirstProfileId(assocToken)

        // Provision guest donor (idempotent by email)
        val donorProfile = guestDonorService.findOrCreateGuestDonor(request.donorEmail, request.donorFullName)

        // Sync display preferences (anonymous flag, display name) — last donation wins
        if (donorProfile.anonymous != request.anonymousDisplay || donorProfile.displayName != request.donorFullName) {
            donorProfile.anonymous = request.anonymousDisplay
            donorProfile.displayName = request.donorFullName
            donorProfileRepository.save(donorProfile)
        }

        val cleanSourceSite = sanitizeSourceSite(request.sourceSite)
        val safeLocale = request.locale?.takeIf { it.matches(Regex("[a-z]{2}")) } ?: "fr"
        val encodedSource = cleanSourceSite?.let { URLEncoder.encode(it, StandardCharsets.UTF_8) }
        val amountCents = request.amount.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        val idempotencyKey = buildIdempotencyKey(widgetToken, donorProfile.id!!, amountCents, mollieProperties.webhookUrl)

        val base = "${mollieProperties.redirectBaseUrl}/$safeLocale/embed/donate/$widgetToken/return"
        val redirectUrl = if (encodedSource != null) "$base?source=$encodedSource" else base
        val cancelUrl = if (encodedSource != null) "$base?cancelled=true&source=$encodedSource" else "$base?cancelled=true"
        val description = "Don - ${campaign.name}".take(255)

        logger.info(
            "Creating Mollie payment for widget={} campaign={} amount={}",
            widgetToken, campaign.id, request.amount
        )

        val molliePayment = mollieClient.createPayment(
            amount = request.amount,
            currency = "EUR",
            description = description,
            redirectUrl = redirectUrl,
            cancelUrl = cancelUrl,
            webhookUrl = mollieProperties.webhookUrl,
            metadata = mapOf(
                "campaignId" to campaign.id!!.toString(),
                "donorProfileId" to donorProfile.id.toString(),
                "widgetToken" to widgetToken,
            ),
            idempotencyKey = idempotencyKey,
            bearerToken = assocToken,
            profileId = assocProfileId,
        )

        val identity = DonorIdentitySnapshot(
            fullName = request.donorFullName,
            addressLine1 = request.donorAddressLine1,
            addressLine2 = request.donorAddressLine2,
            postalCode = request.donorPostalCode,
            city = request.donorCity,
            country = request.donorCountry,
        )

        donationService.initiatePendingDonation(
            providerRef = "mollie:${molliePayment.id}",
            donorProfileId = donorProfile.id,
            campaignId = campaign.id,
            amount = request.amount,
            sourceSite = cleanSourceSite,
            identity = identity,
        )

        val checkoutUrl = molliePayment.checkoutUrl
            ?: throw MolliePaymentException("Mollie returned no checkout URL for payment ${molliePayment.id}")

        logger.info("Pending donation created — mollieId={} campaign={}", molliePayment.id, campaign.id)
        return CreateGuestDonationResponse(checkoutUrl = checkoutUrl, paymentId = molliePayment.id)
    }

    /**
     * Returns the full landing page data for an association's active widget campaign.
     *
     * Exposes association identity, campaign details, expense budget projection, milestones,
     * and the applicable fiscal tax reduction rate. No internal IDs or sensitive fields.
     *
     * @param widgetToken Opaque public token identifying the association's widget.
     * @return [PublicLandingDto] with all donor-safe campaign and association data.
     * @throws [NotFoundException] if the token is unknown or no destination campaign is configured.
     * @throws [ConflictException] if the destination campaign is not LIVE.
     */
    /**
     * Landing page projection for a widget token.
     *
     * @param widgetToken Opaque public widget token.
     * @param previewToken Optional preview token; when it belongs to the association owning
     *   [widgetToken], the LIVE requirement on the destination campaign is lifted. Any other value
     *   (absent, expired, forged, issued for another association) behaves as if no token was passed.
     */
    @Transactional(readOnly = true)
    fun getLanding(widgetToken: String, previewToken: String? = null): PublicLandingDto {
        val (association, campaign) = resolveLanding(widgetToken, previewToken)
        val budget = buildBudgetProjection(campaign.budgetSections)
        val milestones = campaign.milestones
            .sortedBy { it.sortOrder }
            .map { it.toDto() }
        return PublicLandingDto(
            associationName = association.name,
            associationRna = association.identifier,
            addressLine1 = association.addressLine1,
            city = association.city,
            postalCode = association.postalCode,
            legalObject = association.legalObject,
            creationYear = association.creationYear,
            taxReductionRate = taxRateService.taxReductionRate(association),
            campaignId = campaign.id!!,
            campaignName = campaign.name,
            campaignEmoji = campaign.emoji,
            campaignDescription = campaign.description,
            campaignReason = campaign.reason,
            campaignImpactGoals = campaign.impactGoals,
            campaignCategory = campaign.category,
            goal = campaign.goal,
            raised = campaign.raised,
            coverImage = campaign.coverImage,
            budget = budget,
            budgetHash = campaign.budgetHash,
            milestones = milestones,
            widgetAllowedOrigin = association.widgetAllowedOrigin,
            landingTheme = association.landingTheme,
            landingLogo = association.landingLogo,
            showProject = association.landingShowProject,
            showTransparency = association.landingShowTransparency,
            showTrust = association.landingShowTrust,
            // Only ever false behind a valid preview token: resolveLanding would have thrown otherwise.
            donationsEnabled = campaign.status == CampaignStatus.LIVE,
        )
    }

    /**
     * Returns the public confirmation status of a donation by its Mollie payment ID.
     *
     * Used by the return page to poll for confirmation.
     * Leaks no internal data — only PENDING or CONFIRMED.
     *
     * @throws [NotFoundException] if no donation exists for this paymentId.
     */
    fun getDonationStatus(paymentId: String): DonationStatusDto {
        val donation = donationRepository.findByProviderRef("mollie:$paymentId")
            ?: throw NotFoundException("Payment not found")
        val status = if (donation.confirmedAt != null) DonationPublicStatus.CONFIRMED else DonationPublicStatus.PENDING
        return DonationStatusDto(status)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun resolveMollieToken(association: AssociationProfile): String {
        val connection = mollieConnectionRepository.findByAssociationId(association.id!!)
            ?: throw ConflictException("L'association n'a pas de connexion Mollie")
        // Same predicate as the publish and widget-enable gates — see MollieConnection.canCollectDonations.
        if (!connection.canCollectDonations())
            throw ConflictException("L'association ne peut pas encore recevoir de paiements")
        return try {
            mollieConnectTokenManager.getValidAccessToken(association.id!!)
        } catch (e: IllegalStateException) {
            throw ConflictException("Connexion Mollie de l'association interrompue")
        }
    }

    /**
     * Same resolution as [resolveWidget], except that a valid preview token belonging to the owning
     * association lifts the LIVE requirement.
     *
     * Deliberately separate from [resolveWidget]: the donation paths must keep refusing a non-LIVE
     * campaign unconditionally, so a preview token can never be turned into a way to collect money.
     * An unknown widget token stays a 404 even with a valid preview token — the preview grants a
     * relaxation of one check, never the existence of a widget.
     */
    private fun resolveLanding(
        widgetToken: String,
        previewToken: String?,
    ): Pair<AssociationProfile, Campaign> {
        val association = associationProfileRepository.findByWidgetToken(widgetToken)
            .orElseThrow { NotFoundException("Widget not found") }
        val campaign = association.widgetDestinationCampaign
            ?: throw NotFoundException("No destination campaign configured")
        if (campaign.status != CampaignStatus.LIVE) {
            val previewFor = landingPreviewTokenService.resolveAssociationId(previewToken)
            if (previewFor == null || previewFor != association.id) {
                logger.debug("Landing {} has non-LIVE campaign {} ({})", widgetToken, campaign.id, campaign.status)
                throw ConflictException("Campaign is not accepting donations")
            }
            logger.debug("Landing {} served in preview mode (campaign {})", widgetToken, campaign.status)
        }
        return association to campaign
    }

    private fun resolveWidget(widgetToken: String): Pair<AssociationProfile, Campaign> {
        val association = associationProfileRepository.findByWidgetToken(widgetToken)
            .orElseThrow { NotFoundException("Widget not found") }
        val campaign = association.widgetDestinationCampaign
            ?: throw NotFoundException("No destination campaign configured")
        if (campaign.status != CampaignStatus.LIVE) {
            logger.debug("Widget {} has non-LIVE campaign {} ({})", widgetToken, campaign.id, campaign.status)
            throw ConflictException("Campaign is not accepting donations")
        }
        return association to campaign
    }

    /**
     * Sanitizes [raw] to a safe redirect URL: enforces http/https, strips fragment, preserves path+query.
     * The value is untrusted (auto-declared by the widget snippet) — never interpolate raw.
     */
    private fun sanitizeSourceSite(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val uri = URI(raw.trim())
            val scheme = uri.scheme?.takeIf { it == "http" || it == "https" } ?: return null
            if (uri.host.isNullOrBlank()) return null
            URI(scheme, uri.userInfo, uri.host, uri.port, uri.path, uri.query, null).toString().take(2048)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Stable idempotency key for a given widget+donor+amount within a 1-hour window.
     * Protects against network retries before a providerRef exists locally.
     */
    private fun buildIdempotencyKey(widgetToken: String, donorProfileId: UUID, amountCents: Long, webhookUrl: String): String {
        val hourBucket = System.currentTimeMillis() / 3_600_000L
        val input = "$widgetToken|$donorProfileId|$amountCents|${webhookUrl.ifBlank { "none" }}|$hourBucket"
        return UUID.nameUUIDFromBytes(input.toByteArray(Charsets.UTF_8)).toString()
    }
}

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
import org.commonlink.entity.AssociationStatus
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.entity.Donation
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
    private val freezeScreeningDonationService: FreezeScreeningDonationService,
    private val donationCapService: DonationCapService,
    private val mollieWebhookService: MollieWebhookService,
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
            remainingCapacity = donationCapService.remainingCapacity(campaign),
            gtmContainerId = association.gtmContainerId,
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

        // Collection cap — refuse before anything is created. A donation above the cap could only be
        // refunded, and a refund after collection is precisely what must not happen: the receipt is
        // numbered, hashed on-chain and emailed. Checked here rather than further down so a refused
        // attempt leaves no guest profile and no screening journal entry behind.
        donationCapService.requireWithinCap(campaign, request.amount)

        // Provision guest donor (idempotent by email)
        val resolvedDonor = guestDonorService.findOrCreateGuestDonor(request.donorEmail, request.donorFullName)
        val donorProfile = resolvedDonor.profile

        // Sync display preferences (anonymous flag, display name) — last donation wins.
        // Guest profiles only: this endpoint is unauthenticated and the e-mail in the body proves
        // nothing. Applying these fields to a real donor's profile let anyone rename them and, worse,
        // flip `anonymous` to false — overriding someone else's privacy choice from the outside
        // (security audit 2026-08-20, M1).
        if (resolvedDonor.ownedByGuest &&
            (donorProfile.anonymous != request.anonymousDisplay || donorProfile.displayName != request.donorFullName)
        ) {
            donorProfile.anonymous = request.anonymousDisplay
            donorProfile.displayName = request.donorFullName
            donorProfileRepository.save(donorProfile)
        }

        // Build identity snapshot before freeze check — required to screen the donor
        val identity = DonorIdentitySnapshot(
            fullName = request.donorFullName,
            addressLine1 = request.donorAddressLine1,
            addressLine2 = request.donorAddressLine2,
            postalCode = request.donorPostalCode,
            city = request.donorCity,
            country = request.donorCountry,
            birthDate = request.donorBirthDate,
        )

        // LCB-FT art. L.561-5 — screen donor against asset-freeze register before any payment
        val freezeOutcome = freezeScreeningDonationService.runFreezeCheck(
            associationId = association.id!!,
            donorProfileId = donorProfile.id!!,
            identity = identity,
        )
        if (freezeOutcome != ScreeningOutcome.CLEAR) {
            throw ConflictException(FREEZE_BLOCK_MESSAGE)
        }

        val assocToken = resolveMollieToken(association)
        val assocProfileId = mollieClient.getFirstProfileId(assocToken)

        val cleanSourceSite = sanitizeSourceSite(request.sourceSite)
        val safeLocale = request.locale?.takeIf { it.matches(Regex("[a-z]{2}")) } ?: "fr"
        val encodedSource = cleanSourceSite?.let { URLEncoder.encode(it, StandardCharsets.UTF_8) }
        val amountCents = request.amount.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        val idempotencyKey = buildIdempotencyKey(widgetToken, donorProfile.id!!, amountCents, mollieProperties.webhookUrl)

        // Minted here, not read off molliePayment.id below: Mollie's own payment id does not exist
        // yet at this point (it is only known once createPayment returns), so it cannot be embedded
        // in the URL passed *into* that call. This id is what the /return page polls status by —
        // never providerRef, never an internal donor/campaign id.
        val publicRef = UUID.randomUUID()

        val base = "${mollieProperties.redirectBaseUrl}/$safeLocale/embed/donate/$widgetToken/return"
        // Carried on the success redirect only — the dataLayer `purchase` push on /return needs it,
        // the cancelled path never fires that event so cancelUrl stays free of donation data.
        val trackingQuery = "ref=$publicRef" +
            "&amount=${request.amount.toPlainString()}" +
            "&currency=EUR" +
            "&campaignId=${campaign.id}" +
            "&campaignName=${URLEncoder.encode(campaign.name, StandardCharsets.UTF_8)}" +
            "&associationName=${URLEncoder.encode(association.name, StandardCharsets.UTF_8)}" +
            "&anonymous=${request.anonymousDisplay}"
        val redirectUrl = if (encodedSource != null) "$base?$trackingQuery&source=$encodedSource" else "$base?$trackingQuery"
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

        donationService.initiatePendingDonation(
            providerRef = "mollie:${molliePayment.id}",
            donorProfileId = donorProfile.id,
            campaignId = campaign.id,
            amount = request.amount,
            sourceSite = cleanSourceSite,
            identity = identity,
            publicRef = publicRef,
        )

        val checkoutUrl = molliePayment.checkoutUrl
            ?: throw MolliePaymentException("Mollie returned no checkout URL for payment ${molliePayment.id}")

        logger.info("Pending donation created — mollieId={} campaign={}", molliePayment.id, campaign.id)
        return CreateGuestDonationResponse(checkoutUrl = checkoutUrl, paymentId = molliePayment.id, publicRef = publicRef)
    }

    companion object {
        // Indiscernability constraint (LCB-FT): HIT and UNAVAILABLE throw the same exception with
        // the same message — callers and tests may assert equality on this constant.
        internal const val FREEZE_BLOCK_MESSAGE = "Le service est temporairement indisponible."
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
            startDate = campaign.startDate,
            endDate = campaign.endDate,
            coverImage = campaign.coverImage,
            budget = budget,
            budgetHash = campaign.budgetHash,
            milestones = milestones,
            widgetAllowedOrigin = association.widgetAllowedOrigin,
            landingTheme = association.landingTheme,
            landingLogo = association.landingLogo,
            // Only ever false behind a valid preview token: resolveLanding would have thrown otherwise.
            donationsEnabled = campaign.status == CampaignStatus.LIVE,
            remainingCapacity = donationCapService.remainingCapacity(campaign),
            gtmContainerId = association.gtmContainerId,
        )
    }

    /**
     * Returns the public confirmation status of a donation by its opaque [Donation.publicRef].
     *
     * Used by the `/return` page to poll for confirmation before pushing the `purchase` dataLayer
     * event. Leaks no internal data: no providerRef, no donor/campaign id — [method] is the only
     * addition beyond PENDING/CONFIRMED, and it is set only once confirmed (the donor already knows
     * which method they used, so nothing new is disclosed).
     *
     * While still pending, makes a best-effort live Mollie status check ([tryLiveConfirm]) instead of
     * waiting on the webhook: Mollie confirms a card payment in seconds, but webhook *delivery* can lag
     * 1-30s, and the `/return` page only polls for a few seconds before giving up and redirecting.
     *
     * @throws [NotFoundException] if no donation exists for this ref.
     */
    fun getDonationStatus(publicRef: UUID): DonationStatusDto {
        var donation = donationRepository.findByPublicRef(publicRef)
            ?: throw NotFoundException("Payment not found")

        if (donation.confirmedAt == null) {
            donation = tryLiveConfirm(publicRef, donation.providerRef) ?: donation
        }

        val confirmed = donation.confirmedAt != null
        return DonationStatusDto(
            status = if (confirmed) DonationPublicStatus.CONFIRMED else DonationPublicStatus.PENDING,
            method = donation.paymentMethod.takeIf { confirmed },
        )
    }

    /**
     * Re-fetches [providerRef]'s payment status from Mollie right now and, if paid, confirms it —
     * reusing [MollieWebhookService.handleWebhook] so campaign credit / receipt / on-chain enqueue
     * always go through the exact same path regardless of whether this call or the later webhook
     * confirms first (the latter becomes a no-op via [DonationService.recordPayment]'s confirmedAt guard).
     *
     * Never propagates a failure: a Mollie outage, a broken Connect token, or a non-Mollie
     * [providerRef] must not break donor-facing status polling. Falls back to the caller's own
     * (unconfirmed) copy of the donation on any error.
     */
    private fun tryLiveConfirm(publicRef: UUID, providerRef: String): Donation? {
        if (!providerRef.startsWith("mollie:")) return null
        return try {
            mollieWebhookService.handleWebhook(providerRef.removePrefix("mollie:"))
            donationRepository.findByPublicRef(publicRef)
        } catch (e: Exception) {
            logger.debug("Live Mollie status check failed for publicRef={}", publicRef, e)
            null
        }
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
        // SUSPENDED (IC-44 — confirmed campaign report) blocks the whole association's portfolio,
        // same generic message as a non-LIVE campaign: nothing here should tell the caller which
        // check failed. ALERT (report merely received, not yet ruled on) is deliberately not
        // gated — see AssociationStatus KDoc.
        if (association.status == AssociationStatus.SUSPENDED) {
            logger.debug("Landing {} has SUSPENDED association {}", widgetToken, association.id)
            throw ConflictException("Campaign is not accepting donations")
        }
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
        // Same SUSPENDED gate as resolveLanding — see the comment there.
        if (association.status == AssociationStatus.SUSPENDED) {
            logger.debug("Widget {} has SUSPENDED association {}", widgetToken, association.id)
            throw ConflictException("Campaign is not accepting donations")
        }
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

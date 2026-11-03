package org.commonlink.service

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
import org.commonlink.exception.CollectionUnavailableException
import org.commonlink.exception.ConflictException
import org.commonlink.exception.NotFoundException
import org.commonlink.payment.CheckoutPayer
import org.commonlink.payment.CheckoutRequest
import org.commonlink.payment.PaymentProviderRegistry
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
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
    private val donationService: DonationService,
    private val taxRateService: TaxRateService,
    private val landingPreviewTokenService: LandingPreviewTokenService,
    private val freezeScreeningDonationService: FreezeScreeningDonationService,
    private val donationCapService: DonationCapService,
    private val legalAcceptanceService: LegalAcceptanceService,
    private val paymentProviderRegistry: PaymentProviderRegistry,
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
            collectAvailable = isCollectAvailable(association),
        )
    }

    /**
     * Whether [association]'s payment provider can collect right now. Database reads only, no network call (contract
     * of [org.commonlink.payment.PaymentProvider.readiness]) — safe on these unauthenticated public GETs. A provider
     * with no implementation yet is not available.
     */
    private fun isCollectAvailable(association: AssociationProfile): Boolean =
        paymentProviderRegistry.findForAssociation(association)?.readiness(association.id!!)?.isReady ?: false

    /**
     * Orchestrates a guest donation:
     * 1. Resolves widget token to a LIVE campaign
     * 2. Provisions (or retrieves) the guest donor
     * 3. Creates a hosted-checkout payment through the association's [org.commonlink.payment.PaymentProvider]
     * 4. Persists a pending [org.commonlink.entity.Donation] with identity snapshot
     *
     * The donation remains pending until the provider confirms the payment (notification or live re-read).
     * The provider's payment id is returned to the frontend for sessionStorage before redirect.
     *
     * @throws CollectionUnavailableException when the association's provider cannot collect right now — checked
     *   first, before anything is created.
     */
    fun createDonation(widgetToken: String, request: CreateGuestDonationRequest): CreateGuestDonationResponse {
        val (association, campaign) = resolveWidget(widgetToken)

        // Provider readiness first: a refused attempt must leave no guest donor and no screening journal entry.
        if (!isCollectAvailable(association)) throw CollectionUnavailableException()

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

        val cleanSourceSite = sanitizeSourceSite(request.sourceSite)
        val safeLocale = request.locale?.takeIf { it.matches(Regex("[a-z]{2}")) } ?: "fr"

        // Minted here, not read off the provider's payment id: that id does not exist yet at this
        // point (it is only known once the checkout is created), so it cannot be embedded in the
        // return URL passed *into* that call. This id is what the /return page polls status by —
        // never providerRef, never an internal donor/campaign id.
        val publicRef = UUID.randomUUID()

        // Refuses with a 409 when the association cannot collect, and fails before anything below is
        // written when the provider returns no payment page — no orphan pending donation or proof.
        val session = paymentProviderRegistry.forAssociation(association).createCheckout(
            CheckoutRequest(
                associationId = association.id!!,
                campaignId = campaign.id!!,
                campaignName = campaign.name,
                donorProfileId = donorProfile.id!!,
                amount = request.amount,
                publicRef = publicRef,
                widgetToken = widgetToken,
                locale = safeLocale,
                sourceSite = cleanSourceSite,
                payer = CheckoutPayer(
                    email = request.donorEmail,
                    fullName = request.donorFullName,
                    addressLine1 = request.donorAddressLine1,
                    postalCode = request.donorPostalCode,
                    city = request.donorCity,
                    country = request.donorCountry,
                ),
            )
        )

        val donation = donationService.initiatePendingDonation(
            providerRef = session.providerRef,
            donorProfileId = donorProfile.id,
            campaignId = campaign.id,
            amount = request.amount,
            sourceSite = cleanSourceSite,
            identity = identity,
            publicRef = publicRef,
        )
        // Art. 1740 A CGI proof of acceptance — a donation is a transactional act, so this is
        // recorded fresh every time rather than reused across future donations. Idempotent per
        // donation: a retried request (same providerRef, same pending Donation row) writes nothing
        // twice — see LegalAcceptanceService.recordDonorAcceptance.
        legalAcceptanceService.recordDonorAcceptance(
            donorProfileId = donorProfile.id!!,
            donationId = donation.id!!,
            campaignId = campaign.id!!,
            signerName = request.donorFullName,
            signerEmail = request.donorEmail,
        )

        logger.info("Pending donation created — paymentId={} campaign={}", session.providerPaymentId, campaign.id)
        return CreateGuestDonationResponse(
            checkoutUrl = session.redirectUrl,
            paymentId = session.providerPaymentId,
            publicRef = publicRef,
        )
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
            campaignUpdatedAt = campaign.updatedAt,
            budget = budget,
            budgetHash = campaign.budgetHash,
            milestones = milestones,
            widgetAllowedOrigin = association.widgetAllowedOrigin,
            landingTheme = association.landingTheme,
            landingLogo = association.landingLogo,
            // Not LIVE only behind a valid preview token: resolveLanding would have thrown otherwise.
            donationsEnabled = campaign.status == CampaignStatus.LIVE && isCollectAvailable(association),
            remainingCapacity = donationCapService.remainingCapacity(campaign),
            gtmContainerId = association.gtmContainerId,
        )
    }

    /**
     * Returns the public confirmation status of a donation by its opaque [Donation.publicRef].
     *
     * Used by the `/return` page to wait for confirmation before sending the donor back to the
     * association's page, then by the CommonLink iframe on that page to read the confirmed values of
     * the GA4 `purchase` event. Before confirmation only the status is returned; once confirmed, the
     * donation values (amount, campaign, association, anonymity choice, payment method) — never the
     * donor's identity, never providerRef.
     *
     * While still pending, makes a best-effort live status check at the payment provider
     * ([tryLiveConfirm]) instead of waiting on its notification: a card payment is confirmed in seconds,
     * but notification *delivery* can lag 1-30s, and the `/return` page only polls for a few seconds
     * before giving up and redirecting.
     *
     * @throws [NotFoundException] if no donation exists for this ref.
     */
    fun getDonationStatus(publicRef: UUID): DonationStatusDto {
        var donation = donationRepository.findByPublicRef(publicRef)
            ?: throw NotFoundException("Payment not found")

        if (donation.confirmedAt == null) {
            donation = tryLiveConfirm(publicRef, donation.providerRef) ?: donation
        }

        if (donation.confirmedAt == null) return DonationStatusDto(DonationPublicStatus.PENDING)
        return DonationStatusDto(
            status = DonationPublicStatus.CONFIRMED,
            method = donation.paymentMethod,
            amount = donation.amount,
            // Every public-widget donation is created in EUR — see createDonation.
            currency = "EUR",
            campaignId = donation.campaign.id,
            campaignName = donation.campaign.name,
            associationName = donation.campaign.association.name,
            anonymous = donation.donor.anonymous,
        )
    }

    /**
     * Re-reads [providerRef]'s payment at the provider that created it right now and, if paid, confirms
     * it — through [org.commonlink.payment.PaymentProvider.refreshPayment], which reuses the provider's
     * notification path so campaign credit / receipt / on-chain enqueue always go through the exact same
     * code regardless of whether this call or the later notification confirms first (the latter becomes
     * a no-op via [DonationService.recordPayment]'s confirmedAt guard).
     *
     * The provider is resolved from the [providerRef] prefix, never from the association's current
     * provider. Never propagates a failure: a provider outage, a broken link, or an unknown prefix must
     * not break donor-facing status polling. Falls back to the caller's own (unconfirmed) copy of the
     * donation on any error.
     */
    private fun tryLiveConfirm(publicRef: UUID, providerRef: String): Donation? {
        val provider = paymentProviderRegistry.forProviderRef(providerRef) ?: return null
        return try {
            provider.refreshPayment(providerRef)
            donationRepository.findByPublicRef(publicRef)
        } catch (e: Exception) {
            logger.debug("Live payment status check failed for publicRef={}", publicRef, e)
            null
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

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
}

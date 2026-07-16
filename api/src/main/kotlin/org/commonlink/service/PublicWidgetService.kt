package org.commonlink.service

import org.commonlink.config.MollieProperties
import org.commonlink.dto.CreateGuestDonationRequest
import org.commonlink.dto.CreateGuestDonationResponse
import org.commonlink.dto.DonationPublicStatus
import org.commonlink.dto.DonationStatusDto
import org.commonlink.dto.PublicWidgetDto
import org.commonlink.entity.AssociationProfile
import org.commonlink.entity.Campaign
import org.commonlink.entity.CampaignStatus
import org.commonlink.exception.ConflictException
import org.commonlink.exception.MolliePaymentException
import org.commonlink.exception.NotFoundException
import org.commonlink.repository.AssociationProfileRepository
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.DonorProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
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
    private val mollieClient: MollieClient,
    private val mollieProperties: MollieProperties,
    private val donationService: DonationService,
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

        // Provision guest donor (idempotent by email)
        val donorProfile = guestDonorService.findOrCreateGuestDonor(request.donorEmail, request.donorFullName)

        // Sync display preferences (anonymous flag, display name) — last donation wins
        if (donorProfile.anonymous != request.anonymousDisplay || donorProfile.displayName != request.donorFullName) {
            donorProfile.anonymous = request.anonymousDisplay
            donorProfile.displayName = request.donorFullName
            donorProfileRepository.save(donorProfile)
        }

        val cleanSourceSite = sanitizeSourceSite(request.sourceSite)
        val amountCents = request.amount.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
        val idempotencyKey = buildIdempotencyKey(widgetToken, donorProfile.id!!, amountCents, mollieProperties.webhookUrl)

        val redirectUrl = "${mollieProperties.redirectBaseUrl}/embed/donate/$widgetToken/return"
        val cancelUrl = "${mollieProperties.redirectBaseUrl}/embed/donate/$widgetToken"
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
     * Extracts scheme+host only from [raw]; returns null if parsing fails or input is blank.
     * The value is untrusted (auto-declared by the widget snippet) — never interpolate raw.
     */
    private fun sanitizeSourceSite(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val uri = URI(raw.trim())
            val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return null
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            "$scheme://$host".take(255)
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

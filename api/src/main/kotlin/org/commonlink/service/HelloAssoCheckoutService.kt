package org.commonlink.service

import org.commonlink.config.MollieProperties
import org.commonlink.dto.HelloAssoCheckoutIntentJson
import org.commonlink.dto.HelloAssoInitCheckoutJson
import org.commonlink.entity.ComplianceAuditSubjectType
import org.commonlink.entity.Donation
import org.commonlink.entity.HelloAssoConnectionState
import org.commonlink.exception.BadGatewayException
import org.commonlink.exception.ConflictException
import org.commonlink.exception.HelloAssoException
import org.commonlink.exception.HelloAssoNotEligibleException
import org.commonlink.exception.HelloAssoUnauthorizedException
import org.commonlink.exception.NotFoundException
import org.commonlink.payment.CheckoutRequest
import org.commonlink.payment.CheckoutSession
import org.commonlink.payment.PaymentProviderId
import org.commonlink.repository.DonationRepository
import org.commonlink.repository.HelloAssoConnectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.RoundingMode
import java.net.URLEncoder
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.util.UUID

/** What a HelloAsso re-read did to a donation. */
enum class HelloAssoPaymentOutcome {
    /** Authorized (or registered offline) payment: the donation is confirmed (idempotent). */
    CONFIRMED,

    /** Nothing paid yet, or refused / canceled / abandoned: the donation stays pending. */
    PENDING,

    /** Refunded, being refunded or contested: alert + compliance journal, the donation is left as is. */
    REVERSED,
}

/**
 * HelloAsso checkouts of real donations (flow §4) and their re-read (flow §6) — the single place where a HelloAsso
 * payment state turns into a CommonLink donation state, whoever asks (notification, return page, reconciler).
 *
 * Never trusts the browser nor a notification body: the payment state is always re-read with
 * `GET checkout-intents/{id}`.
 */
@Service
class HelloAssoCheckoutService(
    private val connectionRepository: HelloAssoConnectionRepository,
    private val donationRepository: DonationRepository,
    private val donationService: DonationService,
    private val tokenManager: HelloAssoTokenManager,
    private val gateway: HelloAssoGateway,
    private val breaker: HelloAssoConnectionBreaker,
    private val eligibilityService: HelloAssoEligibilityService,
    private val technicalAlertService: TechnicalAlertService,
    private val complianceAuditLogService: ComplianceAuditLogService,
    private val mollieProperties: MollieProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Same refusal as Mollie's when the association cannot collect. */
        const val CANNOT_COLLECT = "L'association ne peut pas encore recevoir de paiements"
        private const val ITEM_NAME_MAX = 250
        private val PAID = setOf("Authorized", "Registered")
        private val REVERSED_STATES = setOf("Refunded", "Refunding", "Contested")
    }

    /**
     * Creates the HelloAsso checkout-intent of a real donation. The return page is the same as Mollie's
     * (`/{locale}/embed/donate/{widgetToken}/return`); `lang=en` is added to the HelloAsso page for English donors.
     *
     * @throws ConflictException when the association cannot collect (not linked, broken, not eligible, or HelloAsso
     *   answers 409 — the latter also records the lost eligibility and re-reads it).
     * @throws BadGatewayException when HelloAsso is unavailable or returns no payment page.
     */
    fun createCheckout(request: CheckoutRequest): CheckoutSession {
        val connection = connectionRepository.findByAssociationId(request.associationId)
        if (connection == null || connection.state != HelloAssoConnectionState.ACTIVE || !connection.canCollect) {
            throw ConflictException(CANNOT_COLLECT)
        }
        val slug = connection.organizationSlug ?: throw ConflictException(CANNOT_COLLECT)
        val accessToken = accessToken(request.associationId)
        val response = try {
            gateway.createCheckoutIntent(accessToken, slug, body(request))
        } catch (ex: HelloAssoNotEligibleException) {
            log.warn("HelloAsso refused a checkout for association {}: organization not eligible", request.associationId)
            eligibilityService.store(request.associationId, canCollect = false)
            eligibilityService.refresh(request.associationId)
            throw ConflictException(CANNOT_COLLECT)
        } catch (ex: HelloAssoUnauthorizedException) {
            breaker.markBroken(request.associationId)
            throw ConflictException(CANNOT_COLLECT)
        } catch (ex: HelloAssoException) {
            log.warn("HelloAsso checkout failed for association {}: {}", request.associationId, ex.javaClass.simpleName)
            throw BadGatewayException("HelloAsso is unavailable")
        }
        val id = response.id
        val redirectUrl = response.redirectUrl?.takeIf { it.isNotBlank() }
        if (id == null || redirectUrl == null) throw BadGatewayException("HelloAsso returned no payment page")
        return CheckoutSession(
            providerRef = PaymentProviderId.HELLOASSO.refPrefix + id,
            redirectUrl = if (request.locale == "en") withLang(redirectUrl) else redirectUrl,
            providerPaymentId = id.toString(),
        )
    }

    /**
     * Re-reads the HelloAsso payment of [providerRef] (`helloasso:<checkoutIntentId>`) and applies its state.
     *
     * @throws NotFoundException when no donation carries [providerRef].
     * @throws HelloAssoException on a HelloAsso failure (a rejected token also marks the link BROKEN).
     */
    @Transactional
    fun refreshPayment(providerRef: String): HelloAssoPaymentOutcome {
        require(providerRef.startsWith(PaymentProviderId.HELLOASSO.refPrefix)) { "Not a HelloAsso payment reference" }
        val checkoutIntentId = providerRef.removePrefix(PaymentProviderId.HELLOASSO.refPrefix).toLongOrNull()
            ?: throw NotFoundException("Payment not found")
        val donation = donationRepository.findByProviderRef(providerRef) ?: throw NotFoundException("Payment not found")
        val associationId = donation.campaign.association.id!!
        val connection = connectionRepository.findByAssociationId(associationId)
            ?: throw IllegalStateException("No HelloAsso link for association $associationId")
        val slug = connection.organizationSlug ?: throw IllegalStateException("HelloAsso link without organization")
        val intent = try {
            gateway.getCheckoutIntent(accessToken(associationId), slug, checkoutIntentId)
        } catch (ex: HelloAssoUnauthorizedException) {
            breaker.markBroken(associationId)
            throw ex
        }
        return apply(donation, intent)
    }

    /**
     * Records on the link of [providerRef]'s association that a payment was confirmed without its notification
     * ([org.commonlink.payment.HelloAssoWebhookState.MISSING] until a notification arrives again).
     */
    @Transactional
    fun markWebhookMissing(providerRef: String) {
        val donation = donationRepository.findByProviderRef(providerRef) ?: return
        val connection = connectionRepository.findByAssociationId(donation.campaign.association.id!!) ?: return
        connection.webhookMissingAt = Instant.now()
        connection.updatedAt = Instant.now()
        connectionRepository.save(connection)
    }

    private fun apply(donation: Donation, intent: HelloAssoCheckoutIntentJson): HelloAssoPaymentOutcome {
        val payments = intent.order?.payments.orEmpty()
        val reversed = payments.firstOrNull { it.state in REVERSED_STATES }
        if (reversed != null && donation.confirmedAt != null) {
            reportReversal(donation, reversed.state!!)
            return HelloAssoPaymentOutcome.REVERSED
        }
        val paid = payments.firstOrNull { it.state in PAID } ?: return HelloAssoPaymentOutcome.PENDING
        // Already confirmed (resent notification, return page, reconciler): nothing more to do.
        if (donation.confirmedAt != null) return HelloAssoPaymentOutcome.CONFIRMED
        // Written before confirming: the receipt is rendered after commit and reads it.
        donation.paymentMethod = paid.paymentMeans
        donationRepository.save(donation)
        donationService.recordPayment(donation.providerRef, donation.donor.id!!, donation.campaign.id!!, donation.amount)
        return HelloAssoPaymentOutcome.CONFIRMED
    }

    private fun reportReversal(donation: Donation, state: String) {
        log.warn("HelloAsso reports donation {} as {}", donation.id, state)
        technicalAlertService.reportFailure(TechnicalAlertKind.PAYMENT_REVERSED, null, donation.providerRef, null)
        complianceAuditLogService.append(
            eventType = ComplianceAuditLogService.HELLOASSO_PAYMENT_REVERSED,
            subjectType = ComplianceAuditSubjectType.DONATION,
            payload = mapOf("providerRef" to donation.providerRef, "state" to state),
            subjectId = donation.id,
        )
    }

    /** A valid access token, or [ConflictException] once a rejected refresh token has marked the link BROKEN. */
    private fun accessToken(associationId: UUID): String =
        try {
            tokenManager.getValidAccessToken(associationId)
        } catch (ex: HelloAssoUnauthorizedException) {
            breaker.markBroken(associationId)
            throw ConflictException(CANNOT_COLLECT)
        } catch (ex: IllegalStateException) {
            throw ConflictException(CANNOT_COLLECT)
        } catch (ex: HelloAssoException) {
            throw BadGatewayException("HelloAsso is unavailable")
        }

    private fun body(request: CheckoutRequest): HelloAssoInitCheckoutJson {
        val cents = request.amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact()
        val base = "${mollieProperties.redirectBaseUrl}/${request.locale}/embed/donate/${request.widgetToken}/return"
        val source = request.sourceSite?.let { "&source=" + URLEncoder.encode(it, StandardCharsets.UTF_8) }.orEmpty()
        return HelloAssoInitCheckoutJson(
            totalAmount = cents,
            initialAmount = cents,
            itemName = "Don - ${request.campaignName}".take(ITEM_NAME_MAX),
            backUrl = "$base?cancelled=true$source",
            errorUrl = "$base?cancelled=true&type=error$source",
            returnUrl = "$base?ref=${request.publicRef}$source",
            containsDonation = true,
            payer = HelloAssoPayerValidator.build(request.payer),
            metadata = mapOf(
                "publicRef" to request.publicRef.toString(),
                "campaignId" to request.campaignId.toString(),
                "donorProfileId" to request.donorProfileId.toString(),
                "widgetToken" to request.widgetToken,
            ),
        )
    }

    private fun withLang(url: String) = url + (if ('?' in url) "&" else "?") + "lang=en"

}

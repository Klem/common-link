package org.commonlink.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Public profile for a non-profit association user.
 *
 * Created at registration time and linked one-to-one with the parent [User].
 * The [identifier] field stores the primary identifier — RNA (W-number, 10 chars) for JOAFE registrations,
 * SIREN (9 digits) for legacy registrations. Secondary [siren] field holds SIREN when both are known.
 *
 * KYC lifecycle is tracked via [verificationStatus]; documents are stored in [AssociationDocument].
 */
@Entity
@Table(name = "association_profiles")
class AssociationProfile(
    /** Auto-generated UUID primary key; null until the entity is persisted. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID? = null,

    /** The [User] account that owns this profile. The unique constraint enforces one profile per user. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    /** Official registered name of the association. */
    @Column(name = "name", nullable = false)
    val name: String,

    /** RNA identifier (e.g. W123456789, 10 characters) or SIREN for legacy registrations. Primary legal identifier. */
    @Column(name = "identifier", nullable = false, length = 20)
    val identifier: String,

    /** City where the association is headquartered. */
    @Column(name = "city")
    var city: String? = null,

    /** Postal code of the association's headquarters. */
    @Column(name = "postal_code")
    var postalCode: String? = null,

    /** Name of the primary contact person for the association. */
    @Column(name = "contact_name")
    var contactName: String? = null,

    /** Public description of the association's mission and activities. */
    @Column(name = "description")
    var description: String? = null,

    /** French SIREN number (9 digits). Nullable — JOAFE-registered associations may not have one. */
    @Column(name = "siren", length = 9)
    var siren: String? = null,

    /** Year the association was founded (e.g. 2018). */
    @Column(name = "creation_year")
    var creationYear: Short? = null,

    /** Public contact email for the association. */
    @Column(name = "contact_email", length = 255)
    var contactEmail: String? = null,

    /** Public contact phone number. */
    @Column(name = "phone", length = 30)
    var phone: String? = null,

    /** KYC verification lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    var verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,

    /** Reason provided by the admin when rejecting the KYC dossier. */
    @Column(name = "verification_rejection_reason", columnDefinition = "TEXT")
    var verificationRejectionReason: String? = null,

    /** Timestamp when the association last submitted their KYC documents. */
    @Column(name = "verification_submitted_at")
    var verificationSubmittedAt: Instant? = null,

    /** Timestamp when an admin approved the KYC dossier. */
    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,

    /**
     * Frozen reference to the [AssociationRegistryCheck] that informed the KYC decision
     * (approve/reject). Null if the dossier is undecided or was decided without a prior scan.
     * The referenced row is immutable, so this is a faithful decision-time snapshot (LCB-FT).
     */
    @Column(name = "decision_registry_check_id")
    var decisionRegistryCheckId: UUID? = null,

    /**
     * Public opaque token identifying this association's donation widget (e.g. `clk_…`).
     * Null means the widget is inactive. Unique across all associations; generated and
     * rotated via the dedicated token endpoint (B7). Never expose internal IDs through this.
     */
    @Column(name = "widget_token", unique = true)
    var widgetToken: String? = null,

    /**
     * The campaign that receives donations submitted through this association's widget.
     * Null if no destination has been configured yet. Set to null automatically (ON DELETE SET NULL)
     * if the targeted campaign is deleted — the widget will then refuse donations until reconfigured.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "widget_destination_campaign_id")
    var widgetDestinationCampaign: Campaign? = null,

    /**
     * Allowed origin for widget post-payment redirects (e.g. `https://www.asso-a.fr`).
     * Null means no dynamic redirect is permitted — open-redirect protection by default.
     */
    @Column(name = "widget_allowed_origin", length = 255)
    var widgetAllowedOrigin: String? = null,

    /** Full street address of the association's registered office (e.g. "42 RUE DE CLICHY 75009 PARIS"). Printed on Cerfa receipts. */
    @Column(name = "address_line1", length = 255)
    var addressLine1: String? = null,

    /** Official purpose / objet social (e.g. "Aide bénévole à l'alimentation…"). Printed on Cerfa receipts. */
    @Column(name = "legal_object", columnDefinition = "TEXT")
    var legalObject: String? = null,

    /** Full name of the person authorised to sign receipts on behalf of the association. */
    @Column(name = "signer_name", length = 255)
    var signerName: String? = null,

    /** Role/title of the authorised signer (e.g. "Trésorier"). */
    @Column(name = "signer_role", length = 100)
    var signerRole: String? = null,

    /** Visual palette of the donation landing page. [LandingTheme.DEFAULT] reproduces the original design. */
    @Enumerated(EnumType.STRING)
    @Column(name = "landing_theme", nullable = false, length = 20)
    var landingTheme: LandingTheme = LandingTheme.DEFAULT,

    /**
     * Public serving path of the landing page logo (`/api/public/associations/{id}/logo`).
     * Null means no logo has been uploaded; the landing header then shows the name alone.
     * The bytes live in [AssociationLogo], never here.
     */
    @Column(name = "landing_logo", length = 255)
    var landingLogo: String? = null,

    /** Show the "what this donation funds" section on the landing page. */
    @Column(name = "landing_show_project", nullable = false)
    var landingShowProject: Boolean = true,

    /** Show the budget / milestones transparency section on the landing page. */
    @Column(name = "landing_show_transparency", nullable = false)
    var landingShowTransparency: Boolean = true,

    /** Show the "donate with confidence" section on the landing page. */
    @Column(name = "landing_show_trust", nullable = false)
    var landingShowTrust: Boolean = true,

    /** AML/CFT risk level assigned to this association. Defaults to STANDARD pending formal classification. */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    var riskLevel: RiskLevel = RiskLevel.STANDARD,

    /** Timestamp when the risk level was last formally assessed. Null until a first assessment is recorded. */
    @Column(name = "risk_level_assessed_at")
    var riskLevelAssessedAt: Instant? = null,

    /**
     * Version identifier of the risk classification document in force at assessment time.
     * Null until the first formal assessment. Without this reference a past assessment cannot
     * be interpreted once the classification document is revised.
     */
    @Column(name = "risk_classification_version", length = 32)
    var riskClassificationVersion: String? = null,

    /** Deterministic 20-byte EVM address derived from the association's UUID via HMAC-SHA256. Set at profile creation. */
    @Column(name = "wallet_address", length = 42)
    var walletAddress: String? = null,

    /**
     * Google Tag Manager container ID (e.g. `GTM-XXXXXXX`) for Google Ad Grants tracking.
     * Drives GTM injection on the landing page, the embedded widget, and the copy/paste export
     * offered in the settings tab — not landing-scoped, hence no `landing_` prefix. Null means
     * GTM is not configured, so nothing is injected anywhere.
     */
    @Column(name = "gtm_container_id", length = 20)
    var gtmContainerId: String? = null,
)

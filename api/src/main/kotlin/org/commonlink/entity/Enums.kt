package org.commonlink.entity


/**
 * Defines the types of users on the platform.
 *
 * The role is embedded in the JWT (`role` claim) and used by Spring Security
 * as a granted authority (`ROLE_DONOR` / `ROLE_ASSOCIATION`) for route-level access control.
 *
 * Two of these roles are **back-office** roles ([CURATOR], [COMPLIANCE_OFFICER]) and must never be
 * reachable from a public sign-up payload — see [SELF_ASSIGNABLE].
 */
enum class UserRole {
    /** A philanthropist who browses campaigns and makes donations. */
    DONOR,
    /** A non-profit organisation that creates and manages fundraising campaigns. */
    ASSOCIATION,
    /** A platform curator who can perform on-chain moderation actions (verify, pause, etc.). */
    CURATOR,
    /**
     * The AML/CFT compliance officer responsible for reviewing alerts and suspicious-activity
     * reports (SARs). Grants exclusive access to the compliance back-office (/api/compliance/)
     * and nothing else — in particular, no curator moderation actions and no association data.
     */
    COMPLIANCE_OFFICER,
    ;

    companion object {
        /**
         * Roles a caller may request for themselves on a public, unauthenticated sign-up route
         * (email registration, magic link, Google sign-up).
         *
         * [CURATOR] and [COMPLIANCE_OFFICER] are deliberately excluded: they are provisioned
         * out-of-band by [org.commonlink.bootstrap.CuratorBootstrap] and
         * [org.commonlink.bootstrap.ComplianceOfficerBootstrap]. Accepting them from a request body
         * granted anyone who could receive an email full access to the compliance back-office
         * (security audit 2026-08-20, C1).
         *
         * Single source of truth for the DTO constraint
         * ([org.commonlink.validation.SelfAssignableRole]) and the service-side guard in
         * [org.commonlink.service.AuthService].
         */
        val SELF_ASSIGNABLE: Set<UserRole> = setOf(DONOR, ASSOCIATION)
    }
}

/**
 * Tracks how the user's account was originally created.
 *
 * Primarily informational, but also drives UI decisions such as showing "set password"
 * prompts for users who registered via [GOOGLE] or [MAGIC_LINK] and have no password hash.
 */
enum class AuthProvider {
    /** Account created with email + password registration. */
    EMAIL,
    /** Account created via Google OAuth sign-up. */
    GOOGLE,
    /** Account created by clicking a one-time magic-link sent to the user's email. */
    MAGIC_LINK,
    /**
     * Account provisioned automatically by the donation widget for a guest donor.
     *
     * These accounts are non-connectable: no password, no Google sub. The [User.guest]
     * flag is always `true` for accounts with this provider.
     */
    GUEST,
}

/**
 * Lifecycle status of an IBAN entry for a beneficiary.
 *
 * Tracks the verification journey from initial registration through VOP (Verification of Payee)
 * check completion. The status drives UI display and whether a transfer can be initiated.
 */
enum class IbanVerificationStatus {
    /** IBAN has been added but no verification has been attempted yet. */
    PENDING,
    /** IBAN format is syntactically valid (checksum passed) but VOP has not been run. */
    FORMAT_VALID,
    /** VOP returned a positive match — account holder name matches the beneficiary exactly. */
    VERIFIED,
    /** VOP returned a close match — name is similar but not exact; manual review recommended. */
    CLOSE_MATCH,
    /** VOP returned no match — account holder name differs significantly from the beneficiary. */
    NO_MATCH,
    /** VOP could not be completed for the given IBAN (e.g. bank does not support VOP). */
    NOT_POSSIBLE,
    /** IBAN failed format validation (invalid checksum or structure). */
    INVALID
}

/**
 * Raw outcome returned by a VOP (Verification of Payee) check against the beneficiary's bank.
 *
 * Maps directly to the four possible responses defined in the SEPA VOP standard.
 * This value is stored alongside the full [BeneficiaryIban.vopRawResponse] for audit purposes.
 */
enum class VopResult {
    /** The provided name matches the account holder name exactly. */
    MATCH,
    /** The provided name is similar but not identical to the account holder name. */
    CLOSE_MATCH,
    /** The provided name does not match the account holder name. */
    NO_MATCH,
    /** The receiving bank does not support VOP for the given account. */
    NOT_POSSIBLE
}

/**
 * Lifecycle status of a fundraising campaign.
 *
 * Drives visibility to donors and whether donations can be accepted.
 */
enum class CampaignStatus {
    /** Campaign is being configured and is not yet visible to donors. */
    DRAFT,
    /** Campaign is published and actively accepting donations. */
    LIVE,
    /** Campaign is temporarily suspended by the association. */
    PAUSED,
    /** Association requested revert to draft; awaiting CURATOR on-chain execution. */
    REVERT_REQUESTED,
    /** Campaign was cancelled before completion. */
    CANCELLED,
    /** Campaign reached its goal and has been completed. */
    COMPLETED,
    /** Campaign collection period is over (legacy terminal state). */
    ENDED
}

/**
 * Which side of the budget prévisionnel a section belongs to.
 *
 * A complete budget has both EXPENSE (charges) and REVENUE (produits) sections.
 */
enum class BudgetSide {
    /** Expense items (French: charges). */
    EXPENSE,
    /** Revenue/income items (French: produits). */
    REVENUE
}

/**
 * Progress status of a campaign milestone.
 *
 * Only one milestone can be CURRENT at a time; it becomes REACHED once the target amount is hit.
 */
enum class MilestoneStatus {
    /** Target not yet reached and not the current active milestone. */
    LOCKED,
    /** The currently active milestone being worked towards. */
    CURRENT,
    /** Target amount has been reached. */
    REACHED
}

/**
 * KYC verification lifecycle for an association.
 *
 * Cycle: UNVERIFIED → PENDING (after document submission) → VERIFIED | REJECTED.
 * After REJECTED the association can replace documents and resubmit (REJECTED → PENDING).
 */
enum class VerificationStatus {
    /** No documents submitted yet. */
    UNVERIFIED,
    /** Documents submitted, awaiting admin review. */
    PENDING,
    /** Admin has approved the dossier. */
    VERIFIED,
    /** Admin has rejected the dossier; reason stored in [AssociationProfile.verificationRejectionReason]. */
    REJECTED,
}

/**
 * Type of a stored [AssociationDocument].
 *
 * The three `VERIF_*` types are required for KYC verification; the two `MANDATE_*` types
 * are required before signing a fiscal mandate. [OPTIONAL] documents are supplementary
 * (activity reports, audited accounts, etc.) and can have multiple entries per association.
 */
enum class AssociationDocumentType {
    /** Association statutes — required for KYC. */
    VERIF_STATUTS,
    /** RNA receipt or Journal Officiel insertion — required for KYC. */
    VERIF_RNA_RECEIPT,
    /** Legal representative's identity document — required for KYC. */
    VERIF_REPRESENTATIVE_ID,
    /** Association statutes — required for fiscal mandate. */
    MANDATE_STATUTS,
    /** Fiscal ruling (rescrit fiscal) or sworn attestation — required for fiscal mandate. */
    MANDATE_RESCRIT,
    /** Supplementary document freely uploaded by the association (financial reports, etc.). */
    OPTIONAL,
}

/**
 * Eligibility category declared by the association when signing the fiscal mandate.
 *
 * Determines the tax-reduction percentage donors can claim (Art. 200 or 200-1 ter CGI).
 */
enum class MandateEligibility {
    /** Organisme d'intérêt général — 66 % reduction (Art. 200 CGI). */
    OIG_66,
    /** Organisme d'aide aux personnes en difficulté (loi Coluche) — 75 % reduction (Art. 200-1 ter CGI). */
    OIG_75_COLUCHE,
    /** Reconnu d'utilité publique — 66 % reduction. */
    PUBLIC_UTILITY_66,
}

/**
 * Operational state of a Mollie Connect connection.
 *
 * BROKEN means the stored refresh token has been rejected by Mollie (rotated, revoked, or
 * expired) and no automated recovery is possible; the frontend must re-trigger the OAuth flow.
 * Stored as a string column so future states can be added without a migration.
 */
enum class MollieConnectionState {
    /** Connection is healthy; access token can be refreshed silently. */
    ACTIVE,
    /** Refresh token was rejected by Mollie; the association must reconnect. */
    BROKEN,
}

/** High-level categorisation of a [org.commonlink.entity.Payout]: personnel vs operational expense. */
enum class PayoutKind { REMUNERATION, EXPENSE }

/** Lifecycle status of a [org.commonlink.entity.Payout]. */
enum class PayoutStatus { PENDING, CONFIRMED, FAILED }

/**
 * A business rule preventing a payout from being issued, surfaced to the association
 * before submission so they understand why the "Émettre le paiement" action is blocked.
 */
enum class PayoutBlockingReason {
    /** The selected [org.commonlink.entity.PayeeIban] has not reached [IbanVerificationStatus.VERIFIED]. */
    IBAN_NOT_VERIFIED,
    /** The requested amount exceeds the campaign's available balance. */
    INSUFFICIENT_BALANCE,
    /** The payout label/justification is shorter than 16 characters once trimmed. */
    DESCRIPTION_TOO_SHORT,
}

/**
 * AML/CFT (LCB-FT) risk level assigned to an association or a donation.
 *
 * Capturing the level without the associated [AssociationProfile.riskClassificationVersion]
 * renders a past assessment uninterpretable once the classification document is revised.
 * The mapping from a risk level to the corresponding due-diligence measures (simplified,
 * standard, or enhanced) is defined in a versioned classification document, not in this code.
 */
enum class RiskLevel {
    /** Low risk — simplified due diligence may apply per the current classification. */
    LOW,
    /** Standard risk — baseline due diligence applies. Default for all new records. */
    STANDARD,
    /** High risk — enhanced due diligence required per the current classification. */
    HIGH,
}

/**
 * Distingue les représentants légaux (REPRESENTATIVE) des bénéficiaires effectifs stricts
 * (BENEFICIAL_OWNER) au sein de la table `beneficial_owner`.
 *
 * Art. R.561-3 CMF (décret n°2024-720 du 5 juillet 2024) : pour une association, tout
 * administrateur, membre de surveillance ou dirigeant est bénéficiaire effectif. Les deux
 * catégories sont soumises au contrôle de gel et à un gate d'approbation distinct.
 */
enum class BeneficialOwnerType { BENEFICIAL_OWNER, REPRESENTATIVE }

/**
 * Compliance status of an [AssociationProfile] (IC-44 — canal de signalement de campagne).
 *
 * Cycle : `ACTIVE → ALERT` (signalement public reçu, [org.commonlink.entity.ComplianceAlertOrigin.CAMPAIGN_REPORT]
 * ouvert) → `SUSPENDED` (signalement confirmé fondé) → `ACTIVE` (réactivation par la compliance).
 * `ALERT` ne bloque rien publiquement — seul `SUSPENDED` gate les dons, voir
 * [org.commonlink.service.PublicWidgetService].
 */
enum class AssociationStatus { ACTIVE, ALERT, SUSPENDED }

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
enum class PayoutKind {
    REMUNERATION,
    EXPENSE,
    ;

    companion object {
        /**
         * Plan comptable codes that make a payout a personnel expense — 64 « Charges de
         * personnel ». Everything else, including a code the association typed itself, is an
         * operational expense.
         */
        private val REMUNERATION_TYPE_CODES = setOf("64-rem", "64-soc")

        /**
         * Derives the kind from the accounting code, so the two can never contradict each other.
         *
         * The request body carries a `kind` too, and it used to be trusted: a replayed call with
         * `typeCode = "64-rem"` and `kind = EXPENSE` filed a salary as an operating cost, and
         * every click on this platform is replayable. The code is the value the association
         * actually chose from a closed list; the kind was only ever a projection of it computed in
         * the browser, so the browser is no longer asked.
         *
         * Mirrors `kindFromTypeCode` in `app/src/components/campaign/CampaignPaymentsTab.tsx` —
         * the frontend keeps its copy to label its own form, not to decide the stored value.
         */
        fun fromTypeCode(typeCode: String): PayoutKind =
            if (typeCode.trim().lowercase() in REMUNERATION_TYPE_CODES) REMUNERATION else EXPENSE
    }
}

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
 * Stable cause of a payout's last failure, translated by the frontend.
 *
 * The first seventeen entries are Bridge's own `status_reason` values, which are bare ISO 20022
 * codes — `AC01`, `NOAS` — and not the prose [Payout.bridgeLastError] also carries. The rest are
 * our own causes, which never reach a bank.
 *
 * Posed at the site where the cause is known and never derived afterwards. Recognising a cause
 * from the text of its message is the anti-pattern already rejected for
 * [org.commonlink.exception.BridgeInitiationNotStartedException]: it breaks on the first
 * rewording, and here that means showing an association the wrong explanation.
 *
 * [Payout.bridgeLastError] keeps the verbatim text for support and is no longer displayed.
 */
enum class PayoutErrorCode {
    /** *(IncorrectAccountNumber)* the account number is either invalid or does not exist. */
    AC01,
    /** *(ClosedAccountNumber)* the account is closed and cannot be used. */
    AC04,
    /** *(BlockedAccount)* the account is blocked and cannot be used. */
    AC06,
    /** *(TransactionForbidden)* Transaction forbidden on this type of account. */
    AG01,
    /** *(InsufficientFunds)* the funds available do not cover the amount. No Demo Bank login. */
    AM04,
    /** *(InvalidNumberOfTransactions)* the number of transactions exceeds the bank's limit. */
    AM18,
    /** *(RequestedExecutionDateTooFarInFuture)* — we send no execution date, so this signals drift. */
    CH03,
    /** *(RequestedByCustomer)* the reject is due to the debtor: refusal or lack of liquidity. */
    CUST,
    /** *(OrderCancelled)* an authorised user cancelled the order at their bank. */
    DS02,
    /** *(InvalidFileFormat)* the original payment request was invalid — our request, our bug. */
    FF01,
    /** *(FraudulentOriginated)* the payment request is considered fraudulent by the bank. */
    FRAD,
    /** *(NotSpecifiedReasonAgentGenerated)* no reason specified by the bank. */
    MS03,
    /** *(NoAnswerFromCustomer)* the association never answered and the bank timed out. */
    NOAS,
    /** *(MissingDebtorAccountOrIdentification)* debtor account or identification inconsistent. */
    RR01,
    /** *(MissingCreditorNameOrAddress)* creditor name or address insufficient for the regulator. */
    RR03,
    /** *(RegulatoryReason)* reject from regulatory reason. */
    RR04,
    /** *(InvalidPartyID)* identification required by the country or payment type is invalid. No Demo Bank login. */
    RR12,

    /** The bank refused and communicated no reason — Bridge omits `status_reason` entirely. */
    UNSPECIFIED,

    /**
     * Bridge returned a `status_reason` this version does not model.
     *
     * Kept rather than collapsed to null: "refused for a reason we cannot name" is not "no failure
     * at all", and the verbatim string is still on [Payout.bridgeLastError] for whoever looks.
     */
    UNKNOWN,

    /** The authorisation window closed before the association authorised. Not a bank refusal. */
    LINK_EXPIRED,
    /** The payment link was revoked before use. Not a bank refusal either. */
    LINK_REVOKED,
    /** Bridge could not be reached, or returned something unusable, while creating the link. */
    INITIATION_FAILED,
    /** Bridge read the link back with a destination it could not vouch for — see the read-back guard. */
    DESTINATION_UNVERIFIED,
    /** The link exists at Bridge but could not be recorded here, so it was revoked immediately. */
    LINK_NOT_RECORDED,
    ;

    companion object {
        /**
         * Maps a Bridge `status_reason` to this enum.
         *
         * @param wireStatusReason the raw value, absent when the bank gave no reason.
         * @return the matching code, [UNSPECIFIED] when Bridge sent none, [UNKNOWN] when it sent
         *   one we do not model — never null, because a rejection always has *a* cause to show.
         */
        fun fromStatusReason(wireStatusReason: String?): PayoutErrorCode {
            val trimmed = wireStatusReason?.trim()?.takeIf { it.isNotEmpty() } ?: return UNSPECIFIED
            return entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) && it.isBridgeStatusReason }
                ?: UNKNOWN
        }
    }

    /** Whether this value is one Bridge can send back, as opposed to one of our own causes. */
    val isBridgeStatusReason: Boolean
        get() = ordinal <= RR12.ordinal

    /**
     * Whether the bank was asked and said no.
     *
     * [NOAS] and [DS02] are deliberately excluded although Bridge reports them under `RJCT`:
     * a timeout waiting for the association, and the association cancelling at its own bank, are
     * nobody ever asking. Stamping them terminal retires a payout for good because someone let a
     * screen expire — the same reasoning that already routes an expired link to a release rather
     * than a failure. See `org.commonlink.service.BridgeWebhookService`.
     */
    val isBankRefusal: Boolean
        get() = isBridgeStatusReason && this != NOAS && this != DS02 ||
            this == UNSPECIFIED || this == UNKNOWN
}

/**
 * State of a Bridge payment initiation for a payout.
 *
 * Deliberately distinct from [PayoutStatus]: this is the bank's view of the transfer, whereas
 * [PayoutStatus] is CommonLink's accounting lifecycle. Only [ACSC] promotes a payout to
 * [PayoutStatus.CONFIRMED], and only [RJCT] demotes it to [PayoutStatus.FAILED] — the bank
 * refused, which is terminal.
 *
 * [LINK_EXPIRED] and [LINK_REVOKED] demote nothing. A link that died unused means nobody ever
 * authorised anything, so the payout goes back to a retryable [PayoutStatus.PENDING] with its
 * amount returned to the campaign. They are therefore transient: reported by
 * [org.commonlink.service.BridgePaymentInitiationService.getPaymentLink] and acted on, never
 * stored on a row — releasing the payout sets its Bridge status back to `null`. Rows written
 * before that rule may still carry them, which is why the column's check constraint keeps them.
 *
 * The first six values are Bridge's ISO 20022 transaction statuses. The last two are payment-link
 * terminal states, kept in the same enum because exactly one of the two levels is meaningful at a
 * time: before the association has authenticated at its bank there is no transaction yet, only a
 * link that can expire or be revoked.
 */
enum class BridgePaymentStatus {
    /** Link created, the association has not authenticated at its bank yet. */
    CREA,
    /** Accepted by Bridge, awaiting bank-side authorisation. */
    ACTC,
    /** Authorised and pending settlement — funds are on their way. */
    PDNG,
    /** Terminal success: the transfer was accepted and settled by the bank. */
    ACSC,
    /**
     * Terminal failure: rejected by the bank. Bridge's `status_reason` carries the cause as a bare
     * ISO 20022 code — `AC01`, `NOAS` — mapped to [PayoutErrorCode] and stored in
     * [Payout.bridgeLastErrorCode], the raw string going to [Payout.bridgeLastError].
     *
     * Not every `RJCT` is a refusal: `NOAS` and `DS02` mean the association never answered or
     * cancelled at its own bank, and those release the payout instead of failing it — see
     * [PayoutErrorCode.isBankRefusal].
     */
    RJCT,
    /** Partial execution — only meaningful for bulk transfers; a payout carries a single transaction. */
    PART,
    /** The link expired before the association authenticated: nothing was asked, nothing owed. */
    LINK_EXPIRED,
    /** The link was revoked before use — same outcome, and how a rejected link is closed. */
    LINK_REVOKED,
    ;

    /** Whether no further state change is expected. */
    val isTerminal: Boolean
        get() = this == ACSC || this == RJCT || this == LINK_EXPIRED || this == LINK_REVOKED

    /** Whether the transfer is engaged: the amount must stay reserved on the campaign. */
    val isInFlight: Boolean
        get() = !isTerminal

    /**
     * Whether the transfer has left the association's hands, so the link dying cannot stop it.
     *
     * [CREA] and [ACTC] both mean the association has not yet authorised at its bank; on a link
     * that has expired or been revoked it never will, because the URL that would let it is dead.
     * From [PDNG] on, the bank is executing and the link's fate is irrelevant.
     *
     * Observed on 2026-09-23: a payer entered the tunnel, created a payment request, abandoned it,
     * and the link expired five minutes later. Bridge sent `payment.link.updated` as hoped, but
     * letting the request's `ACTC` win reported the payout in flight for ever and kept its amount
     * engaged on the campaign — the very outcome the expiry was meant to prevent.
     */
    val survivesLinkDeath: Boolean
        get() = this == PDNG || this == PART || isTerminal

    companion object {
        /**
         * Maps a Bridge transaction status wire value to this enum.
         *
         * @return the matching constant, or null for an unrecognised or absent value — an unknown
         *   state must never be mistaken for a terminal one.
         */
        fun fromTransactionWire(value: String?): BridgePaymentStatus? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) && it != LINK_EXPIRED && it != LINK_REVOKED }
    }
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

/** A versioned legal document text (notice ACPR / art. 1740 A CGI — preuve d'acceptation). */
enum class LegalDocumentType { CGU, CGV }

/** Who accepted a [LegalDocumentType] — see [org.commonlink.entity.LegalAcceptance]. */
enum class LegalAcceptanceSubjectType { DONOR, ASSOCIATION }

/**
 * Why a campaign's DRAFT→LIVE publish attempt was refused by
 * [org.commonlink.service.CampaignService.preparePublish] — one value per guard in that method, in
 * the order they are evaluated. Recorded on the compliance audit journal
 * ([org.commonlink.service.ComplianceAuditLogService.appendCampaignReviewRefused]); the annual
 * ACPR activity report's "projets reçus / retenus" metric (art. R.548-4 II CMF) is derived from
 * these events alongside [org.commonlink.service.ComplianceAuditLogService.appendCampaignReviewRetained].
 *
 * Freeze-list hits, out-of-scope refusals and manual curator rejections are **not** represented
 * here: those are association-level KYB outcomes recorded on their own journal entries
 * ([org.commonlink.service.ComplianceAuditLogService.appendOutOfScopeRefusal],
 * [org.commonlink.service.ComplianceAuditLogService.appendNoRepresentativeRefusal]). At this gate
 * they only ever surface as [KYB_NOT_VERIFIED] — `preparePublish` has no visibility into why
 * `verificationStatus` isn't `VERIFIED`.
 */
enum class CampaignReviewRefusalReason {
    GOAL_MISSING,
    SCHEDULE_MISSING,
    BUDGET_UNBALANCED,
    IMPACT_GOALS_MISSING,
    KYB_NOT_VERIFIED,
    BANK_NOT_CONNECTED,
    BANK_CONNECTION_BROKEN,
    BANK_KYC_INCOMPLETE,
    CGU_NOT_ACCEPTED,
}

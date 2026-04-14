package org.commonlink.entity

/**
 * Defines the two types of users on the platform.
 *
 * The role is embedded in the JWT (`role` claim) and used by Spring Security
 * as a granted authority (`ROLE_DONOR` / `ROLE_ASSOCIATION`) for route-level access control.
 */
enum class UserRole {
    /** A philanthropist who browses campaigns and makes donations. */
    DONOR,
    /** A non-profit organisation that creates and manages fundraising campaigns. */
    ASSOCIATION
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
    MAGIC_LINK
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
    /** Campaign collection period is over. */
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

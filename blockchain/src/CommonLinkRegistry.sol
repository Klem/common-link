// SPDX-License-Identifier: MIT
pragma solidity ^0.8.25;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";

/**
 * @title  CommonLinkRegistry
 * @author Klem clement@ommon-link.org
 * @notice On-chain registry for the CommonLink philanthropy platform.
 *         Records verified associations (off-chain KYC attested on-chain), campaigns,
 *         attributed donations (donor -> campaign -> amount with fiscal proof),
 *         cumulative donor statistics, and milestone attainment.
 *
 *         The contract MOVES NO FUNDS. All monetary values are denominated in
 *         EUR cents. This is a proof and traceability layer, never a financial layer.
 *
 *         Immutability principle: once a donation is recorded on-chain it is
 *         permanent. Any subsequent fiat-side refunds are tracked in the off-chain
 *         backend (PostgreSQL) and never alter this registry.
 *
 * @dev    Reference spec: commonlink-registry-spec-v1.2.md
 *         All calls are signed by the CommonLink hot wallet backend; neither
 *         associations nor donors ever sign transactions directly.
 */
contract CommonLinkRegistry is AccessControl, Pausable {
    // ─────────────────────────────────────────────────────────────────────
    // Version
    // ─────────────────────────────────────────────────────────────────────

    /// @notice Semantic version of the registry, mirrors the spec version.
    string public constant VERSION = "1.2.0-mvp";

    // ─────────────────────────────────────────────────────────────────────
    // Roles
    // ─────────────────────────────────────────────────────────────────────

    /// @notice Role allowed to write donations, campaigns, and milestones.
    ///         Held by the backend hot wallet (OnchainWorker).
    bytes32 public constant RECORDER_ROLE = keccak256("RECORDER_ROLE");

    /// @notice Role allowed to curate associations and campaign status transitions.
    ///         Held by the CommonLink moderation team.
    bytes32 public constant CURATOR_ROLE = keccak256("CURATOR_ROLE");

    // ─────────────────────────────────────────────────────────────────────
    // Types
    // ─────────────────────────────────────────────────────────────────────

    /// @notice Possible campaign states. `Completed` and `Cancelled` are terminal.
    enum CampaignStatus {
        Active,
        Paused,
        Completed,
        Cancelled
    }

    /// @notice On-chain attestation of an association's KYC status.
    /// @dev    Valid combinations:
    ///           verified=false, verifiedAt=0, revokedAt=0 -> never verified
    ///           verified=true,  verifiedAt>0, revokedAt=0 -> verified, never revoked
    ///           verified=false, verifiedAt>0, revokedAt>0 -> verified then revoked
    ///           verified=true,  verifiedAt>0, revokedAt>0 -> verified -> revoked -> restored
    ///         `revokedAt` is preserved across restoration to keep an audit trail.
    struct Association {
        bool verified;
        bytes32 sirenHash;
        uint32 verifiedAt;
        uint32 revokedAt;
    }

    /// @notice A fundraising campaign. The `campaignId` (UUID encoded as bytes32)
    ///         is the mapping key, not a struct field.
    struct Campaign {
        address association;
        uint96 goal; // EUR cents
        uint96 raised; // EUR cents
        uint32 startDate;
        uint32 endDate;
        CampaignStatus status;
        uint8 milestoneCount;
        bytes32 budgetHash; // keccak256 du JSON canonique JCS du budget
    }

    /// @notice A recorded donation. The `donationId` (UUID encoded as bytes32)
    ///         is the mapping key, not a struct field.
    struct Donation {
        address donor;
        uint96 amount; // EUR cents
        bytes32 campaignId;
        uint32 timestamp;
        bytes32 receiptHash; // IPFS hash of the Cerfa receipt PDF
        bytes32 txRef; // Stripe/Monerium reference encoded as bytes32
    }

    /// @notice Cumulative donor statistics. Packed into a single storage slot
    ///         so each update costs exactly one SSTORE.
    struct DonorStats {
        uint128 totalDonated; // EUR cents
        uint32 donationCount;
        uint32 uniqueCampaigns;
        uint32 firstDonationAt;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Storage
    // ─────────────────────────────────────────────────────────────────────

    /// @notice Attestation state keyed by association wallet address.
    mapping(address => Association) public associations;

    /// @notice Reverse index sirenHash -> address. Guarantees SIREN uniqueness on-chain.
    mapping(bytes32 => address) public sirenHashToAssociation;

    /// @notice Campaigns keyed by UUID encoded as bytes32.
    mapping(bytes32 => Campaign) public campaigns;

    /// @notice Donations keyed by UUID encoded as bytes32. Write-once: a donation
    ///         entry can never be modified or deleted.
    mapping(bytes32 => Donation) public donations;

    /// @notice Cumulative statistics per donor address.
    mapping(address => DonorStats) public donorStats;

    /// @notice Total donated by (donor, campaign) pair. Used to detect a donor's
    ///         first contribution to a given campaign (for `uniqueCampaigns`).
    mapping(address => mapping(bytes32 => uint128)) public donorCampaignTotal;

    // ─────────────────────────────────────────────────────────────────────
    // Custom errors
    // ─────────────────────────────────────────────────────────────────────

    // Access & state
    error UnauthorizedRole(bytes32 role, address caller);
    error AssociationNotVerified(address association);

    // Association lifecycle
    error EmptySirenHash();
    error SirenAlreadyRegistered(bytes32 sirenHash, address existingAssociation);
    error AssociationAlreadyVerified(address association);
    error AssociationNotRevoked(address association);
    error AssociationNeverVerified(address association);
    error InvalidAssociation();
    error InvalidAdmin();

    // Campaign creation
    error CampaignAlreadyExists(bytes32 campaignId);
    error InvalidDateRange(uint32 startDate, uint32 endDate);
    error InvalidGoal();
    error EmptyCampaignId();
    error EmptyBudgetHash();

    // Donation recording
    error DonationAlreadyExists(bytes32 donationId);
    error EmptyDonationId();
    error CampaignDoesNotExist(bytes32 campaignId);
    error CampaignNotActive(bytes32 campaignId, CampaignStatus actualStatus);
    error CampaignNotInWindow(uint32 currentTime, uint32 startDate, uint32 endDate);
    error InvalidAmount();
    error EmptyReceiptHash();
    error EmptyTxRef();
    error InvalidDonor();

    // Status transitions
    error InvalidStatusTransition(CampaignStatus from, CampaignStatus to);
    error CampaignAlreadyTerminal(bytes32 campaignId);

    // Milestones
    error InvalidMilestoneIndex(uint8 index, uint8 milestoneCount);
    error EmptyProofHash();

    // ─────────────────────────────────────────────────────────────────────
    // Events
    // ─────────────────────────────────────────────────────────────────────

    /// @notice Emitted when an association is verified for the first time, or
    ///         after a prior revocation when the same sirenHash is re-attested.
    /// @dev    `sirenHash` is indexed so Ponder can reconstruct an association's
    ///         full history by SIREN even if the wallet address changes in a
    ///         future migration.
    event AssociationVerified(address indexed association, bytes32 indexed sirenHash, uint32 verifiedAt, address indexed by);

    /// @notice Emitted when a verified association is revoked. Soft revoke:
    ///         existing campaigns and donations are unaffected, but new
    ///         donations are rejected at runtime.
    event AssociationRevoked(address indexed association, bytes32 indexed sirenHash, uint32 revokedAt, address indexed by
    );

    /// @notice Emitted when a previously revoked association is restored.
    ///         `revokedAt` from the prior revocation is preserved in storage.
    event AssociationRestored(address indexed association, bytes32 indexed sirenHash, uint32 restoredAt, address indexed by);

    /// @notice Emitted when a new campaign is created.
    event CampaignCreated(address indexed association, bytes32 indexed campaignId, uint96 goal, uint32 startDate, uint32 endDate, uint8 milestoneCount, bytes32 budgetHash, address indexed attestedBy);

    /// @notice Emitted on every campaign status transition.
    event CampaignStatusChanged(bytes32 indexed campaignId, CampaignStatus indexed oldStatus, CampaignStatus indexed newStatus, address by);

    /// @notice Emitted when a donation is recorded. The principal event for
    ///         Ponder indexing; `donor`, `campaignId`, and `association` are
    ///         indexed to cover all frontend query patterns.
    event DonationRecorded(address indexed donor, bytes32 indexed campaignId, address indexed association, bytes32 donationId, uint96 amount, bytes32 receiptHash, bytes32 txRef);

    /// @notice Emitted only on a donor's very first donation across the platform.
    event DonorFirstDonation(address indexed donor, uint32 timestamp);

    /// @notice Emitted the first time a donor contributes to a specific campaign.
    event DonorJoinedCampaign(address indexed donor, bytes32 indexed campaignId);

    /// @notice Emitted when a campaign milestone is marked as reached.
    ///         No on-chain storage: idempotency is enforced by the backend.
    event MilestoneReached(bytes32 indexed campaignId, uint8 indexed milestoneIndex, bytes32 proofHash, uint32 timestamp);

    // ─────────────────────────────────────────────────────────────────────
    // Events
    // ─────────────────────────────────────────────────────────────────────

    /// @dev Reverts with CampaignDoesNotExist if the campaign has no association.
    modifier requireCampaignExists(bytes32 campaignId) {
        require(campaigns[campaignId].association != address(0), CampaignDoesNotExist(campaignId));
        _;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @notice Deploys the registry. The admin receives DEFAULT_ADMIN_ROLE and
     *         can subsequently grant RECORDER_ROLE and CURATOR_ROLE.
     * @param  admin    Address receiving DEFAULT_ADMIN_ROLE. Should be a multisig
     *                  in production (see spec §11). Cannot be address(0).
     * @param  recorder Address receiving RECORDER_ROLE. Pass address(0) to skip
     *                  and grant later.
     * @param  curator  Address receiving CURATOR_ROLE. Pass address(0) to skip
     *                  and grant later. In MVP, may be identical to `recorder`.
     */
    constructor(address admin, address recorder, address curator) {
        require(admin != address(0), InvalidAdmin());

        _grantRole(DEFAULT_ADMIN_ROLE, admin);

        if (recorder != address(0)) {
            _grantRole(RECORDER_ROLE, recorder);
        }
        if (curator != address(0)) {
            _grantRole(CURATOR_ROLE, curator);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Association lifecycle (CURATOR_ROLE)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @notice Verifies an association for the first time, or re-applies the same
     *         sirenHash if it was set without verification. To re-activate a
     *         previously revoked association, use `restoreAssociation` instead.
     * @param  association Address of the association's Safe wallet.
     * @param  sirenHash   keccak256 of the canonical SIREN (9 ASCII digits).
     */
    function verifyAssociation(address association, bytes32 sirenHash) external onlyRole(CURATOR_ROLE) {
        require(association != address(0), InvalidAssociation());
        require(sirenHash != bytes32(0), EmptySirenHash());

        Association storage a = associations[association];

        require(!a.verified, AssociationAlreadyVerified(association));

        // Consistency: once a sirenHash is set for an address, it is immutable.
        // This rejects "verify(A, hashX)" followed by "verify(A, hashY)" on a
        // previously-revoked association — for those, callers must use
        // restoreAssociation, which never touches sirenHash.
        require(a.sirenHash == bytes32(0) || a.sirenHash == sirenHash, SirenAlreadyRegistered(a.sirenHash, association));

        // Global uniqueness: this sirenHash must not already be claimed by another
        // address.
        address existing = sirenHashToAssociation[sirenHash];
        require(existing == address(0) || existing == association, SirenAlreadyRegistered(sirenHash, existing));

        a.verified = true;
        a.sirenHash = sirenHash;
        a.verifiedAt = uint32(block.timestamp);
        // a.revokedAt is intentionally not reset; in practice it is 0 here unless
        // a malformed prior state existed, in which case preserving the audit
        // trail is preferable to silently erasing it.

        if (existing == address(0)) {
            sirenHashToAssociation[sirenHash] = association;
        }

        emit AssociationVerified(association, sirenHash, uint32(block.timestamp), msg.sender);
    }

    /**
     * @notice Revokes a verified association. Soft revoke: existing campaigns
     *         and donations are unchanged, but new donations to those campaigns
     *         are rejected at runtime via `recordDonation`.
     * @param  association Address of the association to revoke.
     */
    function revokeAssociation(address association) external onlyRole(CURATOR_ROLE) {
        require(association != address(0), InvalidAssociation());

        Association storage a = associations[association];

        require(a.verifiedAt != 0, AssociationNeverVerified(association));
        require(a.verified, AssociationNotVerified(association));

        a.verified = false;
        a.revokedAt = uint32(block.timestamp);

        emit AssociationRevoked(association, a.sirenHash, uint32(block.timestamp), msg.sender);
    }

    /**
     * @notice Restores a previously revoked association. `verifiedAt` is updated
     *         to the restoration timestamp; `revokedAt` is preserved to retain
     *         a record of the last revocation.
     * @param  association Address of the association to restore.
     */
    function restoreAssociation(address association) external onlyRole(CURATOR_ROLE) {
        require(association != address(0), InvalidAssociation());

        Association storage a = associations[association];

        require(a.verifiedAt != 0, AssociationNeverVerified(association));
        require(!a.verified, AssociationNotRevoked(association));

        a.verified = true;
        a.verifiedAt = uint32(block.timestamp);
        // a.revokedAt is intentionally retained for audit.

        emit AssociationRestored(association, a.sirenHash, uint32(block.timestamp), msg.sender);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Campaigns (RECORDER_ROLE)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @notice Records a new campaign. The association must currently be verified.
     * @param  campaignId     UUID encoded as bytes32 (see spec §2.5).
     * @param  association    Address of the association's Safe.
     * @param  goal           Fundraising goal in EUR cents.
     * @param  startDate      Unix timestamp of campaign start (inclusive).
     * @param  endDate        Unix timestamp of campaign end (inclusive).
     * @param  milestoneCount Total number of planned milestones (may be 0).
     * @param  budgetHash     keccak256 of the canonical (JCS) budget JSON.
     */
    function createCampaign(
        bytes32 campaignId,
        address association,
        uint96 goal,
        uint32 startDate,
        uint32 endDate,
        uint8 milestoneCount,
        bytes32 budgetHash
    ) external whenNotPaused onlyRole(RECORDER_ROLE) {
        require(campaignId != bytes32(0), EmptyCampaignId());
        require(associations[association].verified, AssociationNotVerified(association));
        require(campaigns[campaignId].association == address(0), CampaignAlreadyExists(campaignId));
        require(goal != 0, InvalidGoal());
        require(endDate > startDate, InvalidDateRange(startDate, endDate));

        // block.timestamp manipulation by validators (~15s) is irrelevant here:
        // campaigns last days or months, this is a sanity check, not a precision gate.
        // forge-lint: disable-next-line(block-timestamp)
        require(endDate > block.timestamp, InvalidDateRange(startDate, endDate));

        require(budgetHash != bytes32(0), EmptyBudgetHash());

        campaigns[campaignId] = Campaign({
            association: association,
            goal: goal,
            raised: 0,
            startDate: startDate,
            endDate: endDate,
            status: CampaignStatus.Active,
            milestoneCount: milestoneCount,
            budgetHash: budgetHash
        });

        emit CampaignCreated(association, campaignId, goal, startDate, endDate, milestoneCount, budgetHash, msg.sender);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Donations (RECORDER_ROLE)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @notice Records a donation. All validity checks are strict; once recorded,
     *         a donation is immutable and can never be cancelled or refunded
     *         on-chain (fiat refunds are handled off-chain in the backend).
     * @param  donationId  UUID encoded as bytes32.
     * @param  donor       Donor EOA address (backend-managed).
     * @param  campaignId  UUID of the target campaign.
     * @param  amount      Amount in EUR cents.
     * @param  receiptHash IPFS hash of the Cerfa receipt PDF.
     * @param  txRef       Stripe/Monerium reference encoded as bytes32.
     */
    function recordDonation(bytes32 donationId, address donor, bytes32 campaignId, uint96 amount, bytes32 receiptHash, bytes32 txRef) external whenNotPaused onlyRole(RECORDER_ROLE) requireCampaignExists(campaignId) {
        require(donationId != bytes32(0), EmptyDonationId());
        require(donations[donationId].donor == address(0), DonationAlreadyExists(donationId));
        require(donor != address(0), InvalidDonor());

        Campaign storage c = campaigns[campaignId];


        // Runtime soft-revoke check: if the campaign's association has been
        // revoked, the donation is refused.
        require(associations[c.association].verified, AssociationNotVerified(c.association));

        require(c.status == CampaignStatus.Active, CampaignNotActive(campaignId, c.status));

        // Same reasoning as in createCampaign: campaigns operate at day/month
        // granularity; ~15s validator drift on block.timestamp cannot cause
        // economic or legal harm. We accept it explicitly.
        // forge-lint: disable-next-line(block-timestamp)
        require(block.timestamp >= c.startDate && block.timestamp <= c.endDate, CampaignNotInWindow(uint32(block.timestamp), c.startDate, c.endDate));

        require(amount != 0, InvalidAmount());
        require(receiptHash != bytes32(0), EmptyReceiptHash());
        require(txRef != bytes32(0), EmptyTxRef());

        // 1. Write the donation entry.
        donations[donationId] = Donation({
            donor: donor,
            amount: amount,
            campaignId: campaignId,
            timestamp: uint32(block.timestamp),
            receiptHash: receiptHash,
            txRef: txRef
        });

        // 2. Update the campaign's raised total.
        c.raised += amount;

        // 3. Update donor statistics. The "first donation" booleans must be
        //    evaluated BEFORE any of the counters are bumped.
        DonorStats storage s = donorStats[donor];
        bool isFirstDonationEver = (s.firstDonationAt == 0);
        bool isFirstOnCampaign = (donorCampaignTotal[donor][campaignId] == 0);

        if (isFirstDonationEver) {
            s.firstDonationAt = uint32(block.timestamp);
        }
        s.totalDonated += amount;
        s.donationCount += 1;
        if (isFirstOnCampaign) {
            s.uniqueCampaigns += 1;
        }

        // 4. Update the (donor, campaign) total. Done AFTER the isFirstOnCampaign
        //    check to keep that flag correct.
        donorCampaignTotal[donor][campaignId] += amount;

        // 5. Emit events.
        emit DonationRecorded(donor, campaignId, c.association, donationId, amount, receiptHash, txRef);
        if (isFirstDonationEver) {
            emit DonorFirstDonation(donor, uint32(block.timestamp));
        }
        if (isFirstOnCampaign) {
            emit DonorJoinedCampaign(donor, campaignId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Campaign status transitions (CURATOR_ROLE)
    // ─────────────────────────────────────────────────────────────────────

    /// @notice Pauses an Active campaign. Reverts on any other source state.
    function pauseCampaign(bytes32 campaignId) external onlyRole(CURATOR_ROLE) requireCampaignExists(campaignId) {
        Campaign storage c = campaigns[campaignId];
        require(c.status == CampaignStatus.Active, InvalidStatusTransition(c.status, CampaignStatus.Paused));

        _changeStatus(campaignId, c, CampaignStatus.Paused);
    }

    /// @notice Resumes a Paused campaign. Reverts on any other source state.
    function unpauseCampaign(bytes32 campaignId) external onlyRole(CURATOR_ROLE) requireCampaignExists(campaignId) {
        Campaign storage c = campaigns[campaignId];
        require(c.status == CampaignStatus.Paused, InvalidStatusTransition(c.status, CampaignStatus.Active));

        _changeStatus(campaignId, c, CampaignStatus.Active);
    }

    /// @notice Cancels a campaign (terminal). Allowed from Active or Paused.
    function cancelCampaign(bytes32 campaignId) external onlyRole(CURATOR_ROLE) requireCampaignExists(campaignId)  {
        Campaign storage c = campaigns[campaignId];
        require(c.status != CampaignStatus.Cancelled && c.status != CampaignStatus.Completed, CampaignAlreadyTerminal(campaignId));

        _changeStatus(campaignId, c, CampaignStatus.Cancelled);
    }

    /// @notice Completes a campaign (terminal). Allowed from Active or Paused.
    function completeCampaign(bytes32 campaignId) external onlyRole(CURATOR_ROLE) requireCampaignExists(campaignId) {
        Campaign storage c = campaigns[campaignId];
        require(c.status != CampaignStatus.Cancelled && c.status != CampaignStatus.Completed, CampaignAlreadyTerminal(campaignId));

        _changeStatus(campaignId, c, CampaignStatus.Completed);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Milestones (RECORDER_ROLE)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @notice Emits an event signalling that a campaign milestone has been
     *         reached. No on-chain storage of the attainment: idempotency
     *         (preventing the same milestone from being marked twice) is the
     *         backend's responsibility.
     * @param  campaignId Target campaign.
     * @param  index      Zero-based milestone index, must be < milestoneCount.
     * @param  proofHash  Hash of the proof artifact (e.g. IPFS pointer).
     */
    function markMilestoneReached(bytes32 campaignId, uint8 index, bytes32 proofHash) external whenNotPaused onlyRole(RECORDER_ROLE) requireCampaignExists(campaignId) {
        Campaign storage c = campaigns[campaignId];

        require(c.status == CampaignStatus.Active, CampaignNotActive(campaignId, c.status));
        require(index < c.milestoneCount, InvalidMilestoneIndex(index, c.milestoneCount));
        require(proofHash != bytes32(0), EmptyProofHash());

        emit MilestoneReached(campaignId, index, proofHash, uint32(block.timestamp));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Global emergency pause (DEFAULT_ADMIN_ROLE)
    // ─────────────────────────────────────────────────────────────────────

    /// @notice Freezes recordDonation, createCampaign, and markMilestoneReached.
    ///         Curation functions (status changes, association lifecycle) remain
    ///         available so incident triage can proceed.
    function pause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _pause();
    }

    /// @notice Lifts the global pause.
    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _unpause();
    }

    // ─────────────────────────────────────────────────────────────────────
    // View helpers — return entire structs in a single call
    // ─────────────────────────────────────────────────────────────────────

    function getAssociation(address association) external view returns (Association memory) {
        return associations[association];
    }

    function getCampaign(bytes32 campaignId) external view returns (Campaign memory) {
        return campaigns[campaignId];
    }

    function getDonation(bytes32 donationId) external view returns (Donation memory) {
        return donations[donationId];
    }

    function getDonorStats(address donor) external view returns (DonorStats memory) {
        return donorStats[donor];
    }

    function getDonorCampaignTotal(address donor, bytes32 campaignId) external view returns (uint128) {
        return donorCampaignTotal[donor][campaignId];
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────


    /// @dev Applies a status transition and emits the corresponding event.
    function _changeStatus(bytes32 campaignId, Campaign storage c, CampaignStatus newStatus) internal {
        CampaignStatus oldStatus = c.status;
        c.status = newStatus;
        emit CampaignStatusChanged(campaignId, oldStatus, newStatus, msg.sender);
    }
}
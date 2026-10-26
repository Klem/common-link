// SPDX-License-Identifier: MIT
pragma solidity ^0.8.30;

import {AccessControlDefaultAdminRules} from "@openzeppelin/contracts/access/extensions/AccessControlDefaultAdminRules.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";
import {ICommonLinkRegistryReader} from "./interfaces/ICommonLinkRegistryReader.sol";

/**
 * @title  CommonLinkRegistry
 * @author Klem clement@ommon-link.org
 * @notice On-chain proof registry for the CommonLink philanthropy platform. It records:
 *           - associations and campaigns, each registered ONCE and forever (integrity reference);
 *           - the budget hash of each campaign;
 *           - attributed donations (donor -> campaign -> amount with fiscal proof) and the
 *             resulting cumulative donor statistics;
 *           - settled payouts (disbursements of campaign funds to beneficiaries) with their bank
 *             transfer reference;
 *           - cumulative campaign statistics (donations, unique donors, payouts).
 *
 *         The contract MOVES NO FUNDS AND CANNOT RECEIVE ANY: no receive / fallback / payable function, no token
 *         receiver hook (ERC-721, ERC-1155, ERC-777, ERC-677), no balance read, no recovery function. A balance can
 *         still be forced (SELFDESTRUCT of another contract, fee recipient, pre-deployment transfer, plain ERC-20
 *         transfer): it stays locked forever and affects no record. All monetary values are denominated in EUR cents.
 *
 *         NOTARY PRINCIPLE: the registry only records facts the backend has ALREADY decided and
 *         confirmed. It re-implements NO business rule (campaign status, association eligibility,
 *         goals, balances, fees, badge criteria): the backend is the decision point.
 *
 *         REGISTERED ONCE, REGISTERED FOREVER: there is no lifecycle on-chain. An association is
 *         registered when it becomes able to collect, a campaign when it first goes live. Neither is
 *         ever removed, paused or re-activated here; making an entity read-only is a backend
 *         decision. The registry only guards its own integrity: access control, pause, duplicate
 *         identifiers, referential existence and input sanity. It does NOT check campaign status,
 *         KYC eligibility, fees, payment confirmation, payout eligibility or balances, budget validity,
 *         refunds (none exist on-chain), donation-to-payout allocation or badge criteria.
 *
 *         HUB OF A HUB-AND-SATELLITES DESIGN: this contract is immutable and holds the canonical state.
 *         It never calls another contract. Satellites (e.g. the soulbound badge) read it through
 *         ICommonLinkRegistryReader, advertised via ERC-165 and versioned by READER_VERSION.
 *
 * @dev    Records are signed by the CommonLink backend hot wallet (RECORDER_ROLE); the emergency pause by a
 *         dedicated backend key (PAUSER_ROLE, triggered by a compliance officer); resuming, role management and
 *         admin hand-over by the admin (a Safe multisig in production, two-step transfer with a delay, never
 *         renounceable). Neither associations nor donors ever sign transactions. See blockchain/README.md.
 */
contract CommonLinkRegistry is AccessControlDefaultAdminRules, Pausable, ICommonLinkRegistryReader {
    // ─────────────────────────────────────────────────────────────────────
    // Version & roles
    // ─────────────────────────────────────────────────────────────────────

    /// @notice Semantic version of the registry.
    string public constant VERSION = "3.0.0-mvp";

    /// @inheritdoc ICommonLinkRegistryReader
    uint16 public constant READER_VERSION = 1;

    /// @notice Role allowed to write every record. Held by the backend hot wallet.
    bytes32 public constant RECORDER_ROLE = keccak256("RECORDER_ROLE");

    /// @notice Role allowed to pause every write (not to resume). Held by a dedicated backend key.
    bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");

    // Types (Association, Campaign, Donation, Payout, CampaignStats, DonorStats) are declared in
    // ICommonLinkRegistryReader so that satellites compile without importing the hub.

    // ─────────────────────────────────────────────────────────────────────
    // Storage — internal: the only read surface is the get* functions of ICommonLinkRegistryReader.
    // ─────────────────────────────────────────────────────────────────────

    /// @notice Registered associations keyed by pseudonymous address.
    mapping(address => Association) internal associations;

    /// @notice Reverse index sirenHash -> address. A SIREN is registered at most once.
    mapping(bytes32 => address) internal sirenHashToAssociation;

    /// @notice Registered campaigns keyed by UUID encoded as bytes32.
    mapping(bytes32 => Campaign) internal campaigns;

    /// @notice Donations keyed by UUID encoded as bytes32. Write-once.
    mapping(bytes32 => Donation) internal donations;

    /// @notice Cumulative statistics per donor address.
    mapping(address => DonorStats) internal donorStats;

    /// @notice Total donated by (donor, campaign) pair.
    mapping(address => mapping(bytes32 => uint128)) internal donorCampaignTotal;

    /// @notice Settled payouts keyed by UUID encoded as bytes32. Write-once.
    mapping(bytes32 => Payout) internal payouts;

    /// @notice Cumulative statistics per campaign (UUID encoded as bytes32).
    mapping(bytes32 => CampaignStats) internal campaignStats;

    // ─────────────────────────────────────────────────────────────────────
    // Custom errors
    // ─────────────────────────────────────────────────────────────────────

    error InvalidRecorder();
    error InvalidPauser();
    error AdminRenounceForbidden();

    // Associations
    error InvalidAssociation();
    error EmptySirenHash();
    error AssociationAlreadyRegistered(address association);
    error SirenAlreadyRegistered(bytes32 sirenHash, address existingAssociation);
    error AssociationNotRegistered(address association);

    // Campaigns
    error EmptyCampaignId();
    error CampaignAlreadyRegistered(bytes32 campaignId);
    error CampaignNotRegistered(bytes32 campaignId);
    error EmptyBudgetHash();

    // Donations
    error EmptyDonationId();
    error DonationAlreadyExists(bytes32 donationId);
    error InvalidDonor();
    error InvalidAmount();
    error EmptyReceiptHash();
    error EmptyTxRef();
    error RaisedOverflow(bytes32 campaignId, uint96 raised, uint96 amount);

    // Payouts
    error EmptyPayoutId();
    error PayoutAlreadyRecorded(bytes32 payoutId);
    error PaidOutOverflow(bytes32 campaignId, uint96 totalPaidOut, uint96 amount);

    // ─────────────────────────────────────────────────────────────────────
    // Events
    // ─────────────────────────────────────────────────────────────────────

    /// @notice Emitted once, when an association is registered.
    event AssociationRegistered(address indexed association, bytes32 indexed sirenHash, uint32 registeredAt);

    /// @notice Emitted once, when a campaign is registered.
    event CampaignRegistered(bytes32 indexed campaignId, address indexed association, bytes32 budgetHash, uint32 registeredAt);

    /// @notice Emitted when a new budget hash is declared for a campaign.
    event CampaignBudgetUpdated(bytes32 indexed campaignId, bytes32 oldBudgetHash, bytes32 newBudgetHash, uint32 timestamp);

    /// @notice Emitted when a donation is recorded.
    event DonationRecorded(address indexed donor, bytes32 indexed campaignId, address indexed association, bytes32 donationId, uint96 amount, bytes32 receiptHash, bytes32 txRef);

    /// @notice Emitted only on a donor's very first recorded donation.
    event DonorFirstDonation(address indexed donor, uint32 timestamp);

    /// @notice Emitted the first time a donor contributes to a given campaign.
    event DonorJoinedCampaign(address indexed donor, bytes32 indexed campaignId);

    /// @notice Emitted when a settled payout is recorded. `amount` is in EUR cents; `txRef` is the bank transfer
    ///         reference (payment provider transaction id encoded as bytes32).
    event PayoutRecorded(bytes32 indexed payoutId, bytes32 indexed campaignId, uint96 amount, bytes32 txRef, uint32 timestamp);

    // ─────────────────────────────────────────────────────────────────────
    // Modifiers
    // ─────────────────────────────────────────────────────────────────────

    /// @dev Reverts with CampaignNotRegistered if the campaign was never registered.
    modifier requireCampaignRegistered(bytes32 campaignId) {
        require(campaigns[campaignId].association != address(0), CampaignNotRegistered(campaignId));
        _;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @notice Deploys the registry. Non-payable: deploying with value reverts.
     * @param  adminDelay Delay (seconds) of the two-step DEFAULT_ADMIN_ROLE transfer.
     * @param  admin      Receives DEFAULT_ADMIN_ROLE (a Safe multisig in production). Cannot be zero.
     * @param  recorder   Receives RECORDER_ROLE. Cannot be zero.
     * @param  pauser     Receives PAUSER_ROLE. Cannot be zero.
     * @dev    Distinct admin / recorder / pauser keys are enforced by the deployment script, not here.
     */
    constructor(uint48 adminDelay, address admin, address recorder, address pauser)
        AccessControlDefaultAdminRules(adminDelay, admin)
    {
        require(recorder != address(0), InvalidRecorder());
        require(pauser != address(0), InvalidPauser());
        _grantRole(RECORDER_ROLE, recorder);
        _grantRole(PAUSER_ROLE, pauser);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Integrity reference: associations and campaigns (registered once)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @notice Registers an association, once and forever. The backend calls it when the
     *         association becomes able to collect donations; whether it may still collect later
     *         is a backend decision that never touches this record.
     * @param  association Pseudonymous on-chain address of the association (no private key exists).
     * @param  sirenHash   keccak256 of the canonical SIREN. A SIREN can be registered only once.
     */
    function registerAssociation(address association, bytes32 sirenHash) external whenNotPaused onlyRole(RECORDER_ROLE) {
        require(association != address(0), InvalidAssociation());
        require(sirenHash != bytes32(0), EmptySirenHash());
        require(associations[association].registeredAt == 0, AssociationAlreadyRegistered(association));
        address existing = sirenHashToAssociation[sirenHash];
        require(existing == address(0), SirenAlreadyRegistered(sirenHash, existing));

        // forge-lint: disable-next-line(block-timestamp)
        uint32 registeredAt = uint32(block.timestamp);
        associations[association] = Association({sirenHash: sirenHash, registeredAt: registeredAt});
        sirenHashToAssociation[sirenHash] = association;

        emit AssociationRegistered(association, sirenHash, registeredAt);
    }

    /**
     * @notice Registers a campaign, once and forever, with its budget hash. The backend calls it
     *         when the campaign first goes live; later status changes stay off-chain.
     * @param  campaignId  UUID encoded as bytes32.
     * @param  association Registered association owning the campaign.
     * @param  budgetHash  keccak256 of the canonical (JCS) budget JSON.
     */
    function registerCampaign(bytes32 campaignId, address association, bytes32 budgetHash) external whenNotPaused onlyRole(RECORDER_ROLE) {
        require(campaignId != bytes32(0), EmptyCampaignId());
        require(campaigns[campaignId].association == address(0), CampaignAlreadyRegistered(campaignId));
        require(associations[association].registeredAt != 0, AssociationNotRegistered(association));
        require(budgetHash != bytes32(0), EmptyBudgetHash());

        // forge-lint: disable-next-line(block-timestamp)
        uint32 registeredAt = uint32(block.timestamp);
        campaigns[campaignId] = Campaign({association: association, raised: 0, registeredAt: registeredAt, budgetHash: budgetHash});

        emit CampaignRegistered(campaignId, association, budgetHash, registeredAt);
    }

    /**
     * @notice Declares a new budget hash for a registered campaign. Whether the budget may still
     *         change is a backend rule; the previous hash lives in the event logs.
     * @param  campaignId    Registered campaign.
     * @param  newBudgetHash New keccak256 of the canonical budget JSON. Must be non-zero.
     */
    function updateCampaignBudget(bytes32 campaignId, bytes32 newBudgetHash) external whenNotPaused onlyRole(RECORDER_ROLE) requireCampaignRegistered(campaignId) {
        require(newBudgetHash != bytes32(0), EmptyBudgetHash());

        Campaign storage c = campaigns[campaignId];
        bytes32 oldHash = c.budgetHash;
        c.budgetHash = newBudgetHash;

        // forge-lint: disable-next-line(block-timestamp)
        emit CampaignBudgetUpdated(campaignId, oldHash, newBudgetHash, uint32(block.timestamp));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Donations (RECORDER_ROLE)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @notice Records a donation the backend has confirmed. Once recorded, a donation is immutable
     *         (fiat refunds are handled off-chain). No campaign status or association state is
     *         checked: the backend decides eligibility before it enqueues this call.
     * @param  donationId  UUID encoded as bytes32.
     * @param  donor       Donor pseudonymous address (backend-managed, no private key).
     * @param  campaignId  Registered campaign.
     * @param  amount      Amount in EUR cents.
     * @param  receiptHash Hash of the fiscal receipt.
     * @param  txRef       Payment provider reference encoded as bytes32.
     */
    function recordDonation(bytes32 donationId, address donor, bytes32 campaignId, uint96 amount, bytes32 receiptHash, bytes32 txRef) external whenNotPaused onlyRole(RECORDER_ROLE) requireCampaignRegistered(campaignId) {
        require(donationId != bytes32(0), EmptyDonationId());
        require(donations[donationId].donor == address(0), DonationAlreadyExists(donationId));
        require(donor != address(0), InvalidDonor());
        require(amount != 0, InvalidAmount());
        require(receiptHash != bytes32(0), EmptyReceiptHash());
        require(txRef != bytes32(0), EmptyTxRef());

        Campaign storage c = campaigns[campaignId];

        donations[donationId] = Donation({
            donor: donor,
            amount: amount,
            campaignId: campaignId,
            timestamp: uint32(block.timestamp),
            receiptHash: receiptHash,
            txRef: txRef
        });

        // Explicit overflow guard with a domain-specific error (scoped to limit stack pressure).
        {
            uint96 currentRaised = c.raised;
            uint96 newRaised;
            unchecked {
                newRaised = currentRaised + amount;
            }
            require(newRaised >= currentRaised, RaisedOverflow(campaignId, currentRaised, amount));
            c.raised = newRaised;
        }

        // Donor statistics: the "first" flags are evaluated BEFORE any counter is bumped.
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
        donorCampaignTotal[donor][campaignId] += amount;

        _countDonation(campaignId, isFirstOnCampaign);

        emit DonationRecorded(donor, campaignId, c.association, donationId, amount, receiptHash, txRef);
        if (isFirstDonationEver) {
            emit DonorFirstDonation(donor, uint32(block.timestamp));
        }
        if (isFirstOnCampaign) {
            emit DonorJoinedCampaign(donor, campaignId);
        }
    }

    /**
     * @dev   Campaign statistics of a recorded donation. Separate function: recordDonation is at the stack limit.
     * @param campaignId        Campaign of the donation.
     * @param isFirstOnCampaign Whether it is the donor's first donation to this campaign (a new unique donor).
     */
    function _countDonation(bytes32 campaignId, bool isFirstOnCampaign) internal {
        CampaignStats storage cs = campaignStats[campaignId];
        cs.donationCount += 1;
        if (isFirstOnCampaign) {
            cs.uniqueDonors += 1;
        }
        // forge-lint: disable-next-line(block-timestamp)
        cs.lastDonationAt = uint32(block.timestamp);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Payouts (RECORDER_ROLE)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @notice Records a settled payout (disbursement of campaign funds to a beneficiary). Proof
     *         only: no funds move. Payout eligibility and the available balance are backend rules.
     * @param  payoutId    UUID of the payout encoded as bytes32. Must be non-zero and unused.
     * @param  campaignId  Registered campaign the funds were disbursed from.
     * @param  amountCents Disbursed amount in EUR cents. Must be non-zero.
     * @param  txRef       Bank transfer reference (payment provider transaction id) encoded as bytes32. Must be
     *                     non-zero.
     */
    function recordPayout(bytes32 payoutId, bytes32 campaignId, uint96 amountCents, bytes32 txRef) external whenNotPaused onlyRole(RECORDER_ROLE) requireCampaignRegistered(campaignId) {
        require(payoutId != bytes32(0), EmptyPayoutId());
        require(payouts[payoutId].campaignId == bytes32(0), PayoutAlreadyRecorded(payoutId));
        require(amountCents != 0, InvalidAmount());
        require(txRef != bytes32(0), EmptyTxRef());

        // forge-lint: disable-next-line(block-timestamp)
        uint32 timestamp = uint32(block.timestamp);
        payouts[payoutId] = Payout({campaignId: campaignId, amount: amountCents, timestamp: timestamp, txRef: txRef});

        // Campaign statistics. No "paid out <= raised" guard: a business rule (notary principle), and `raised` only
        // counts the donations recorded on this chain.
        CampaignStats storage cs = campaignStats[campaignId];
        {
            uint96 currentPaidOut = cs.totalPaidOut;
            uint96 newPaidOut;
            unchecked {
                newPaidOut = currentPaidOut + amountCents;
            }
            require(newPaidOut >= currentPaidOut, PaidOutOverflow(campaignId, currentPaidOut, amountCents));
            cs.totalPaidOut = newPaidOut;
        }
        cs.payoutCount += 1;
        cs.lastPayoutAt = timestamp;

        emit PayoutRecorded(payoutId, campaignId, amountCents, txRef, timestamp);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Emergency pause (PAUSER_ROLE) and resume (DEFAULT_ADMIN_ROLE)
    // ─────────────────────────────────────────────────────────────────────

    /// @notice Freezes every write: registrations, budget updates, donations, payouts. Reads stay available.
    function pause() external onlyRole(PAUSER_ROLE) {
        _pause();
    }

    /// @notice Lifts the pause. Reserved to the admin: the pauser can freeze, never resume.
    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _unpause();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Governance
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @notice Renouncing a role. DEFAULT_ADMIN_ROLE can never be renounced: without an admin, no operational key
     *         (recorder, pauser) could be rotated after a compromise and the pause could never be lifted. The admin
     *         changes hands only through the two-step transfer (beginDefaultAdminTransfer / acceptDefaultAdminTransfer).
     * @param  role    Role to renounce.
     * @param  account Must be the caller (checked by OpenZeppelin).
     */
    function renounceRole(bytes32 role, address account) public override {
        require(role != DEFAULT_ADMIN_ROLE, AdminRenounceForbidden());
        super.renounceRole(role, account);
    }

    /**
     * @notice ERC-165: advertises ICommonLinkRegistryReader in addition to the OpenZeppelin access-control interfaces.
     * @param  interfaceId Interface identifier to test.
     * @return Whether this contract implements `interfaceId`.
     */
    function supportsInterface(bytes4 interfaceId) public view override returns (bool) {
        return interfaceId == type(ICommonLinkRegistryReader).interfaceId || super.supportsInterface(interfaceId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Views (ICommonLinkRegistryReader) — return entire structs in a single call
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

    function getPayout(bytes32 payoutId) external view returns (Payout memory) {
        return payouts[payoutId];
    }

    function getCampaignStats(bytes32 campaignId) external view returns (CampaignStats memory) {
        return campaignStats[campaignId];
    }
}

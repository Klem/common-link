// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {CommonLinkRegistry} from "src/CommonLinkRegistry.sol";

/**
 * @title  CommonLinkBaseTest
 * @notice Shared fixture for all CommonLinkRegistry tests.
 *         Centralises actor setup, role grants, and the most common state
 *         transitions (verify asso, create campaign, record donation) so test
 *         files only need to focus on the behaviour they exercise.
 *
 *         All tests inherit this base. Each `test_*` function operates on a
 *         fresh state thanks to forge-std/Test's per-function isolation.
 */
contract CommonLinkBaseTest is Test {
    CommonLinkRegistry internal registry;

    // ─────────────────────────────────────────────────────────────────────
    // Actors — labelled for readable traces
    // ─────────────────────────────────────────────────────────────────────

    address internal admin    = makeAddr("admin");
    address internal recorder = makeAddr("recorder");
    address internal curator  = makeAddr("curator");
    address internal stranger = makeAddr("stranger");

    address internal asso1 = makeAddr("asso1");
    address internal asso2 = makeAddr("asso2");
    address internal asso3 = makeAddr("asso3");

    address internal donor1 = makeAddr("donor1");
    address internal donor2 = makeAddr("donor2");
    address internal donor3 = makeAddr("donor3");

    // ─────────────────────────────────────────────────────────────────────
    // Test data
    // ─────────────────────────────────────────────────────────────────────

    bytes32 internal constant SIREN_HASH_1 = keccak256("123456789");
    bytes32 internal constant SIREN_HASH_2 = keccak256("987654321");
    bytes32 internal constant SIREN_HASH_3 = keccak256("555555555");

    bytes32 internal constant CAMPAIGN_ID_1 = bytes32(uint256(0xC1));
    bytes32 internal constant CAMPAIGN_ID_2 = bytes32(uint256(0xC2));
    bytes32 internal constant CAMPAIGN_ID_3 = bytes32(uint256(0xC3));

    bytes32 internal constant DONATION_ID_1 = bytes32(uint256(0xD1));
    bytes32 internal constant DONATION_ID_2 = bytes32(uint256(0xD2));
    bytes32 internal constant DONATION_ID_3 = bytes32(uint256(0xD3));

    bytes32 internal constant BUDGET_HASH    = keccak256("budget-v1");
    bytes32 internal constant RECEIPT_HASH_1 = keccak256("cerfa-1");
    bytes32 internal constant RECEIPT_HASH_2 = keccak256("cerfa-2");
    bytes32 internal constant TX_REF_1       = bytes32("pi_test_111");
    bytes32 internal constant TX_REF_2       = bytes32("pi_test_222");
    bytes32 internal constant PROOF_HASH     = keccak256("milestone-proof-1");

    // Pre-computed role hashes. We compute them here rather than calling
    // `registry.XXX_ROLE()` inside `expectRevert` because that external call
    // would consume any active `vm.prank` and skew the AC revert payload.
    bytes32 internal constant RECORDER_ROLE      = keccak256("RECORDER_ROLE");
    bytes32 internal constant CURATOR_ROLE       = keccak256("CURATOR_ROLE");
    bytes32 internal constant DEFAULT_ADMIN_ROLE = bytes32(0);

    // Fix a deterministic starting timestamp so date-window assertions are
    // independent of the host environment.
    uint32 internal constant T0 = 1_800_000_000; // 2027-01-15 ish

    // ─────────────────────────────────────────────────────────────────────
    // setUp
    // ─────────────────────────────────────────────────────────────────────

    function setUp() public virtual {
        // Anchor block.timestamp so end-date checks are stable.
        vm.warp(T0);

        registry = new CommonLinkRegistry(admin, recorder, curator);

        vm.label(address(registry), "Registry");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers — verified associations
    // ─────────────────────────────────────────────────────────────────────

    /// @dev Verifies `who` with `sirenHash` as curator. Reverts inside the
    ///      registry on any pre-condition failure (helpful: failing helpers
    ///      surface the contract's revert directly).
    function _verify(address who, bytes32 sirenHash) internal {
        vm.prank(curator);
        registry.verifyAssociation(who, sirenHash);
    }

    function _revoke(address who) internal {
        vm.prank(curator);
        registry.revokeAssociation(who);
    }

    function _restore(address who) internal {
        vm.prank(curator);
        registry.restoreAssociation(who);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers — campaigns
    // ─────────────────────────────────────────────────────────────────────

    /// @dev Creates a default campaign owned by `assoAddr`. Window: [T0+1, T0+30d].
    function _createDefaultCampaign(bytes32 campaignId, address assoAddr) internal {
        _createCampaign(
            campaignId,
            assoAddr,
            100_000_00,                  // 100 000 € in cents
            T0 + 1,
            T0 + 30 days,
            3,                           // milestoneCount
            BUDGET_HASH
        );
    }

    function _createCampaign(
        bytes32 campaignId,
        address assoAddr,
        uint96 goal,
        uint32 startDate,
        uint32 endDate,
        uint8 milestoneCount,
        bytes32 budgetHash
    ) internal {
        vm.prank(recorder);
        registry.createCampaign(
            campaignId,
            assoAddr,
            goal,
            startDate,
            endDate,
            milestoneCount,
            budgetHash
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers — donations
    // ─────────────────────────────────────────────────────────────────────

    /// @dev Records a donation with sensible defaults. Caller should ensure
    ///      the campaign is in its active window via `vm.warp`.
    function _recordDonation(
        bytes32 donationId,
        address donor,
        bytes32 campaignId,
        uint96 amount
    ) internal {
        vm.prank(recorder);
        registry.recordDonation(
            donationId,
            donor,
            campaignId,
            amount,
            RECEIPT_HASH_1,
            TX_REF_1
        );
    }

    function _recordDonation(
        bytes32 donationId,
        address donor,
        bytes32 campaignId,
        uint96 amount,
        bytes32 receiptHash,
        bytes32 txRef
    ) internal {
        vm.prank(recorder);
        registry.recordDonation(
            donationId,
            donor,
            campaignId,
            amount,
            receiptHash,
            txRef
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers — status transitions
    // ─────────────────────────────────────────────────────────────────────

    function _pauseCampaign(bytes32 campaignId) internal {
        vm.prank(curator);
        registry.pauseCampaign(campaignId);
    }

    function _unpauseCampaign(bytes32 campaignId) internal {
        vm.prank(curator);
        registry.unpauseCampaign(campaignId);
    }

    function _cancelCampaign(bytes32 campaignId) internal {
        vm.prank(curator);
        registry.cancelCampaign(campaignId);
    }

    function _completeCampaign(bytes32 campaignId) internal {
        vm.prank(curator);
        registry.completeCampaign(campaignId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers — convenience composite setup
    // ─────────────────────────────────────────────────────────────────────

    /// @dev "Ready to donate": asso verified, campaign created and within its
    ///      active window. Used by ~all donation tests.
    function _setupReadyToDonate() internal {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);
        vm.warp(T0 + 1 days); // inside the [T0+1, T0+30d] window
    }
}

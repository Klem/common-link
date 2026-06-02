// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {CommonLinkRegistry} from "../../src/CommonLinkRegistry.sol";

/**
 * @title  CommonLinkBaseTest
 * @notice Shared fixtures for the CommonLinkRegistry test suite.
 *
 *         Provides:
 *           - A freshly deployed registry with `admin`, `recorder`, `curator` roles wired.
 *           - Two test associations + two test donors, pre-verified-or-not on demand.
 *           - Helpers for creating campaigns in any starting state (Draft / Active /
 *             Paused / Completed / Cancelled) so individual tests stay focused on the
 *             behaviour under test rather than re-implementing setup.
 *           - Identifier helpers mimicking the Postgres-UUID-as-bytes32 convention
 *             (16 first bytes = value, 16 trailing bytes = zero), with a separate
 *             namespace for donations (top bit set) so collisions between campaign
 *             and donation IDs are structurally impossible across the test suite.
 */
abstract contract CommonLinkBaseTest is Test {
    // ─────────────────────────────────────────────────────────────────────
    // Test actors
    // ─────────────────────────────────────────────────────────────────────

    CommonLinkRegistry public registry;

    address public admin    = makeAddr("admin");
    address public recorder = makeAddr("recorder");
    address public curator  = makeAddr("curator");
    address public asso1    = makeAddr("asso1");
    address public asso2    = makeAddr("asso2");
    address public donor1   = makeAddr("donor1");
    address public donor2   = makeAddr("donor2");
    address public attacker = makeAddr("attacker");

    // ─────────────────────────────────────────────────────────────────────
    // Canonical fixture values
    // ─────────────────────────────────────────────────────────────────────

    bytes32 internal constant SIREN_HASH_1 = keccak256("775672594");
    bytes32 internal constant SIREN_HASH_2 = keccak256("123456789");

    bytes32 internal constant BUDGET_V1 = keccak256("budget-v1");
    bytes32 internal constant BUDGET_V2 = keccak256("budget-v2");
    bytes32 internal constant BUDGET_V3 = keccak256("budget-v3");

    bytes32 internal constant RECEIPT_HASH_1 = keccak256("receipt-pdf-1");
    bytes32 internal constant TX_REF_1 = bytes32("pi_3OqWxYZ12345678901234567");

    uint96 internal constant DEFAULT_GOAL = 1_000_000; // 10 000.00 EUR in cents

    // ─────────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────────

    function setUp() public virtual {
        // Anchor block.timestamp at a stable, non-zero value so tests
        // exercising `block.timestamp` are not at risk of `endDate == 0`
        // colliding with the "not yet closed" sentinel.
        vm.warp(1_800_000_000); // 2027-01-15

        registry = new CommonLinkRegistry(admin, recorder, curator);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Identifier helpers (UUID-shaped bytes32)
    // ─────────────────────────────────────────────────────────────────────

    /// @dev Mimics the IdEncoder convention: 16-byte payload, 16-byte zero tail.
    function _campaignId(uint128 seed) internal pure returns (bytes32) {
        return bytes32(uint256(seed) << 128);
    }

    /// @dev Donation namespace: top bit set so collisions with `_campaignId(seed)`
    ///      are structurally impossible in tests.
    function _donationId(uint128 seed) internal pure returns (bytes32) {
        return bytes32((uint256(seed) | (uint256(1) << 127)) << 128);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Association helpers
    // ─────────────────────────────────────────────────────────────────────

    function _verifyAsso(address asso, bytes32 sirenHash) internal {
        vm.prank(curator);
        registry.verifyAssociation(asso, sirenHash);
    }

    function _verifyAsso1() internal {
        _verifyAsso(asso1, SIREN_HASH_1);
    }

    function _revokeAsso(address asso) internal {
        vm.prank(curator);
        registry.revokeAssociation(asso);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Campaign helpers — one per starting state
    // ─────────────────────────────────────────────────────────────────────

    /// @dev Creates a Draft campaign for `asso` with default goal.
    function _createDraft(bytes32 id, address asso) internal {
        vm.prank(recorder);
        registry.createCampaign(id, asso, DEFAULT_GOAL, 0, BUDGET_V1);
    }

    /// @dev Creates a Draft then publishes it. Returns in Active state.
    function _createActive(bytes32 id, address asso) internal {
        _createDraft(id, asso);
        vm.prank(recorder);
        registry.publishCampaign(id);
    }

    function _createPaused(bytes32 id, address asso) internal {
        _createActive(id, asso);
        vm.prank(curator);
        registry.pauseCampaign(id);
    }

    function _createCompleted(bytes32 id, address asso) internal {
        _createActive(id, asso);
        vm.prank(curator);
        registry.completeCampaign(id);
    }

    function _createCancelled(bytes32 id, address asso) internal {
        _createActive(id, asso);
        vm.prank(curator);
        registry.cancelCampaign(id);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Donation helper
    // ─────────────────────────────────────────────────────────────────────

    function _donate(bytes32 donationId, address donor, bytes32 campaignId, uint96 amount) internal {
        vm.prank(recorder);
        registry.recordDonation(donationId, donor, campaignId, amount, RECEIPT_HASH_1, TX_REF_1);
    }
}

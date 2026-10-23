// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {CommonLinkRegistry} from "../../src/CommonLinkRegistry.sol";

/**
 * @title  CommonLinkBaseTest
 * @notice Shared fixtures for the CommonLinkRegistry v2 test suite: a freshly deployed registry
 *         (admin + recorder), test actors, identifier helpers mimicking the Postgres UUID-as-bytes32
 *         convention (16 value bytes, 16 zero bytes; donations and payouts in separate namespaces),
 *         and registration / donation helpers.
 */
abstract contract CommonLinkBaseTest is Test {
    CommonLinkRegistry public registry;

    address public admin    = makeAddr("admin");
    address public recorder = makeAddr("recorder");
    address public asso1    = makeAddr("asso1");
    address public asso2    = makeAddr("asso2");
    address public donor1   = makeAddr("donor1");
    address public donor2   = makeAddr("donor2");
    address public attacker = makeAddr("attacker");

    bytes32 internal constant SIREN_HASH_1 = keccak256("775672594");
    bytes32 internal constant SIREN_HASH_2 = keccak256("123456789");
    bytes32 internal constant BUDGET_V1 = keccak256("budget-v1");
    bytes32 internal constant BUDGET_V2 = keccak256("budget-v2");
    bytes32 internal constant RECEIPT_HASH_1 = keccak256("receipt-pdf-1");
    // forge-lint: disable-next-line(unsafe-typecast)
    bytes32 internal constant TX_REF_1 = bytes32("tr_WDqYK6vllg");
    /// @dev A Bridge transaction id: 32 hex characters, exactly one bytes32 of text.
    // forge-lint: disable-next-line(unsafe-typecast)
    bytes32 internal constant PAYOUT_TX_REF_1 = bytes32("01a1680fecd57eb7a3aea61d4f074b6d");

    /// @dev Role ids as constants: calling registry.X_ROLE() after vm.prank would consume the prank.
    bytes32 internal constant RECORDER_ROLE = keccak256("RECORDER_ROLE");
    bytes32 internal constant ADMIN_ROLE = 0x00;

    bytes32 internal constant CAMP_1 = bytes32(uint256(1) << 128);
    bytes32 internal constant CAMP_2 = bytes32(uint256(2) << 128);

    function setUp() public virtual {
        // Stable, non-zero timestamp so `registeredAt == 0` never collides with "unregistered".
        vm.warp(1_800_000_000);
        registry = new CommonLinkRegistry(admin, recorder);
    }

    // ── Identifiers ─────────────────────────────────────────────────────

    function _campaignId(uint128 seed) internal pure returns (bytes32) {
        return bytes32(uint256(seed) << 128);
    }

    /// @dev Donation namespace: bit 127 set.
    function _donationId(uint128 seed) internal pure returns (bytes32) {
        return bytes32((uint256(seed) | (uint256(1) << 127)) << 128);
    }

    /// @dev Payout namespace: bit 126 set.
    function _payoutId(uint128 seed) internal pure returns (bytes32) {
        return bytes32((uint256(seed) | (uint256(1) << 126)) << 128);
    }

    // ── Registration helpers ────────────────────────────────────────────

    function _registerAsso(address asso, bytes32 sirenHash) internal {
        vm.prank(recorder);
        registry.registerAssociation(asso, sirenHash);
    }

    function _registerAsso1() internal {
        _registerAsso(asso1, SIREN_HASH_1);
    }

    function _registerCampaign(bytes32 id, address asso) internal {
        vm.prank(recorder);
        registry.registerCampaign(id, asso, BUDGET_V1);
    }

    /// @dev Registers asso1 and CAMP_1 for asso1.
    function _registerAsso1AndCamp1() internal {
        _registerAsso1();
        _registerCampaign(CAMP_1, asso1);
    }

    function _donate(bytes32 donationId, address donor, bytes32 campaignId, uint96 amount) internal {
        vm.prank(recorder);
        registry.recordDonation(donationId, donor, campaignId, amount, RECEIPT_HASH_1, TX_REF_1);
    }
}

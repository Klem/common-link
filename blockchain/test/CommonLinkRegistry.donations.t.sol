// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {CommonLinkBaseTest} from "./helpers/CommonLinkBaseTest.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";

/**
 * @title  RecordDonationTest
 * @notice Donation eligibility tests. Eligibility is governed solely by
 *         `status == Active` — there is no pre-declared window of validity.
 *         A campaign remains donatable until a curator explicitly closes it
 *         (Completed or Cancelled), or pauses / reverts-to-draft it.
 */
contract RecordDonationTest is CommonLinkBaseTest {
    bytes32 internal constant CAMP_1 = bytes32(uint256(1) << 128);
    bytes32 internal constant DON_1 = bytes32(((uint256(1) | (uint256(1) << 127)) << 128));
    bytes32 internal constant DON_2 = bytes32(((uint256(2) | (uint256(1) << 127)) << 128));

    function setUp() public override {
        super.setUp();
        _verifyAsso1();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Status-only eligibility
    // ─────────────────────────────────────────────────────────────────────

    function test_donation_acceptedYearsAfterCreation() public {
        _createActive(CAMP_1, asso1);

        // Donation accepted 5 years later — no pre-declared endDate to fail against.
        vm.warp(block.timestamp + 365 days * 5);

        _donate(DON_1, donor1, CAMP_1, 1000);

        assertEq(registry.getCampaign(CAMP_1).raised, 1000);
    }

    function test_donation_acceptedImmediatelyAfterPublish() public {
        _createActive(CAMP_1, asso1);

        // No `block.timestamp >= startDate` constraint can fail here, since
        // startDate = block.timestamp of createCampaign by construction.
        _donate(DON_1, donor1, CAMP_1, 500);

        assertEq(registry.getCampaign(CAMP_1).raised, 500);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Draft rejection
    // ─────────────────────────────────────────────────────────────────────

    function test_donation_revertsOnDraft() public {
        _createDraft(CAMP_1, asso1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotActive.selector, CAMP_1, CommonLinkRegistry.CampaignStatus.Draft
            )
        );
        registry.recordDonation(DON_1, donor1, CAMP_1, 1000, RECEIPT_HASH_1, TX_REF_1);
    }

    function test_donation_revertsAfterRevertToDraft() public {
        _createActive(CAMP_1, asso1);
        vm.prank(curator);
        registry.revertCampaignToDraft(CAMP_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotActive.selector, CAMP_1, CommonLinkRegistry.CampaignStatus.Draft
            )
        );
        registry.recordDonation(DON_1, donor1, CAMP_1, 1000, RECEIPT_HASH_1, TX_REF_1);
    }

    function test_donation_acceptedAgainAfterRepublish() public {
        _createActive(CAMP_1, asso1);
        vm.prank(curator);
        registry.revertCampaignToDraft(CAMP_1);
        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);

        _donate(DON_1, donor1, CAMP_1, 750);
        assertEq(registry.getCampaign(CAMP_1).raised, 750);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Other non-Active states still rejected
    // ─────────────────────────────────────────────────────────────────────

    function test_donation_revertsOnPaused() public {
        _createPaused(CAMP_1, asso1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotActive.selector, CAMP_1, CommonLinkRegistry.CampaignStatus.Paused
            )
        );
        registry.recordDonation(DON_1, donor1, CAMP_1, 1000, RECEIPT_HASH_1, TX_REF_1);
    }

    function test_donation_revertsOnCompleted() public {
        _createCompleted(CAMP_1, asso1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotActive.selector, CAMP_1, CommonLinkRegistry.CampaignStatus.Completed
            )
        );
        registry.recordDonation(DON_1, donor1, CAMP_1, 1000, RECEIPT_HASH_1, TX_REF_1);
    }

    function test_donation_revertsOnCancelled() public {
        _createCancelled(CAMP_1, asso1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotActive.selector, CAMP_1, CommonLinkRegistry.CampaignStatus.Cancelled
            )
        );
        registry.recordDonation(DON_1, donor1, CAMP_1, 1000, RECEIPT_HASH_1, TX_REF_1);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Resilience to repeated pause/unpause cycles
    // ─────────────────────────────────────────────────────────────────────

    function test_donation_alternatesOnPauseUnpause() public {
        _createActive(CAMP_1, asso1);

        _donate(DON_1, donor1, CAMP_1, 100);

        vm.prank(curator);
        registry.pauseCampaign(CAMP_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotActive.selector, CAMP_1, CommonLinkRegistry.CampaignStatus.Paused
            )
        );
        registry.recordDonation(DON_2, donor1, CAMP_1, 200, RECEIPT_HASH_1, TX_REF_1);

        vm.prank(curator);
        registry.unpauseCampaign(CAMP_1);

        _donate(DON_2, donor1, CAMP_1, 200);
        assertEq(registry.getCampaign(CAMP_1).raised, 300);
    }

    // ─────────────────────────────────────────────────────────────────────
    // C1 — explicit overflow guard on c.raised
    // ─────────────────────────────────────────────────────────────────────

    /// @dev Saturates raised to type(uint96).max - 1 with one massive donation
    ///      and then tries to push 2 cents on top. The unchecked addition would
    ///      wrap to 0 without the explicit guard; the guard surfaces a
    ///      domain-specific `RaisedOverflow` error instead.
    function test_donation_revertsOnRaisedOverflow() public {
        bytes32 campId = bytes32(uint256(7) << 128);

        // Goal is uint96 so it can hold the saturating donation.
        vm.prank(recorder);
        registry.createCampaign(campId, asso1, type(uint96).max, 0, RECEIPT_HASH_1);
        vm.prank(recorder);
        registry.publishCampaign(campId);

        uint96 nearMax = type(uint96).max - 1;
        vm.prank(recorder);
        registry.recordDonation(DON_1, donor1, campId, nearMax, RECEIPT_HASH_1, TX_REF_1);
        assertEq(registry.getCampaign(campId).raised, nearMax);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.RaisedOverflow.selector, campId, nearMax, uint96(2)
            )
        );
        registry.recordDonation(DON_2, donor1, campId, 2, RECEIPT_HASH_1, TX_REF_1);

        // State must be intact after the revert.
        assertEq(registry.getCampaign(campId).raised, nearMax);
    }
}

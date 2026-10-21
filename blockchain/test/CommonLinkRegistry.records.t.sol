// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {CommonLinkBaseTest} from "./helpers/CommonLinkBaseTest.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";

/// @notice Donation recording: integrity guards only, no business rule.
contract RecordDonationTest is CommonLinkBaseTest {
    event DonationRecorded(address indexed donor, bytes32 indexed campaignId, address indexed association, bytes32 donationId, uint96 amount, bytes32 receiptHash, bytes32 txRef);
    event DonorFirstDonation(address indexed donor, uint32 timestamp);
    event DonorJoinedCampaign(address indexed donor, bytes32 indexed campaignId);

    function setUp() public override {
        super.setUp();
        _registerAsso1AndCamp1();
    }

    function test_donation_recordsStatsAndEmits() public {
        vm.expectEmit(true, true, true, true, address(registry));
        emit DonationRecorded(donor1, CAMP_1, asso1, _donationId(1), 1000, RECEIPT_HASH_1, TX_REF_1);
        vm.expectEmit(true, false, false, true, address(registry));
        emit DonorFirstDonation(donor1, uint32(block.timestamp));
        vm.expectEmit(true, true, false, false, address(registry));
        emit DonorJoinedCampaign(donor1, CAMP_1);
        _donate(_donationId(1), donor1, CAMP_1, 1000);

        CommonLinkRegistry.Donation memory d = registry.getDonation(_donationId(1));
        assertEq(d.donor, donor1);
        assertEq(d.amount, 1000);
        assertEq(d.campaignId, CAMP_1);
        assertEq(registry.getCampaign(CAMP_1).raised, 1000);

        CommonLinkRegistry.DonorStats memory s = registry.getDonorStats(donor1);
        assertEq(s.totalDonated, 1000);
        assertEq(s.donationCount, 1);
        assertEq(s.uniqueCampaigns, 1);
        assertEq(s.firstDonationAt, uint32(block.timestamp));
        assertEq(registry.getDonorCampaignTotal(donor1, CAMP_1), 1000);
    }

    function test_donation_aggregatesAcrossCampaigns() public {
        _registerCampaign(CAMP_2, asso1);
        uint32 first = uint32(block.timestamp);
        _donate(_donationId(1), donor1, CAMP_1, 100);
        vm.warp(block.timestamp + 1 days);
        _donate(_donationId(2), donor1, CAMP_1, 200);
        _donate(_donationId(3), donor1, CAMP_2, 300);

        CommonLinkRegistry.DonorStats memory s = registry.getDonorStats(donor1);
        assertEq(s.totalDonated, 600);
        assertEq(s.donationCount, 3);
        assertEq(s.uniqueCampaigns, 2);
        assertEq(s.firstDonationAt, first);
        assertEq(registry.getDonorCampaignTotal(donor1, CAMP_1), 300);
        assertEq(registry.getCampaign(CAMP_2).raised, 300);
    }

    function test_donation_revertsOnUnregisteredCampaign() public {
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.CampaignNotRegistered.selector, CAMP_2));
        registry.recordDonation(_donationId(1), donor1, CAMP_2, 1000, RECEIPT_HASH_1, TX_REF_1);
    }

    function test_donation_revertsOnDuplicateId() public {
        _donate(_donationId(1), donor1, CAMP_1, 1000);
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.DonationAlreadyExists.selector, _donationId(1)));
        registry.recordDonation(_donationId(1), donor2, CAMP_1, 5, RECEIPT_HASH_1, TX_REF_1);

        assertEq(registry.getCampaign(CAMP_1).raised, 1000, "no double count");
        assertEq(registry.getDonorStats(donor1).donationCount, 1);
    }

    function test_donation_sanityGuards() public {
        vm.startPrank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptyDonationId.selector);
        registry.recordDonation(bytes32(0), donor1, CAMP_1, 1, RECEIPT_HASH_1, TX_REF_1);
        vm.expectRevert(CommonLinkRegistry.InvalidDonor.selector);
        registry.recordDonation(_donationId(1), address(0), CAMP_1, 1, RECEIPT_HASH_1, TX_REF_1);
        vm.expectRevert(CommonLinkRegistry.InvalidAmount.selector);
        registry.recordDonation(_donationId(1), donor1, CAMP_1, 0, RECEIPT_HASH_1, TX_REF_1);
        vm.expectRevert(CommonLinkRegistry.EmptyReceiptHash.selector);
        registry.recordDonation(_donationId(1), donor1, CAMP_1, 1, bytes32(0), TX_REF_1);
        vm.expectRevert(CommonLinkRegistry.EmptyTxRef.selector);
        registry.recordDonation(_donationId(1), donor1, CAMP_1, 1, RECEIPT_HASH_1, bytes32(0));
        vm.stopPrank();
    }

    function test_donation_revertsOnRaisedOverflow() public {
        uint96 nearMax = type(uint96).max - 1;
        _donate(_donationId(1), donor1, CAMP_1, nearMax);

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.RaisedOverflow.selector, CAMP_1, nearMax, uint96(2)));
        registry.recordDonation(_donationId(2), donor1, CAMP_1, 2, RECEIPT_HASH_1, TX_REF_1);
        assertEq(registry.getCampaign(CAMP_1).raised, nearMax);
    }

    function test_donation_revertsForNonRecorder() public {
        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, attacker, RECORDER_ROLE
            )
        );
        registry.recordDonation(_donationId(1), donor1, CAMP_1, 1, RECEIPT_HASH_1, TX_REF_1);
    }

    /// @dev Registered once, registered forever: nothing can make a registered campaign refuse a
    ///      donation, however long after its registration (closing it is a backend decision).
    function test_donation_acceptedYearsAfterRegistration() public {
        vm.warp(block.timestamp + 365 days * 5);
        _donate(_donationId(1), donor1, CAMP_1, 1000);
        assertEq(registry.getCampaign(CAMP_1).raised, 1000);
    }

    function testFuzz_donation_anyNonZeroAmount(uint96 amount) public {
        vm.assume(amount != 0);
        _donate(_donationId(1), donor1, CAMP_1, amount);
        assertEq(registry.getCampaign(CAMP_1).raised, amount);
        assertEq(registry.getDonorStats(donor1).totalDonated, amount);
    }
}

/// @notice Payout recording: proof only, integrity guards only.
contract RecordPayoutTest is CommonLinkBaseTest {
    event PayoutRecorded(bytes32 indexed payoutId, bytes32 indexed campaignId, uint96 amount, uint32 timestamp);

    function setUp() public override {
        super.setUp();
        _registerAsso1AndCamp1();
    }

    function _payout(bytes32 payoutId, uint96 amount) internal {
        vm.prank(recorder);
        registry.recordPayout(payoutId, CAMP_1, amount);
    }

    function test_payout_recordsAndEmits() public {
        vm.expectEmit(true, true, false, true, address(registry));
        emit PayoutRecorded(_payoutId(1), CAMP_1, 12_345, uint32(block.timestamp));
        _payout(_payoutId(1), 12_345);
        assertTrue(registry.payoutRecorded(_payoutId(1)));
        assertFalse(registry.payoutRecorded(_payoutId(2)));
    }

    function test_payout_movesNothing() public {
        _donate(_donationId(1), donor1, CAMP_1, 5_000);
        _payout(_payoutId(1), 3_000);
        assertEq(registry.getCampaign(CAMP_1).raised, 5_000, "payout must not alter raised");
        assertEq(registry.getDonorStats(donor1).totalDonated, 5_000);
        assertEq(address(registry).balance, 0, "registry never holds funds");
    }

    /// @dev No balance rule on-chain: the backend enforces the available balance.
    function test_payout_exceedingRaisedIsRecorded() public {
        _payout(_payoutId(1), 10_000);
        assertTrue(registry.payoutRecorded(_payoutId(1)));
    }

    function test_payout_maxUint96() public {
        _payout(_payoutId(1), type(uint96).max);
        assertTrue(registry.payoutRecorded(_payoutId(1)));
    }

    function test_payout_revertsOnDuplicate() public {
        _payout(_payoutId(1), 100);
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.PayoutAlreadyRecorded.selector, _payoutId(1)));
        registry.recordPayout(_payoutId(1), CAMP_1, 999);
    }

    function test_payout_sanityAndReferentialGuards() public {
        vm.startPrank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptyPayoutId.selector);
        registry.recordPayout(bytes32(0), CAMP_1, 100);
        vm.expectRevert(CommonLinkRegistry.InvalidAmount.selector);
        registry.recordPayout(_payoutId(1), CAMP_1, 0);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.CampaignNotRegistered.selector, CAMP_2));
        registry.recordPayout(_payoutId(1), CAMP_2, 100);
        vm.stopPrank();
    }

    function test_payout_revertsForNonRecorder() public {
        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, attacker, RECORDER_ROLE
            )
        );
        registry.recordPayout(_payoutId(1), CAMP_1, 100);
    }
}

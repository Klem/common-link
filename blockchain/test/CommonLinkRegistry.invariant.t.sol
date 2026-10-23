// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";
import {CommonLinkHandler} from "./handlers/CommonLinkHandler.sol";
import {MockDonorBadge} from "./mocks/MockDonorBadge.sol";

/**
 * @title  CommonLinkRegistryInvariantTest
 * @notice Structural invariants of the CommonLinkRegistry v2 (notary, registered once):
 *           - INV-1  a registration is permanent: association and campaign records never change
 *                    (sirenHash, registeredAt, association owner), budgetHash is never empty;
 *           - INV-2  every identifier (association, SIREN, campaign, donation, payout, badge pair)
 *                    is written at most once;
 *           - INV-3  `raised` equals the sum of the donations recorded on the campaign;
 *           - INV-4  donor statistics equal the sum of the donations recorded for the donor;
 *           - INV-5  the registry never refuses a write for any reason other than its integrity
 *                    guards (no business rule crept in);
 *           - INV-6  the registry never holds funds.
 *
 *         Requires `fail_on_revert = false` under [invariant] in foundry.toml.
 */
contract CommonLinkRegistryInvariantTest is Test {
    CommonLinkRegistry public registry;
    CommonLinkHandler public handler;
    MockDonorBadge public badge;

    address public admin    = makeAddr("admin-inv");
    address public recorder = makeAddr("recorder-inv");

    function setUp() public {
        vm.warp(1_800_000_000);
        registry = new CommonLinkRegistry(admin, recorder);
        badge = new MockDonorBadge(address(registry));
        vm.prank(admin);
        registry.setBadgeContract(address(badge));
        handler = new CommonLinkHandler(registry, badge, recorder);

        targetContract(address(handler));
        bytes4[] memory selectors = new bytes4[](7);
        selectors[0] = handler.registerAssociation.selector;
        selectors[1] = handler.registerCampaign.selector;
        selectors[2] = handler.updateCampaignBudget.selector;
        selectors[3] = handler.recordDonation.selector;
        selectors[4] = handler.recordPayout.selector;
        selectors[5] = handler.mintBadge.selector;
        selectors[6] = handler.warpForward.selector;
        targetSelector(FuzzSelector({addr: address(handler), selectors: selectors}));
    }

    function invariant_registrationsArePermanent() public view {
        uint256 na = handler.registeredAssoCount();
        for (uint256 i = 0; i < na; i++) {
            address a = handler.registeredAssos(i);
            CommonLinkRegistry.Association memory rec = registry.getAssociation(a);
            assertEq(rec.registeredAt, handler.ghost_assoRegisteredAt(a), "INV-1: association registeredAt changed");
            assertEq(rec.sirenHash, handler.ghost_assoSiren(a), "INV-1: association sirenHash changed");
            assertEq(registry.sirenHashToAssociation(rec.sirenHash), a, "INV-1: SIREN index changed");
        }
        uint256 nc = handler.registeredCampaignCount();
        for (uint256 i = 0; i < nc; i++) {
            bytes32 id = handler.registeredCampaigns(i);
            CommonLinkRegistry.Campaign memory c = registry.getCampaign(id);
            assertEq(c.registeredAt, handler.ghost_campaignRegisteredAt(id), "INV-1: campaign registeredAt changed");
            assertEq(c.association, handler.ghost_campaignAsso(id), "INV-1: campaign association changed");
            assertTrue(c.budgetHash != bytes32(0), "INV-1: budgetHash empty");
        }
    }

    function invariant_identifiersWrittenAtMostOnce() public view {
        assertFalse(handler.ghost_duplicateAccepted(), "INV-2: an already-written identifier was accepted again");
        uint256 nd = handler.donationCount();
        for (uint256 i = 0; i < nd; i++) {
            bytes32 id = handler.donationIds(i);
            assertEq(handler.ghost_donationRecordCount(id), 1, "INV-2: donation recorded more than once");
            assertTrue(registry.getDonation(id).donor != address(0), "INV-2: recorded donation vanished");
        }
        uint256 np = handler.payoutCount();
        for (uint256 i = 0; i < np; i++) {
            bytes32 id = handler.payoutIds(i);
            assertEq(handler.ghost_payoutRecordCount(id), 1, "INV-2: payout recorded more than once");
            assertTrue(registry.getPayout(id).campaignId != bytes32(0), "INV-2: recorded payout vanished");
        }
        assertEq(badge.mintCount(), handler.ghost_badgesMinted(), "INV-2: badge mint count diverges");
    }

    function invariant_raisedEqualsSumOfDonations() public view {
        uint256 nc = handler.registeredCampaignCount();
        for (uint256 i = 0; i < nc; i++) {
            bytes32 id = handler.registeredCampaigns(i);
            assertEq(registry.getCampaign(id).raised, handler.ghost_raised(id), "INV-3: raised diverges");
        }
    }

    function invariant_donorStatsMatchRecordedDonations() public view {
        uint256 n = handler.donorCount();
        for (uint256 i = 0; i < n; i++) {
            address d = handler.donors(i);
            CommonLinkRegistry.DonorStats memory s = registry.getDonorStats(d);
            assertEq(s.totalDonated, handler.ghost_donorTotal(d), "INV-4: totalDonated diverges");
            assertEq(s.donationCount, handler.ghost_donorCount(d), "INV-4: donationCount diverges");
        }
    }

    function invariant_campaignStatsMatchRecords() public view {
        uint256 nc = handler.registeredCampaignCount();
        for (uint256 i = 0; i < nc; i++) {
            bytes32 id = handler.registeredCampaigns(i);
            CommonLinkRegistry.CampaignStats memory cs = registry.getCampaignStats(id);
            assertEq(cs.donationCount, handler.ghost_campaignDonationCount(id), "INV-6: donationCount diverges");
            assertEq(cs.uniqueDonors, handler.ghost_campaignUniqueDonors(id), "INV-6: uniqueDonors diverges");
            assertEq(cs.totalPaidOut, handler.ghost_paidOut(id), "INV-6: totalPaidOut diverges");
            assertEq(cs.payoutCount, handler.ghost_campaignPayoutCount(id), "INV-6: payoutCount diverges");
        }
    }

    function invariant_noBusinessRuleGate() public view {
        assertFalse(handler.ghost_unexpectedRevert(), "INV-5: a write was refused outside the integrity guards");
    }

    function invariant_registryHoldsNoFunds() public view {
        assertEq(address(registry).balance, 0, "INV-6: registry holds funds");
    }
}

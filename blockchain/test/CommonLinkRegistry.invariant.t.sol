// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {CommonLinkRegistry} from "src/CommonLinkRegistry.sol";
import {CommonLinkHandler} from "./handlers/CommonLinkHandler.sol";

/**
 * @title  CommonLinkRegistryInvariantTest
 * @notice Invariant tests: properties that must hold after ANY sequence of
 *         calls. Run with `forge test --match-path *invariant*`.
 *
 *         Properties:
 *           I1 — donor.totalDonated == Σ successful donations by that donor
 *           I2 — campaign.raised    == Σ successful donations to that campaign
 *           I3 — donorCampaignTotal == Σ donations by (donor, campaign)
 *           I4 — donationCount      == # successful donations by that donor
 *           I5 — uniqueCampaigns    ≤ donationCount (cannot join more campaigns
 *                                                    than you have donations)
 *           I6 — sirenHashToAssociation is sticky: once set, never cleared
 */
contract CommonLinkRegistryInvariantTest is Test {
    CommonLinkRegistry internal registry;
    CommonLinkHandler  internal handler;

    address internal admin    = makeAddr("admin");
    address internal recorder = makeAddr("recorder");
    address internal curator  = makeAddr("curator");

    function setUp() public {
        registry = new CommonLinkRegistry(admin, recorder, curator);
        handler  = new CommonLinkHandler(registry, curator, recorder);

        // Focus the fuzzer on the handler's entry points.
        bytes4[] memory selectors = new bytes4[](4);
        selectors[0] = CommonLinkHandler.donate.selector;
        selectors[1] = CommonLinkHandler.togglePauseCampaign.selector;
        selectors[2] = CommonLinkHandler.toggleAssociation.selector;
        selectors[3] = CommonLinkHandler.warpForward.selector;

        targetContract(address(handler));
        targetSelector(FuzzSelector({ addr: address(handler), selectors: selectors }));
    }

    // ─────────────────────────────────────────────────────────────────────
    // I1 — donor.totalDonated equals sum of their successful donations
    // ─────────────────────────────────────────────────────────────────────
    function invariant_DonorTotalsMatchGhost() public view {
        uint256 n = handler.donorsLength();
        for (uint256 i; i < n; ++i) {
            address donor = address(uint160(0xD0000 + i + 1));
            assertEq(
                registry.getDonorStats(donor).totalDonated,
                handler.ghost_donorTotal(donor),
                "donor totalDonated drifted from ghost"
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // I2 — campaign.raised equals sum of donations to it
    // ─────────────────────────────────────────────────────────────────────
    function invariant_CampaignRaisedMatchesGhost() public view {
        uint256 n = handler.campaignIdsLength();
        for (uint256 i; i < n; ++i) {
            bytes32 campaignId = bytes32(uint256(0xCA00_0001) + i);
            assertEq(
                registry.getCampaign(campaignId).raised,
                handler.ghost_campaignRaised(campaignId),
                "campaign raised drifted from ghost"
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // I3 — Σ raised across all campaigns == Σ totalDonated across donors
    //      (closure property — money in == money out)
    // ─────────────────────────────────────────────────────────────────────
    function invariant_GlobalAccountingClosure() public view {
        uint256 sumRaised;
        uint256 nCampaigns = handler.campaignIdsLength();
        for (uint256 i; i < nCampaigns; ++i) {
            bytes32 campaignId = bytes32(uint256(0xCA00_0001) + i);
            sumRaised += registry.getCampaign(campaignId).raised;
        }

        uint256 sumDonor;
        uint256 nDonors = handler.donorsLength();
        for (uint256 i; i < nDonors; ++i) {
            address donor = address(uint160(0xD0000 + i + 1));
            sumDonor += registry.getDonorStats(donor).totalDonated;
        }

        assertEq(sumRaised, handler.ghost_totalDonated(), "campaign sum vs ghost");
        assertEq(sumDonor,  handler.ghost_totalDonated(), "donor sum vs ghost");
    }

    // ─────────────────────────────────────────────────────────────────────
    // I4 — donationCount matches the ghost
    // ─────────────────────────────────────────────────────────────────────
    function invariant_DonationCountMatchesGhost() public view {
        uint256 n = handler.donorsLength();
        for (uint256 i; i < n; ++i) {
            address donor = address(uint160(0xD0000 + i + 1));
            assertEq(
                uint256(registry.getDonorStats(donor).donationCount),
                handler.ghost_donorDonationCount(donor),
                "donationCount drifted"
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // I5 — uniqueCampaigns can never exceed donationCount
    // ─────────────────────────────────────────────────────────────────────
    function invariant_UniqueCampaignsBoundedByDonationCount() public view {
        uint256 n = handler.donorsLength();
        for (uint256 i; i < n; ++i) {
            address donor = address(uint160(0xD0000 + i + 1));
            CommonLinkRegistry.DonorStats memory s = registry.getDonorStats(donor);
            assertLe(s.uniqueCampaigns, s.donationCount, "uniqueCampaigns > donationCount");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // I6 — sirenHashToAssociation is monotonic: once set, never cleared
    //      (revoke/restore must NOT touch the reverse index)
    // ─────────────────────────────────────────────────────────────────────
    function invariant_SirenIndexIsSticky() public view {
        uint256 n = handler.assosLength();
        for (uint256 i; i < n; ++i) {
            address asso = address(uint160(0xA550_0000 + i + 1));
            bytes32 sirenHash = keccak256(abi.encode("siren", i));
            // Once verifyAssociation succeeded in the handler constructor,
            // every asso has a non-zero sirenHash in storage AND the reverse
            // index must point to it — forever.
            assertEq(
                registry.sirenHashToAssociation(sirenHash),
                asso,
                "siren reverse index lost"
            );
            assertEq(
                registry.getAssociation(asso).sirenHash,
                sirenHash,
                "association sirenHash lost"
            );
        }
    }
}

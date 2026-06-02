// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";
import {CommonLinkHandler} from "./handlers/CommonLinkHandler.sol";

/**
 * @title  CommonLinkRegistryInvariantTest
 * @notice Foundry invariants for the CommonLinkRegistry campaign lifecycle.
 *         Pins down the four properties introduced by the v1.3 spec changeset:
 *
 *           - INV-24: endDate / status coherence
 *           - INV-25: revertCampaignToDraft requires raised == 0
 *           - INV-26: updateCampaignBudget forbidden on terminal or revoked asso
 *           - INV-27: startDate equals create-time block.timestamp and never changes
 *
 *         Pre-v1.3 invariants (donor stat consistency, immutability beyond
 *         budgetHash, SIREN uniqueness) are exercised by a separate handler in
 *         this same suite — extending coverage on the new surface is a follow-up
 *         commit; this file pins the new properties first.
 *
 *         Requires in `foundry.toml`:
 *           [invariant]
 *           runs = 256
 *           depth = 50
 *           fail_on_revert = false
 */
contract CommonLinkRegistryInvariantTest is Test {
    CommonLinkRegistry public registry;
    CommonLinkHandler public handler;

    address public admin    = makeAddr("admin-inv");
    address public recorder = makeAddr("recorder-inv");
    address public curator  = makeAddr("curator-inv");

    function setUp() public {
        vm.warp(1_800_000_000);
        registry = new CommonLinkRegistry(admin, recorder, curator);
        handler = new CommonLinkHandler(registry, recorder, curator);

        targetContract(address(handler));

        bytes4[] memory selectors = new bytes4[](11);
        selectors[0] = handler.createCampaign.selector;
        selectors[1] = handler.publishCampaign.selector;
        selectors[2] = handler.revertCampaignToDraft.selector;
        selectors[3] = handler.updateCampaignBudget.selector;
        selectors[4] = handler.pauseCampaign.selector;
        selectors[5] = handler.unpauseCampaign.selector;
        selectors[6] = handler.completeCampaign.selector;
        selectors[7] = handler.cancelCampaign.selector;
        selectors[8] = handler.recordDonation.selector;
        selectors[9] = handler.revokeAsso.selector;
        selectors[10] = handler.warpForward.selector;
        targetSelector(FuzzSelector({addr: address(handler), selectors: selectors}));
    }

    // ─────────────────────────────────────────────────────────────────────
    // INV-24 — endDate / status coherence
    //   status ∈ {Draft, Active, Paused}  ⟹  endDate == 0
    //   status ∈ {Completed, Cancelled}   ⟹  endDate >= startDate && endDate > 0
    // ─────────────────────────────────────────────────────────────────────

    function invariant_endDateMatchesStatus() public view {
        uint256 n = handler.campaignCount();
        for (uint256 i = 0; i < n; i++) {
            bytes32 id = handler.campaignIds(i);
            CommonLinkRegistry.Campaign memory c = registry.getCampaign(id);

            if (
                c.status == CommonLinkRegistry.CampaignStatus.Draft
                || c.status == CommonLinkRegistry.CampaignStatus.Active
                || c.status == CommonLinkRegistry.CampaignStatus.Paused
            ) {
                assertEq(c.endDate, 0, "INV-24: non-terminal campaign has non-zero endDate");
            } else {
                assertGt(c.endDate, 0, "INV-24: terminal campaign has zero endDate");
                assertGe(c.endDate, c.startDate, "INV-24: endDate < startDate on terminal campaign");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // INV-25 — revertCampaignToDraft never succeeded with raised > 0
    // ─────────────────────────────────────────────────────────────────────

    function invariant_revertToDraftRequiresZeroRaised() public view {
        assertFalse(
            handler.ghost_invalidRevertHappened(),
            "INV-25: revertCampaignToDraft succeeded while raised > 0 or asso revoked"
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // INV-26 — updateCampaignBudget never succeeded if terminal or asso revoked
    // ─────────────────────────────────────────────────────────────────────

    function invariant_updateBudgetForbiddenOnTerminalOrRevoked() public view {
        assertFalse(
            handler.ghost_invalidBudgetUpdateHappened(),
            "INV-26: updateCampaignBudget succeeded on terminal status or revoked asso"
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // INV-27 — startDate equals create-time block.timestamp and never changes
    // ─────────────────────────────────────────────────────────────────────

    function invariant_startDateEqualsCreationBlockTimestamp() public view {
        uint256 n = handler.campaignCount();
        for (uint256 i = 0; i < n; i++) {
            bytes32 id = handler.campaignIds(i);
            uint32 stored = registry.getCampaign(id).startDate;
            uint32 expected = handler.ghost_createBlockTimestamp(id);
            assertEq(stored, expected, "INV-27: startDate diverges from creation block.timestamp");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Bonus — budgetHash never empty post-creation
    // ─────────────────────────────────────────────────────────────────────

    function invariant_budgetHashNeverEmpty() public view {
        uint256 n = handler.campaignCount();
        for (uint256 i = 0; i < n; i++) {
            bytes32 id = handler.campaignIds(i);
            assertTrue(
                registry.getCampaign(id).budgetHash != bytes32(0),
                "budgetHash must never become empty post-creation"
            );
        }
    }
}
// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {CommonLinkRegistry} from "../../src/CommonLinkRegistry.sol";

/**
 * @title  CommonLinkHandler
 * @notice Foundry invariant handler for the CommonLinkRegistry campaign lifecycle.
 *         Wraps the registry's external functions with bounded random inputs and
 *         maintains ghost state about every campaign ever created, so invariants
 *         can be checked against the full history of actions.
 *
 *         `vm.expectRevert` is NOT used inside handler actions. Instead the actions
 *         pre-filter inputs so that calls succeed when the spec says they should,
 *         and let other calls revert without failing the run. Two ghost flags
 *         (`ghost_invalidRevertHappened`, `ghost_invalidBudgetUpdateHappened`) catch
 *         the case where a write succeeds despite spec saying it should revert —
 *         that's a guard the contract would be missing.
 *
 *         Requires `foundry.toml` to set `fail_on_revert = false` under [invariant].
 */
contract CommonLinkHandler is Test {
    CommonLinkRegistry public registry;

    address public immutable recorder;
    address public immutable curator;

    // ─ Actors ─
    address[] public assos;
    address[] public donors;

    // ─ Tracked campaigns ─
    bytes32[] public campaignIds;
    mapping(bytes32 => bool) public seen;

    // ─ Ghost state ─

    /// @dev Mirror of every state transition we've successfully driven.
    mapping(bytes32 => CommonLinkRegistry.CampaignStatus) public ghost_currentStatus;
    mapping(bytes32 => uint32) public ghost_endDateOnClose;
    mapping(bytes32 => uint32) public ghost_startDate;
    mapping(bytes32 => uint32) public ghost_createBlockTimestamp;

    /// @dev Did `revertCampaignToDraft` ever succeed despite spec violation?
    bool public ghost_invalidRevertHappened;

    /// @dev Did `updateCampaignBudget` ever succeed despite spec violation?
    bool public ghost_invalidBudgetUpdateHappened;

    uint256 private _seed;

    constructor(CommonLinkRegistry _registry, address _recorder, address _curator) {
        registry = _registry;
        recorder = _recorder;
        curator = _curator;

        // Three pre-verified assos and three donors.
        for (uint256 i = 0; i < 3; i++) {
            address a = makeAddr(string.concat("asso", vm.toString(i + 100)));
            assos.push(a);
            vm.prank(curator);
            registry.verifyAssociation(a, keccak256(abi.encodePacked("siren", i)));
        }
        for (uint256 i = 0; i < 3; i++) {
            donors.push(makeAddr(string.concat("donor", vm.toString(i + 100))));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Action surface
    // ─────────────────────────────────────────────────────────────────────

    function createCampaign(uint256 assoIdx, uint96 goal) external {
        address asso = assos[assoIdx % assos.length];
        if (!_isVerified(asso)) return;
        if (goal == 0) return;

        bytes32 id = bytes32(uint256(++_seed) << 128);
        if (seen[id]) return;

        vm.prank(recorder);
        try registry.createCampaign(id, asso, goal, 0, keccak256(abi.encodePacked("budget", _seed))) {
            campaignIds.push(id);
            seen[id] = true;
            ghost_currentStatus[id] = CommonLinkRegistry.CampaignStatus.Draft;
            ghost_startDate[id] = uint32(block.timestamp);
            ghost_createBlockTimestamp[id] = uint32(block.timestamp);
        } catch {}
    }

    function publishCampaign(uint256 idx) external {
        if (campaignIds.length == 0) return;
        bytes32 id = campaignIds[idx % campaignIds.length];

        if (ghost_currentStatus[id] != CommonLinkRegistry.CampaignStatus.Draft) return;

        address asso = registry.getCampaign(id).association;
        if (!_isVerified(asso)) return;

        vm.prank(recorder);
        try registry.publishCampaign(id) {
            ghost_currentStatus[id] = CommonLinkRegistry.CampaignStatus.Active;
        } catch {}
    }

    function revertCampaignToDraft(uint256 idx) external {
        if (campaignIds.length == 0) return;
        bytes32 id = campaignIds[idx % campaignIds.length];

        CommonLinkRegistry.CampaignStatus s = ghost_currentStatus[id];
        if (s != CommonLinkRegistry.CampaignStatus.Active && s != CommonLinkRegistry.CampaignStatus.Paused) return;

        uint96 raised = registry.getCampaign(id).raised;
        address asso = registry.getCampaign(id).association;

        vm.prank(curator);
        try registry.revertCampaignToDraft(id) {
            ghost_currentStatus[id] = CommonLinkRegistry.CampaignStatus.Draft;
            // If this succeeded then raised must have been 0 and the asso verified.
            if (raised != 0) ghost_invalidRevertHappened = true;
            if (!_isVerified(asso)) ghost_invalidRevertHappened = true;
        } catch {}
    }

    function updateCampaignBudget(uint256 idx, uint256 hashSeed) external {
        if (campaignIds.length == 0) return;
        bytes32 id = campaignIds[idx % campaignIds.length];

        CommonLinkRegistry.CampaignStatus statusBefore = ghost_currentStatus[id];
        bytes32 oldHash = registry.getCampaign(id).budgetHash;
        bytes32 newHash = keccak256(abi.encodePacked("update", hashSeed));
        if (newHash == oldHash) return;
        if (newHash == bytes32(0)) return;

        address asso = registry.getCampaign(id).association;
        bool assoVerified = _isVerified(asso);

        vm.prank(recorder);
        try registry.updateCampaignBudget(id, newHash) {
            bool terminal = statusBefore == CommonLinkRegistry.CampaignStatus.Completed
                || statusBefore == CommonLinkRegistry.CampaignStatus.Cancelled;
            if (terminal || !assoVerified) ghost_invalidBudgetUpdateHappened = true;
        } catch {}
    }

    function pauseCampaign(uint256 idx) external {
        if (campaignIds.length == 0) return;
        bytes32 id = campaignIds[idx % campaignIds.length];
        if (ghost_currentStatus[id] != CommonLinkRegistry.CampaignStatus.Active) return;

        vm.prank(curator);
        try registry.pauseCampaign(id) {
            ghost_currentStatus[id] = CommonLinkRegistry.CampaignStatus.Paused;
        } catch {}
    }

    function unpauseCampaign(uint256 idx) external {
        if (campaignIds.length == 0) return;
        bytes32 id = campaignIds[idx % campaignIds.length];
        if (ghost_currentStatus[id] != CommonLinkRegistry.CampaignStatus.Paused) return;

        address asso = registry.getCampaign(id).association;
        if (!_isVerified(asso)) return;

        vm.prank(curator);
        try registry.unpauseCampaign(id) {
            ghost_currentStatus[id] = CommonLinkRegistry.CampaignStatus.Active;
        } catch {}
    }

    function completeCampaign(uint256 idx) external {
        if (campaignIds.length == 0) return;
        bytes32 id = campaignIds[idx % campaignIds.length];
        CommonLinkRegistry.CampaignStatus s = ghost_currentStatus[id];
        if (s != CommonLinkRegistry.CampaignStatus.Active && s != CommonLinkRegistry.CampaignStatus.Paused) return;

        vm.prank(curator);
        try registry.completeCampaign(id) {
            ghost_currentStatus[id] = CommonLinkRegistry.CampaignStatus.Completed;
            ghost_endDateOnClose[id] = uint32(block.timestamp);
        } catch {}
    }

    function cancelCampaign(uint256 idx) external {
        if (campaignIds.length == 0) return;
        bytes32 id = campaignIds[idx % campaignIds.length];
        CommonLinkRegistry.CampaignStatus s = ghost_currentStatus[id];
        if (
            s != CommonLinkRegistry.CampaignStatus.Draft
            && s != CommonLinkRegistry.CampaignStatus.Active
            && s != CommonLinkRegistry.CampaignStatus.Paused
        ) return;

        vm.prank(curator);
        try registry.cancelCampaign(id) {
            ghost_currentStatus[id] = CommonLinkRegistry.CampaignStatus.Cancelled;
            ghost_endDateOnClose[id] = uint32(block.timestamp);
        } catch {}
    }

    function recordDonation(uint256 idx, uint256 donorIdx, uint96 amount) external {
        if (campaignIds.length == 0) return;
        bytes32 id = campaignIds[idx % campaignIds.length];
        if (ghost_currentStatus[id] != CommonLinkRegistry.CampaignStatus.Active) return;
        if (amount == 0) return;

        uint96 currentRaised = registry.getCampaign(id).raised;
        unchecked {
            if (uint256(currentRaised) + uint256(amount) > type(uint96).max) return;
        }

        address asso = registry.getCampaign(id).association;
        if (!_isVerified(asso)) return;

        address donor = donors[donorIdx % donors.length];
        bytes32 donId = bytes32((uint256(++_seed) | (uint256(1) << 127)) << 128);

        vm.prank(recorder);
        try registry.recordDonation(donId, donor, id, amount, keccak256("receipt"), bytes32("tx-ref")) {} catch {}
    }

    function revokeAsso(uint256 idx) external {
        address a = assos[idx % assos.length];
        if (!_isVerified(a)) return;

        vm.prank(curator);
        try registry.revokeAssociation(a) {} catch {}
    }

    function restoreAsso(uint256 idx) external {
        address a = assos[idx % assos.length];
        if (_isVerified(a)) return;

        vm.prank(curator);
        try registry.restoreAssociation(a) {} catch {}
    }

    /// @dev Advances time to expose state-transition timestamps to invariants.
    function warpForward(uint16 seconds_) external {
        vm.warp(block.timestamp + uint256(seconds_) + 1);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    function _isVerified(address asso) internal view returns (bool) {
        return registry.getAssociation(asso).verified;
    }

    function campaignCount() external view returns (uint256) {
        return campaignIds.length;
    }
}

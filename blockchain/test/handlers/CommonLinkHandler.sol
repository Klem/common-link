// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {CommonLinkRegistry} from "../../src/CommonLinkRegistry.sol";
import {MockDonorBadge} from "../mocks/MockDonorBadge.sol";

/**
 * @title  CommonLinkHandler
 * @notice Foundry invariant handler for the CommonLinkRegistry v2 (notary, registered once).
 *         Drives every write with bounded random inputs over SMALL identifier domains, so the
 *         fuzzer regularly retries ids that are already registered/recorded. Ghost state mirrors
 *         what was successfully written:
 *           - an attempt the registry should accept but refuses sets `ghost_unexpectedRevert`;
 *           - a duplicate that the registry accepts sets `ghost_duplicateAccepted`;
 *           - duplicates correctly refused are counted (`ghost_duplicateRejected`) to prove the
 *             "at most once" invariants are exercised and not vacuous.
 *         `vm.expectRevert` is NOT used inside handler actions.
 */
contract CommonLinkHandler is Test {
    CommonLinkRegistry public registry;
    MockDonorBadge public badge;
    address public immutable recorder;

    // ─ Actors (fixed small domains) ─
    address[] public assos;
    address[] public donors;

    // ─ Ghost state: registrations ─
    mapping(address => uint32) public ghost_assoRegisteredAt;
    mapping(address => bytes32) public ghost_assoSiren;
    address[] public registeredAssos;

    mapping(bytes32 => uint32) public ghost_campaignRegisteredAt;
    mapping(bytes32 => address) public ghost_campaignAsso;
    bytes32[] public registeredCampaigns;

    // ─ Ghost state: donations ─
    mapping(bytes32 => uint256) public ghost_donationRecordCount;
    bytes32[] public donationIds;
    mapping(bytes32 => uint256) public ghost_raised;
    mapping(address => uint256) public ghost_donorTotal;
    mapping(address => uint256) public ghost_donorCount;

    // ─ Ghost state: payouts and badges ─
    mapping(bytes32 => uint256) public ghost_payoutRecordCount;
    bytes32[] public payoutIds;
    uint256 public ghost_badgesMinted;

    // ─ Flags ─
    bool public ghost_unexpectedRevert;
    bool public ghost_duplicateAccepted;
    uint256 public ghost_duplicateRejected;

    constructor(CommonLinkRegistry _registry, MockDonorBadge _badge, address _recorder) {
        registry = _registry;
        badge = _badge;
        recorder = _recorder;
        for (uint256 i = 0; i < 4; i++) {
            assos.push(makeAddr(string.concat("asso", vm.toString(i + 100))));
            donors.push(makeAddr(string.concat("donor", vm.toString(i + 100))));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Registrations
    // ─────────────────────────────────────────────────────────────────────

    /// @dev `sirenVariant` lets a re-registration attempt carry a DIFFERENT SIREN, so the
    ///      "association registered once" guard is exercised on its own (not masked by the SIREN one).
    function registerAssociation(uint256 assoIdx, uint256 sirenVariant) external {
        address asso = assos[assoIdx % assos.length];
        bytes32 siren = keccak256(abi.encodePacked("siren", asso, sirenVariant % 2));
        bool already = ghost_assoRegisteredAt[asso] != 0;

        vm.prank(recorder);
        try registry.registerAssociation(asso, siren) {
            if (already) {
                ghost_duplicateAccepted = true;
            } else {
                ghost_assoRegisteredAt[asso] = uint32(block.timestamp);
                ghost_assoSiren[asso] = siren;
                registeredAssos.push(asso);
            }
        } catch {
            if (already) ghost_duplicateRejected += 1;
            else ghost_unexpectedRevert = true;
        }
    }

    function registerCampaign(uint256 campSeed, uint256 assoIdx) external {
        if (registeredAssos.length == 0) return;
        bytes32 id = bytes32((campSeed % 6 + 1) << 128);
        address asso = registeredAssos[assoIdx % registeredAssos.length];
        bool already = ghost_campaignRegisteredAt[id] != 0;

        vm.prank(recorder);
        try registry.registerCampaign(id, asso, keccak256(abi.encodePacked("budget", id))) {
            if (already) {
                ghost_duplicateAccepted = true;
            } else {
                ghost_campaignRegisteredAt[id] = uint32(block.timestamp);
                ghost_campaignAsso[id] = asso;
                registeredCampaigns.push(id);
            }
        } catch {
            if (already) ghost_duplicateRejected += 1;
            else ghost_unexpectedRevert = true;
        }
    }

    function updateCampaignBudget(uint256 idx, uint256 hashSeed) external {
        if (registeredCampaigns.length == 0) return;
        bytes32 id = registeredCampaigns[idx % registeredCampaigns.length];

        vm.prank(recorder);
        try registry.updateCampaignBudget(id, keccak256(abi.encodePacked("update", hashSeed))) {}
        catch {
            ghost_unexpectedRevert = true;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Records
    // ─────────────────────────────────────────────────────────────────────

    function recordDonation(uint256 idx, uint256 donorIdx, uint256 donSeed, uint96 amount) external {
        if (registeredCampaigns.length == 0 || amount == 0) return;
        bytes32 id = registeredCampaigns[idx % registeredCampaigns.length];
        address donor = donors[donorIdx % donors.length];
        bytes32 donId = bytes32(((donSeed % 8 + 1) | (uint256(1) << 127)) << 128);
        bool already = ghost_donationRecordCount[donId] != 0;

        // Skip inputs that would legitimately overflow `raised` (RaisedOverflow is a kept guard).
        if (!already && uint256(registry.getCampaign(id).raised) + amount > type(uint96).max) return;

        vm.prank(recorder);
        try registry.recordDonation(donId, donor, id, amount, keccak256("receipt"), bytes32("tx-ref")) {
            if (already) {
                ghost_duplicateAccepted = true;
            } else {
                donationIds.push(donId);
                ghost_donationRecordCount[donId] = 1;
                ghost_raised[id] += amount;
                ghost_donorTotal[donor] += amount;
                ghost_donorCount[donor] += 1;
            }
        } catch {
            if (already) ghost_duplicateRejected += 1;
            else ghost_unexpectedRevert = true;
        }
    }

    function recordPayout(uint256 idx, uint256 payoutSeed, uint96 amount) external {
        if (registeredCampaigns.length == 0 || amount == 0) return;
        bytes32 id = registeredCampaigns[idx % registeredCampaigns.length];
        bytes32 payoutId = bytes32(((payoutSeed % 8 + 1) | (uint256(1) << 126)) << 128);
        bool already = ghost_payoutRecordCount[payoutId] != 0;

        vm.prank(recorder);
        try registry.recordPayout(payoutId, id, amount) {
            if (already) {
                ghost_duplicateAccepted = true;
            } else {
                payoutIds.push(payoutId);
                ghost_payoutRecordCount[payoutId] = 1;
            }
        } catch {
            if (already) ghost_duplicateRejected += 1;
            else ghost_unexpectedRevert = true;
        }
    }

    function mintBadge(uint256 donorIdx, uint256 typeSeed) external {
        address donor = donors[donorIdx % donors.length];
        if (ghost_donorCount[donor] == 0) return; // DonorUnknown is a kept integrity guard
        uint16 badgeType = uint16(typeSeed % 2 + 1);
        bool already = badge.hasBadge(donor, badgeType);

        vm.prank(recorder);
        try registry.mintBadge(donor, badgeType) {
            if (already) ghost_duplicateAccepted = true;
            else ghost_badgesMinted += 1;
        } catch {
            if (already) ghost_duplicateRejected += 1;
            else ghost_unexpectedRevert = true;
        }
    }

    function warpForward(uint16 seconds_) external {
        vm.warp(block.timestamp + uint256(seconds_) + 1);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Views for invariants
    // ─────────────────────────────────────────────────────────────────────

    function registeredAssoCount() external view returns (uint256) {
        return registeredAssos.length;
    }

    function registeredCampaignCount() external view returns (uint256) {
        return registeredCampaigns.length;
    }

    function donationCount() external view returns (uint256) {
        return donationIds.length;
    }

    function payoutCount() external view returns (uint256) {
        return payoutIds.length;
    }

    function donorCount() external view returns (uint256) {
        return donors.length;
    }
}

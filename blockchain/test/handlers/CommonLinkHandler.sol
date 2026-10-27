// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {CommonLinkRegistry} from "../../src/CommonLinkRegistry.sol";

/**
 * @title  CommonLinkHandler
 * @notice Foundry invariant handler for the CommonLinkRegistry hub (notary, registered once).
 *         Drives every write with bounded random inputs over SMALL identifier domains, so the
 *         fuzzer regularly retries ids that are already registered/recorded. Ghost state mirrors
 *         what was successfully written:
 *           - an attempt the registry should accept but refuses sets `ghost_unexpectedRevert`;
 *           - a duplicate that the registry accepts sets `ghost_duplicateAccepted`;
 *           - duplicates correctly refused are counted (`ghost_duplicateRejected`) to prove the
 *             "at most once" invariants are exercised and not vacuous;
 *           - pause / unpause are driven by the pauser, the admin and a third party: a write accepted
 *             while paused sets `ghost_pauseBreach`; a pause or resume accepted from the wrong role sets
 *             `ghost_roleBreach`. Writes refused because the registry is paused are expected, not flagged.
 *         `vm.expectRevert` is NOT used inside handler actions.
 */
contract CommonLinkHandler is Test {
    CommonLinkRegistry public registry;
    address public immutable admin;
    address public immutable recorder;
    address public immutable pauser;
    address public immutable stranger;

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

    // ─ Ghost state: payouts ─
    mapping(bytes32 => uint256) public ghost_payoutRecordCount;
    bytes32[] public payoutIds;

    // ─ Ghost state: campaign statistics ─
    mapping(bytes32 => uint256) public ghost_campaignDonationCount;
    mapping(bytes32 => uint256) public ghost_campaignUniqueDonors;
    mapping(address => mapping(bytes32 => bool)) internal ghost_donatedTo;
    mapping(bytes32 => uint256) public ghost_paidOut;
    mapping(bytes32 => uint256) public ghost_campaignPayoutCount;

    // ─ Flags ─
    bool public ghost_unexpectedRevert;
    bool public ghost_duplicateAccepted;
    uint256 public ghost_duplicateRejected;
    bool public ghost_pauseBreach;
    bool public ghost_roleBreach;
    uint256 public ghost_pausedRejected;

    constructor(CommonLinkRegistry _registry, address _admin, address _recorder, address _pauser) {
        registry = _registry;
        admin = _admin;
        recorder = _recorder;
        pauser = _pauser;
        stranger = makeAddr("stranger-inv");
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
        bool paused = registry.paused();

        vm.prank(recorder);
        try registry.registerAssociation(asso, siren) {
            if (paused) ghost_pauseBreach = true;
            if (already) {
                ghost_duplicateAccepted = true;
            } else {
                ghost_assoRegisteredAt[asso] = uint32(block.timestamp);
                ghost_assoSiren[asso] = siren;
                registeredAssos.push(asso);
            }
        } catch {
            _onRevert(paused, already);
        }
    }

    function registerCampaign(uint256 campSeed, uint256 assoIdx) external {
        if (registeredAssos.length == 0) return;
        bytes32 id = bytes32((campSeed % 6 + 1) << 128);
        address asso = registeredAssos[assoIdx % registeredAssos.length];
        bool already = ghost_campaignRegisteredAt[id] != 0;
        bool paused = registry.paused();

        vm.prank(recorder);
        try registry.registerCampaign(id, asso, keccak256(abi.encodePacked("budget", id))) {
            if (paused) ghost_pauseBreach = true;
            if (already) {
                ghost_duplicateAccepted = true;
            } else {
                ghost_campaignRegisteredAt[id] = uint32(block.timestamp);
                ghost_campaignAsso[id] = asso;
                registeredCampaigns.push(id);
            }
        } catch {
            _onRevert(paused, already);
        }
    }

    function updateCampaignBudget(uint256 idx, uint256 hashSeed) external {
        if (registeredCampaigns.length == 0) return;
        bytes32 id = registeredCampaigns[idx % registeredCampaigns.length];
        bool paused = registry.paused();

        vm.prank(recorder);
        try registry.updateCampaignBudget(id, keccak256(abi.encodePacked("update", hashSeed))) {
            if (paused) ghost_pauseBreach = true;
        } catch {
            _onRevert(paused, false);
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
        bool paused = registry.paused();

        vm.prank(recorder);
        try registry.recordDonation(donId, donor, id, amount, keccak256("receipt"), bytes32("tx-ref")) {
            if (paused) ghost_pauseBreach = true;
            if (already) {
                ghost_duplicateAccepted = true;
            } else {
                donationIds.push(donId);
                ghost_donationRecordCount[donId] = 1;
                ghost_raised[id] += amount;
                ghost_donorTotal[donor] += amount;
                ghost_donorCount[donor] += 1;
                ghost_campaignDonationCount[id] += 1;
                if (!ghost_donatedTo[donor][id]) {
                    ghost_donatedTo[donor][id] = true;
                    ghost_campaignUniqueDonors[id] += 1;
                }
            }
        } catch {
            _onRevert(paused, already);
        }
    }

    function recordPayout(uint256 idx, uint256 payoutSeed, uint96 amount) external {
        if (registeredCampaigns.length == 0 || amount == 0) return;
        bytes32 id = registeredCampaigns[idx % registeredCampaigns.length];
        bytes32 payoutId = bytes32(((payoutSeed % 8 + 1) | (uint256(1) << 126)) << 128);
        bool already = ghost_payoutRecordCount[payoutId] != 0;

        // Skip inputs that would legitimately overflow `totalPaidOut` (PaidOutOverflow is a kept guard).
        if (!already && uint256(registry.getCampaignStats(id).totalPaidOut) + amount > type(uint96).max) return;
        bool paused = registry.paused();

        vm.prank(recorder);
        try registry.recordPayout(payoutId, id, amount, bytes32("bridge-tx")) {
            if (paused) ghost_pauseBreach = true;
            if (already) {
                ghost_duplicateAccepted = true;
            } else {
                payoutIds.push(payoutId);
                ghost_payoutRecordCount[payoutId] = 1;
                ghost_paidOut[id] += amount;
                ghost_campaignPayoutCount[id] += 1;
            }
        } catch {
            _onRevert(paused, already);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pause / resume, by the right and the wrong roles
    // ─────────────────────────────────────────────────────────────────────

    /// @dev actor 0 = pauser, 1 = admin, 2 = recorder, 3 = stranger. Only the pauser may pause.
    function pause(uint256 actorSeed) external {
        address actor = _actor(actorSeed);
        if (registry.paused()) return;
        vm.prank(actor);
        try registry.pause() {
            if (actor != pauser) ghost_roleBreach = true;
        } catch {
            if (actor == pauser) ghost_unexpectedRevert = true;
        }
    }

    /// @dev Only the admin and the pauser key (which holds UNPAUSER_ROLE) may resume.
    function unpause(uint256 actorSeed) external {
        address actor = _actor(actorSeed);
        if (!registry.paused()) return;
        bool allowed = actor == admin || actor == pauser;
        vm.prank(actor);
        try registry.unpause() {
            if (!allowed) ghost_roleBreach = true;
        } catch {
            if (allowed) ghost_unexpectedRevert = true;
        }
    }

    function _actor(uint256 seed) internal view returns (address) {
        uint256 i = seed % 4;
        if (i == 0) return pauser;
        if (i == 1) return admin;
        if (i == 2) return recorder;
        return stranger;
    }

    /// @dev Classifies a refused write: expected while paused or on a duplicate, unexpected otherwise.
    function _onRevert(bool paused, bool already) internal {
        if (paused) ghost_pausedRejected += 1;
        else if (already) ghost_duplicateRejected += 1;
        else ghost_unexpectedRevert = true;
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

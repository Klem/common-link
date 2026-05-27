// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {CommonLinkRegistry} from "src/CommonLinkRegistry.sol";

/**
 * @title  CommonLinkHandler
 * @notice Bounded handler for Foundry invariant testing.
 *         Routes randomised fuzz calls through realistic happy-path entry points
 *         and tracks ghost state we can cross-check against the registry.
 *
 *         Why a handler? Without it, Foundry would call every external function
 *         with random arguments, which produces almost-only-reverts and proves
 *         very little. A handler shapes the fuzzing toward meaningful sequences
 *         (verify → create → donate → maybe pause) and records ghost variables.
 */
contract CommonLinkHandler is Test {
    CommonLinkRegistry public registry;

    // Actors (set once in constructor)
    address public immutable curator;
    address public immutable recorder;

    // Bounded sets so the search space stays tractable
    address[] public assos;
    bytes32[] public campaignIds;
    address[] public donors;

    // Ghost state
    /// @dev Sum of all successful donation amounts across the run.
    uint256 public ghost_totalDonated;
    /// @dev donor => running sum of successful donation amounts.
    mapping(address => uint256) public ghost_donorTotal;
    /// @dev campaignId => running sum of successful donation amounts.
    mapping(bytes32 => uint256) public ghost_campaignRaised;
    /// @dev donor => donationCount.
    mapping(address => uint256) public ghost_donorDonationCount;

    // Donation id counter so each call produces a unique id
    uint256 internal _donationCounter;

    // Campaign window — kept generous so warps within don't escape it
    uint32 internal startDate;
    uint32 internal endDate;

    constructor(
        CommonLinkRegistry _registry,
        address _curator,
        address _recorder
    ) {
        registry  = _registry;
        curator   = _curator;
        recorder  = _recorder;

        // 3 assos, 3 campaigns, 4 donors — modest but exercises uniqueCampaigns.
        for (uint160 i = 1; i <= 3; ++i) {
            address asso = address(uint160(0xA550_0000 + i));
            assos.push(asso);
        }
        campaignIds.push(bytes32(uint256(0xCA00_0001)));
        campaignIds.push(bytes32(uint256(0xCA00_0002)));
        campaignIds.push(bytes32(uint256(0xCA00_0003)));
        for (uint160 i = 1; i <= 4; ++i) {
            address donor = address(uint160(0xD0000 + i));
            donors.push(donor);
        }

        // Verify all assos upfront so the random flow can immediately donate.
        vm.startPrank(_curator);
        for (uint256 i; i < assos.length; ++i) {
            registry.verifyAssociation(assos[i], keccak256(abi.encode("siren", i)));
        }
        vm.stopPrank();

        // Window big enough to last through fuzz time-warps. We pin
        // startDate just ahead of the current timestamp so block.timestamp
        // starts inside [start, end].
        startDate = uint32(block.timestamp);
        endDate   = uint32(block.timestamp + 365 days);

        // Create one campaign per asso.
        vm.startPrank(_recorder);
        for (uint256 i; i < campaignIds.length; ++i) {
            registry.createCampaign(
                campaignIds[i],
                assos[i],
                10_000_000_00,                       // 10M € goal
                startDate,
                endDate,
                3,
                keccak256(abi.encode("budget", i))
            );
        }
        vm.stopPrank();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Random actions — all bounded, all from the recorder/curator
    // ─────────────────────────────────────────────────────────────────────

    /// @notice The bread and butter: record a donation that's expected to succeed.
    ///         Bounded so we don't drown in reverts.
    function donate(
        uint256 donorIdx,
        uint256 campaignIdx,
        uint96 amount
    ) external {
        donorIdx     = bound(donorIdx, 0, donors.length - 1);
        campaignIdx  = bound(campaignIdx, 0, campaignIds.length - 1);
        // Cap amounts so totals never overflow the ghost uint256.
        amount       = uint96(bound(uint256(amount), 1, 1_000_000_00));

        address donor       = donors[donorIdx];
        bytes32 campaignId  = campaignIds[campaignIdx];

        // Read the campaign state first so we skip when it isn't Active.
        CommonLinkRegistry.Campaign memory c = registry.getCampaign(campaignId);
        if (c.status != CommonLinkRegistry.CampaignStatus.Active) return;

        // Skip if the campaign's association has been revoked.
        if (!registry.getAssociation(c.association).verified) return;

        // Skip if we're outside the donation window (a warp() may have moved us).
        if (block.timestamp < c.startDate || block.timestamp > c.endDate) return;

        // Unique donation id
        bytes32 donationId = bytes32(uint256(0xDD00_0000) + ++_donationCounter);

        vm.prank(recorder);
        try registry.recordDonation(
            donationId,
            donor,
            campaignId,
            amount,
            keccak256(abi.encode("receipt", _donationCounter)),
            bytes32(_donationCounter) // txRef — non-zero, unique per call
        ) {
            // Update ghost only on success
            ghost_totalDonated                  += amount;
            ghost_donorTotal[donor]             += amount;
            ghost_campaignRaised[campaignId]    += amount;
            ghost_donorDonationCount[donor]     += 1;
        } catch {
            // Any unexpected revert is silently ignored; we want the
            // invariants to surface inconsistencies, not the call itself.
        }
    }

    /// @notice Occasionally pause/unpause a campaign to exercise the soft path.
    function togglePauseCampaign(uint256 campaignIdx) external {
        campaignIdx = bound(campaignIdx, 0, campaignIds.length - 1);
        bytes32 campaignId = campaignIds[campaignIdx];

        CommonLinkRegistry.Campaign memory c = registry.getCampaign(campaignId);
        vm.prank(curator);
        if (c.status == CommonLinkRegistry.CampaignStatus.Active) {
            try registry.pauseCampaign(campaignId) {} catch {}
        } else if (c.status == CommonLinkRegistry.CampaignStatus.Paused) {
            try registry.unpauseCampaign(campaignId) {} catch {}
        }
    }

    /// @notice Occasionally revoke/restore an association.
    function toggleAssociation(uint256 assoIdx) external {
        assoIdx = bound(assoIdx, 0, assos.length - 1);
        address asso = assos[assoIdx];

        CommonLinkRegistry.Association memory a = registry.getAssociation(asso);
        vm.prank(curator);
        if (a.verified) {
            try registry.revokeAssociation(asso) {} catch {}
        } else if (a.verifiedAt != 0) {
            try registry.restoreAssociation(asso) {} catch {}
        }
    }

    /// @notice Time-warp within the donation window. Keeps the test useful.
    function warpForward(uint32 by) external {
        by = uint32(bound(uint256(by), 0, 7 days));
        uint256 next = block.timestamp + by;
        if (next > endDate) next = endDate;
        vm.warp(next);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Read-only views for invariant assertions
    // ─────────────────────────────────────────────────────────────────────

    function assosLength()       external view returns (uint256) { return assos.length; }
    function campaignIdsLength() external view returns (uint256) { return campaignIds.length; }
    function donorsLength()      external view returns (uint256) { return donors.length; }
}

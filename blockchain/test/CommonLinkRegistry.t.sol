// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";

import {CommonLinkRegistry} from "src/CommonLinkRegistry.sol";
import {CommonLinkBaseTest} from "./helpers/CommonLinkBaseTest.sol";

/**
 * @title  CommonLinkRegistryTest
 * @notice Full unit-test suite for CommonLinkRegistry v1.2.0-mvp.
 *         Spec source of truth: commonlink-registry-spec-v1.2.md
 *
 *         Sections, in order:
 *           1. Constructor & roles
 *           2. verifyAssociation
 *           3. revokeAssociation
 *           4. restoreAssociation
 *           5. Association lifecycle composite scenarios
 *           6. createCampaign
 *           7. recordDonation
 *           8. Campaign status transitions
 *           9. markMilestoneReached
 *          10. Global pause / unpause
 *          11. View helpers
 *          12. Fuzz checks (single-function)
 *
 *         Invariants live in CommonLinkRegistry.invariant.t.sol.
 */
contract CommonLinkRegistryTest is CommonLinkBaseTest {

    // We re-declare events here so we can use `vm.expectEmit` with literal
    // event signatures. Keep in sync with the contract.
    event AssociationVerified(
        address indexed association,
        bytes32 indexed sirenHash,
        uint32 verifiedAt,
        address indexed by
    );
    event AssociationRevoked(
        address indexed association,
        bytes32 indexed sirenHash,
        uint32 revokedAt,
        address indexed by
    );
    event AssociationRestored(
        address indexed association,
        bytes32 indexed sirenHash,
        uint32 restoredAt,
        address indexed by
    );
    event CampaignCreated(
        address indexed association,
        bytes32 indexed campaignId,
        uint96 goal,
        uint32 startDate,
        uint32 endDate,
        uint8 milestoneCount,
        bytes32 budgetHash,
        address indexed attestedBy
    );
    event CampaignStatusChanged(
        bytes32 indexed campaignId,
        CommonLinkRegistry.CampaignStatus indexed oldStatus,
        CommonLinkRegistry.CampaignStatus indexed newStatus,
        address by
    );
    event DonationRecorded(
        address indexed donor,
        bytes32 indexed campaignId,
        address indexed association,
        bytes32 donationId,
        uint96 amount,
        bytes32 receiptHash,
        bytes32 txRef
    );
    event DonorFirstDonation(address indexed donor, uint32 timestamp);
    event DonorJoinedCampaign(address indexed donor, bytes32 indexed campaignId);
    event MilestoneReached(
        bytes32 indexed campaignId,
        uint8 indexed milestoneIndex,
        bytes32 proofHash,
        uint32 timestamp
    );

    // ═════════════════════════════════════════════════════════════════════
    // 1. Constructor & roles
    // ═════════════════════════════════════════════════════════════════════

    function test_Constructor_GrantsAllRoles() public view {
        assertTrue(registry.hasRole(registry.DEFAULT_ADMIN_ROLE(), admin), "admin role");
        assertTrue(registry.hasRole(registry.RECORDER_ROLE(), recorder),    "recorder role");
        assertTrue(registry.hasRole(registry.CURATOR_ROLE(), curator),      "curator role");
    }

    function test_Constructor_Version() public view {
        assertEq(registry.VERSION(), "1.2.0-mvp");
    }

    function test_Constructor_AllowsZeroRecorder() public {
        CommonLinkRegistry r = new CommonLinkRegistry(admin, address(0), curator);
        assertFalse(r.hasRole(r.RECORDER_ROLE(), address(0)));
    }

    function test_Constructor_AllowsZeroCurator() public {
        CommonLinkRegistry r = new CommonLinkRegistry(admin, recorder, address(0));
        assertFalse(r.hasRole(r.CURATOR_ROLE(), address(0)));
    }

    /// @dev In MVP, recorder and curator may share a wallet (spec §11).
    function test_Constructor_AllowsSameWalletForRecorderAndCurator() public {
        address hotWallet = makeAddr("hotWallet");
        CommonLinkRegistry r = new CommonLinkRegistry(admin, hotWallet, hotWallet);
        assertTrue(r.hasRole(r.RECORDER_ROLE(), hotWallet));
        assertTrue(r.hasRole(r.CURATOR_ROLE(), hotWallet));
    }

    function test_RevertWhen_ConstructorAdminIsZero() public {
        vm.expectRevert(CommonLinkRegistry.InvalidAdmin.selector);
        new CommonLinkRegistry(address(0), recorder, curator);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 2. verifyAssociation
    // ═════════════════════════════════════════════════════════════════════

    function test_VerifyAssociation_HappyPath() public {
        vm.expectEmit(true, true, true, true);
        emit AssociationVerified(asso1, SIREN_HASH_1, T0, curator);

        _verify(asso1, SIREN_HASH_1);

        CommonLinkRegistry.Association memory a = registry.getAssociation(asso1);
        assertTrue(a.verified,                "verified flag");
        assertEq(a.sirenHash, SIREN_HASH_1,   "sirenHash");
        assertEq(a.verifiedAt, uint32(T0),    "verifiedAt");
        assertEq(a.revokedAt, 0,              "revokedAt untouched on first verify");

        // Reverse index populated
        assertEq(registry.sirenHashToAssociation(SIREN_HASH_1), asso1);
    }

    function test_RevertWhen_VerifyAssociationWithZeroAddress() public {
        vm.prank(curator);
        vm.expectRevert(CommonLinkRegistry.InvalidAssociation.selector);
        registry.verifyAssociation(address(0), SIREN_HASH_1);
    }

    function test_RevertWhen_VerifyAssociationWithEmptySirenHash() public {
        vm.prank(curator);
        vm.expectRevert(CommonLinkRegistry.EmptySirenHash.selector);
        registry.verifyAssociation(asso1, bytes32(0));
    }

    function test_RevertWhen_VerifyAssociationTwice() public {
        _verify(asso1, SIREN_HASH_1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.AssociationAlreadyVerified.selector, asso1)
        );
        registry.verifyAssociation(asso1, SIREN_HASH_1);
    }

    function test_RevertWhen_VerifyAssociationWithSirenClaimedByAnotherAddress() public {
        _verify(asso1, SIREN_HASH_1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.SirenAlreadyRegistered.selector,
                SIREN_HASH_1,
                asso1
            )
        );
        registry.verifyAssociation(asso2, SIREN_HASH_1);
    }

    /// @dev After a revoke, you must use restoreAssociation — not re-verify
    ///      with a different sirenHash. Guards against SIREN swap on a wallet
    ///      that already had history.
    function test_RevertWhen_VerifyRevokedAssociationWithDifferentSiren() public {
        _verify(asso1, SIREN_HASH_1);
        _revoke(asso1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.SirenAlreadyRegistered.selector,
                SIREN_HASH_1,
                asso1
            )
        );
        registry.verifyAssociation(asso1, SIREN_HASH_2);
    }

    function test_RevertWhen_VerifyAssociationCalledByNonCurator() public {
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector,
                stranger,
                CURATOR_ROLE
            )
        );
        vm.prank(stranger);
        registry.verifyAssociation(asso1, SIREN_HASH_1);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 3. revokeAssociation
    // ═════════════════════════════════════════════════════════════════════

    function test_RevokeAssociation_HappyPath() public {
        _verify(asso1, SIREN_HASH_1);

        // Move forward so we can distinguish revokedAt from verifiedAt.
        vm.warp(T0 + 1 hours);

        vm.expectEmit(true, true, true, true);
        emit AssociationRevoked(asso1, SIREN_HASH_1, uint32(T0 + 1 hours), curator);

        _revoke(asso1);

        CommonLinkRegistry.Association memory a = registry.getAssociation(asso1);
        assertFalse(a.verified,                       "verified=false after revoke");
        assertEq(a.sirenHash, SIREN_HASH_1,           "sirenHash preserved");
        assertEq(a.verifiedAt, uint32(T0),            "verifiedAt preserved");
        assertEq(a.revokedAt, uint32(T0 + 1 hours),   "revokedAt set");

        // Reverse index is NOT cleared — spec keeps SIREN occupancy.
        assertEq(registry.sirenHashToAssociation(SIREN_HASH_1), asso1);
    }

    function test_RevertWhen_RevokeAssociationWithZeroAddress() public {
        vm.prank(curator);
        vm.expectRevert(CommonLinkRegistry.InvalidAssociation.selector);
        registry.revokeAssociation(address(0));
    }

    function test_RevertWhen_RevokeAssociationNeverVerified() public {
        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.AssociationNeverVerified.selector, asso1)
        );
        registry.revokeAssociation(asso1);
    }

    function test_RevertWhen_RevokeAssociationAlreadyRevoked() public {
        _verify(asso1, SIREN_HASH_1);
        _revoke(asso1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1)
        );
        registry.revokeAssociation(asso1);
    }

    function test_RevertWhen_RevokeAssociationCalledByNonCurator() public {
        _verify(asso1, SIREN_HASH_1);

        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector,
                stranger,
                CURATOR_ROLE
            )
        );
        vm.prank(stranger);
        registry.revokeAssociation(asso1);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 4. restoreAssociation
    // ═════════════════════════════════════════════════════════════════════

    function test_RestoreAssociation_HappyPath() public {
        _verify(asso1, SIREN_HASH_1);
        vm.warp(T0 + 1 hours);
        _revoke(asso1);

        // Move further so verifiedAt (restoredAt) ≠ revokedAt
        vm.warp(T0 + 2 hours);

        vm.expectEmit(true, true, true, true);
        emit AssociationRestored(asso1, SIREN_HASH_1, uint32(T0 + 2 hours), curator);

        _restore(asso1);

        CommonLinkRegistry.Association memory a = registry.getAssociation(asso1);
        assertTrue(a.verified,                          "verified=true after restore");
        assertEq(a.sirenHash, SIREN_HASH_1,             "sirenHash preserved");
        assertEq(a.verifiedAt, uint32(T0 + 2 hours),    "verifiedAt = restore time");
        // CRITICAL invariant: revokedAt is preserved for audit (spec §3.2)
        assertEq(a.revokedAt, uint32(T0 + 1 hours),     "revokedAt PRESERVED");
    }

    function test_RevertWhen_RestoreAssociationWithZeroAddress() public {
        vm.prank(curator);
        vm.expectRevert(CommonLinkRegistry.InvalidAssociation.selector);
        registry.restoreAssociation(address(0));
    }

    function test_RevertWhen_RestoreAssociationNeverVerified() public {
        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.AssociationNeverVerified.selector, asso1)
        );
        registry.restoreAssociation(asso1);
    }

    function test_RevertWhen_RestoreAssociationCurrentlyVerified() public {
        _verify(asso1, SIREN_HASH_1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.AssociationNotRevoked.selector, asso1)
        );
        registry.restoreAssociation(asso1);
    }

    function test_RevertWhen_RestoreAssociationCalledByNonCurator() public {
        _verify(asso1, SIREN_HASH_1);
        _revoke(asso1);

        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector,
                stranger,
                CURATOR_ROLE
            )
        );
        vm.prank(stranger);
        registry.restoreAssociation(asso1);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 5. Association lifecycle composite scenarios
    // ═════════════════════════════════════════════════════════════════════

    /// @dev verify → revoke → restore → revoke: state transitions are stable
    ///      and the audit trail is consistent throughout.
    function test_AssociationLifecycle_VerifyRevokeRestoreRevoke() public {
        // Step 1 — verify at T0
        _verify(asso1, SIREN_HASH_1);

        // Step 2 — revoke at T0+1h
        vm.warp(T0 + 1 hours);
        _revoke(asso1);
        CommonLinkRegistry.Association memory s2 = registry.getAssociation(asso1);
        assertFalse(s2.verified);
        assertEq(s2.revokedAt, uint32(T0 + 1 hours));

        // Step 3 — restore at T0+2h
        vm.warp(T0 + 2 hours);
        _restore(asso1);
        CommonLinkRegistry.Association memory s3 = registry.getAssociation(asso1);
        assertTrue(s3.verified);
        assertEq(s3.verifiedAt, uint32(T0 + 2 hours), "verifiedAt = restore");
        assertEq(s3.revokedAt, uint32(T0 + 1 hours),  "revokedAt preserved across restore");

        // Step 4 — revoke again at T0+3h
        vm.warp(T0 + 3 hours);
        _revoke(asso1);
        CommonLinkRegistry.Association memory s4 = registry.getAssociation(asso1);
        assertFalse(s4.verified);
        assertEq(s4.revokedAt, uint32(T0 + 3 hours),  "revokedAt updated on re-revoke");
        assertEq(s4.sirenHash, SIREN_HASH_1,          "sirenHash never changes");
    }

    function test_AssociationLifecycle_SirenHashImmutableAcrossCycle() public {
        _verify(asso1, SIREN_HASH_1);
        _revoke(asso1);
        _restore(asso1);
        assertEq(registry.getAssociation(asso1).sirenHash, SIREN_HASH_1);
        assertEq(registry.sirenHashToAssociation(SIREN_HASH_1), asso1);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 6. createCampaign
    // ═════════════════════════════════════════════════════════════════════

    function test_CreateCampaign_HappyPath() public {
        _verify(asso1, SIREN_HASH_1);

        uint96  goal       = 50_000_00;
        uint32  startDate  = T0 + 1;
        uint32  endDate    = T0 + 30 days;
        uint8   msCount    = 4;

        vm.expectEmit(true, true, true, true);
        emit CampaignCreated(
            asso1, CAMPAIGN_ID_1, goal, startDate, endDate, msCount, BUDGET_HASH, recorder
        );

        _createCampaign(CAMPAIGN_ID_1, asso1, goal, startDate, endDate, msCount, BUDGET_HASH);

        CommonLinkRegistry.Campaign memory c = registry.getCampaign(CAMPAIGN_ID_1);
        assertEq(c.association, asso1);
        assertEq(c.goal, goal);
        assertEq(c.raised, 0);
        assertEq(c.startDate, startDate);
        assertEq(c.endDate, endDate);
        assertEq(uint8(c.status), uint8(CommonLinkRegistry.CampaignStatus.Active));
        assertEq(c.milestoneCount, msCount);
        assertEq(c.budgetHash, BUDGET_HASH);
    }

    /// @dev Spec §7.2: milestoneCount may be 0.
    function test_CreateCampaign_ZeroMilestonesAllowed() public {
        _verify(asso1, SIREN_HASH_1);
        _createCampaign(CAMPAIGN_ID_1, asso1, 1, T0 + 1, T0 + 1 days, 0, BUDGET_HASH);
        assertEq(registry.getCampaign(CAMPAIGN_ID_1).milestoneCount, 0);
    }

    function test_RevertWhen_CreateCampaignWithEmptyId() public {
        _verify(asso1, SIREN_HASH_1);

        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptyCampaignId.selector);
        registry.createCampaign(bytes32(0), asso1, 1, T0 + 1, T0 + 1 days, 0, BUDGET_HASH);
    }

    function test_RevertWhen_CreateCampaignWithUnverifiedAssociation() public {
        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1)
        );
        registry.createCampaign(CAMPAIGN_ID_1, asso1, 1, T0 + 1, T0 + 1 days, 0, BUDGET_HASH);
    }

    function test_RevertWhen_CreateCampaignWithRevokedAssociation() public {
        _verify(asso1, SIREN_HASH_1);
        _revoke(asso1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1)
        );
        registry.createCampaign(CAMPAIGN_ID_1, asso1, 1, T0 + 1, T0 + 1 days, 0, BUDGET_HASH);
    }

    function test_RevertWhen_CreateCampaignDuplicateId() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.CampaignAlreadyExists.selector, CAMPAIGN_ID_1)
        );
        registry.createCampaign(
            CAMPAIGN_ID_1, asso1, 1, T0 + 1, T0 + 1 days, 0, BUDGET_HASH
        );
    }

    function test_RevertWhen_CreateCampaignWithZeroGoal() public {
        _verify(asso1, SIREN_HASH_1);

        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.InvalidGoal.selector);
        registry.createCampaign(CAMPAIGN_ID_1, asso1, 0, T0 + 1, T0 + 1 days, 0, BUDGET_HASH);
    }

    function test_RevertWhen_CreateCampaignWithEndBeforeStart() public {
        _verify(asso1, SIREN_HASH_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidDateRange.selector,
                uint32(T0 + 100),
                uint32(T0 + 50)
            )
        );
        registry.createCampaign(
            CAMPAIGN_ID_1, asso1, 1, T0 + 100, T0 + 50, 0, BUDGET_HASH
        );
    }

    function test_RevertWhen_CreateCampaignWithEndEqualsStart() public {
        _verify(asso1, SIREN_HASH_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidDateRange.selector,
                uint32(T0 + 100),
                uint32(T0 + 100)
            )
        );
        registry.createCampaign(
            CAMPAIGN_ID_1, asso1, 1, T0 + 100, T0 + 100, 0, BUDGET_HASH
        );
    }

    function test_RevertWhen_CreateCampaignWithEndInThePast() public {
        _verify(asso1, SIREN_HASH_1);

        // Past window relative to T0
        uint32 startDate = T0 - 1000;
        uint32 endDate   = T0 - 500;

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidDateRange.selector, startDate, endDate
            )
        );
        registry.createCampaign(
            CAMPAIGN_ID_1, asso1, 1, startDate, endDate, 0, BUDGET_HASH
        );
    }

    /// @dev endDate == block.timestamp must also revert per spec (`endDate > block.timestamp`).
    function test_RevertWhen_CreateCampaignWithEndEqualsNow() public {
        _verify(asso1, SIREN_HASH_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidDateRange.selector,
                uint32(T0 - 100),
                uint32(T0)
            )
        );
        registry.createCampaign(
            CAMPAIGN_ID_1, asso1, 1, T0 - 100, T0, 0, BUDGET_HASH
        );
    }

    function test_RevertWhen_CreateCampaignWithEmptyBudgetHash() public {
        _verify(asso1, SIREN_HASH_1);

        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptyBudgetHash.selector);
        registry.createCampaign(
            CAMPAIGN_ID_1, asso1, 1, T0 + 1, T0 + 1 days, 0, bytes32(0)
        );
    }

    function test_RevertWhen_CreateCampaignCalledByNonRecorder() public {
        _verify(asso1, SIREN_HASH_1);

        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector,
                stranger,
                RECORDER_ROLE
            )
        );
        vm.prank(stranger);
        registry.createCampaign(
            CAMPAIGN_ID_1, asso1, 1, T0 + 1, T0 + 1 days, 0, BUDGET_HASH
        );
    }

    function test_RevertWhen_CreateCampaignWhilePaused() public {
        _verify(asso1, SIREN_HASH_1);

        vm.prank(admin);
        registry.pause();

        vm.expectRevert(Pausable.EnforcedPause.selector);
        vm.prank(recorder);
        registry.createCampaign(
            CAMPAIGN_ID_1, asso1, 1, T0 + 1, T0 + 1 days, 0, BUDGET_HASH
        );
    }
}

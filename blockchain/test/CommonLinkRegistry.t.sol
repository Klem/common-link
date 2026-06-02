// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {CommonLinkBaseTest} from "./helpers/CommonLinkBaseTest.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";

/**
 * @title  CommonLinkRegistry test suite — campaign lifecycle
 * @notice Covers every external function involved in the campaign lifecycle:
 *           - createCampaign (initial state: Draft)
 *           - publishCampaign (Draft → Active)
 *           - revertCampaignToDraft (Active/Paused → Draft, raised == 0)
 *           - updateCampaignBudget (mutable until terminal)
 *           - pauseCampaign / unpauseCampaign
 *           - cancelCampaign / completeCampaign (terminal, stamp endDate)
 *
 *         One contract per logical sub-system. Foundry picks them all up
 *         automatically; this layout keeps each section focused on a single
 *         responsibility while staying in one file.
 *
 *         Donation-specific behaviour lives in `CommonLinkRegistry.donations.t.sol`.
 *         Invariants live in `CommonLinkRegistry.invariant.t.sol`.
 */


// ═════════════════════════════════════════════════════════════════════════
// Section 1 — createCampaign
// ═════════════════════════════════════════════════════════════════════════

contract CreateCampaignTest is CommonLinkBaseTest {
    bytes32 internal constant CAMP_1 = bytes32(uint256(1) << 128);

    function setUp() public override {
        super.setUp();
        _verifyAsso1();
    }

    function test_create_storesCampaignWithDraftStatus() public {
        uint32 expectedStart = uint32(block.timestamp);

        vm.prank(recorder);
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, 3, BUDGET_V1);

        CommonLinkRegistry.Campaign memory c = registry.getCampaign(CAMP_1);
        assertEq(c.association, asso1);
        assertEq(c.goal, DEFAULT_GOAL);
        assertEq(c.raised, 0);
        assertEq(c.startDate, expectedStart);
        assertEq(c.endDate, 0, "endDate must be 0 until terminal transition");
        assertEq(uint8(c.status), uint8(CommonLinkRegistry.CampaignStatus.Draft));
        assertEq(c.milestoneCount, 3);
        assertEq(c.budgetHash, BUDGET_V1);
    }

    function test_create_emitsCampaignCreatedEvent() public {
        uint32 expectedStart = uint32(block.timestamp);

        vm.expectEmit(true, true, true, true);
        emit CommonLinkRegistry.CampaignCreated(
            asso1, CAMP_1, DEFAULT_GOAL, expectedStart, 3, BUDGET_V1, recorder
        );

        vm.prank(recorder);
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, 3, BUDGET_V1);
    }

    function test_create_withZeroMilestoneCount() public {
        vm.prank(recorder);
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, 0, BUDGET_V1);
        assertEq(registry.getCampaign(CAMP_1).milestoneCount, 0);
    }

    function test_create_withMaxMilestoneCount() public {
        vm.prank(recorder);
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, type(uint8).max, BUDGET_V1);
        assertEq(registry.getCampaign(CAMP_1).milestoneCount, type(uint8).max);
    }

    function test_create_startDateIsBlockTimestamp() public {
        uint32 t = 1_900_000_000;
        vm.warp(t);

        vm.prank(recorder);
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, 0, BUDGET_V1);

        assertEq(registry.getCampaign(CAMP_1).startDate, t);
    }

    function test_create_revertsIfEmptyCampaignId() public {
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.EmptyCampaignId.selector));
        registry.createCampaign(bytes32(0), asso1, DEFAULT_GOAL, 0, BUDGET_V1);
    }

    function test_create_revertsIfAssoNotVerified() public {
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso2));
        registry.createCampaign(CAMP_1, asso2, DEFAULT_GOAL, 0, BUDGET_V1);
    }

    function test_create_revertsIfAssoRevoked() public {
        _revokeAsso(asso1);

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1));
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, 0, BUDGET_V1);
    }

    function test_create_revertsIfCampaignAlreadyExists() public {
        _createDraft(CAMP_1, asso1);

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.CampaignAlreadyExists.selector, CAMP_1));
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, 0, BUDGET_V1);
    }

    function test_create_revertsIfGoalIsZero() public {
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.InvalidGoal.selector));
        registry.createCampaign(CAMP_1, asso1, 0, 0, BUDGET_V1);
    }

    function test_create_revertsIfEmptyBudgetHash() public {
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.EmptyBudgetHash.selector));
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, 0, bytes32(0));
    }

    function test_create_revertsForNonRecorder() public {
        bytes32 role = registry.RECORDER_ROLE();
        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, attacker, role
            )
        );
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, 0, BUDGET_V1);
    }

    function test_create_revertsWhenPaused() public {
        vm.prank(admin);
        registry.pause();

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(Pausable.EnforcedPause.selector));
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, 0, BUDGET_V1);
    }
}


// ═════════════════════════════════════════════════════════════════════════
// Section 2 — publishCampaign
// ═════════════════════════════════════════════════════════════════════════

contract PublishCampaignTest is CommonLinkBaseTest {
    bytes32 internal constant CAMP_1 = bytes32(uint256(1) << 128);

    function setUp() public override {
        super.setUp();
        _verifyAsso1();
        _createDraft(CAMP_1, asso1);
    }

    function test_publish_movesDraftToActive() public {
        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);

        assertEq(
            uint8(registry.getCampaign(CAMP_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Active)
        );
    }

    function test_publish_preservesAllOtherFields() public {
        CommonLinkRegistry.Campaign memory before = registry.getCampaign(CAMP_1);

        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);

        CommonLinkRegistry.Campaign memory after_ = registry.getCampaign(CAMP_1);
        assertEq(after_.association, before.association);
        assertEq(after_.goal, before.goal);
        assertEq(after_.raised, 0);
        assertEq(after_.startDate, before.startDate);
        assertEq(after_.endDate, 0);
        assertEq(after_.milestoneCount, before.milestoneCount);
        assertEq(after_.budgetHash, before.budgetHash);
    }

    function test_publish_emitsStatusChangedEvent() public {
        vm.expectEmit(true, true, true, true);
        emit CommonLinkRegistry.CampaignStatusChanged(
            CAMP_1,
            CommonLinkRegistry.CampaignStatus.Draft,
            CommonLinkRegistry.CampaignStatus.Active,
            recorder
        );

        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);
    }

    function test_publish_revertsIfAlreadyActive() public {
        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Active,
                CommonLinkRegistry.CampaignStatus.Active
            )
        );
        registry.publishCampaign(CAMP_1);
    }

    function test_publish_revertsIfPaused() public {
        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);
        vm.prank(curator);
        registry.pauseCampaign(CAMP_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Paused,
                CommonLinkRegistry.CampaignStatus.Active
            )
        );
        registry.publishCampaign(CAMP_1);
    }

    function test_publish_revertsIfCompleted() public {
        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);
        vm.prank(curator);
        registry.completeCampaign(CAMP_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Completed,
                CommonLinkRegistry.CampaignStatus.Active
            )
        );
        registry.publishCampaign(CAMP_1);
    }

    function test_publish_revertsIfCancelled() public {
        vm.prank(curator);
        registry.cancelCampaign(CAMP_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Cancelled,
                CommonLinkRegistry.CampaignStatus.Active
            )
        );
        registry.publishCampaign(CAMP_1);
    }

    function test_publish_revertsIfAssoRevokedBetweenCreateAndPublish() public {
        _revokeAsso(asso1);

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1));
        registry.publishCampaign(CAMP_1);
    }

    function test_publish_succeedsAfterAssoRestored() public {
        _revokeAsso(asso1);
        vm.prank(curator);
        registry.restoreAssociation(asso1);

        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);

        assertEq(
            uint8(registry.getCampaign(CAMP_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Active)
        );
    }

    function test_publish_revertsIfCampaignDoesNotExist() public {
        bytes32 unknown = bytes32(uint256(99) << 128);

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.CampaignDoesNotExist.selector, unknown));
        registry.publishCampaign(unknown);
    }

    function test_publish_revertsForNonRecorder() public {
        bytes32 role = registry.RECORDER_ROLE();
        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, attacker, role
            )
        );
        registry.publishCampaign(CAMP_1);
    }

    function test_publish_revertsForCurator() public {
        bytes32 role = registry.RECORDER_ROLE();
        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, curator, role
            )
        );
        registry.publishCampaign(CAMP_1);
    }

    function test_publish_revertsWhenGloballyPaused() public {
        vm.prank(admin);
        registry.pause();

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(Pausable.EnforcedPause.selector));
        registry.publishCampaign(CAMP_1);
    }
}


// ═════════════════════════════════════════════════════════════════════════
// Section 3 — revertCampaignToDraft
// ═════════════════════════════════════════════════════════════════════════

contract RevertCampaignToDraftTest is CommonLinkBaseTest {
    bytes32 internal constant CAMP_1 = bytes32(uint256(1) << 128);
    bytes32 internal constant DON_1 = bytes32(((uint256(1) | (uint256(1) << 127)) << 128));

    function setUp() public override {
        super.setUp();
        _verifyAsso1();
    }

    function test_revertToDraft_fromActive() public {
        _createActive(CAMP_1, asso1);

        vm.prank(curator);
        registry.revertCampaignToDraft(CAMP_1);

        assertEq(
            uint8(registry.getCampaign(CAMP_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Draft)
        );
    }

    function test_revertToDraft_fromPaused() public {
        _createPaused(CAMP_1, asso1);

        vm.prank(curator);
        registry.revertCampaignToDraft(CAMP_1);

        assertEq(
            uint8(registry.getCampaign(CAMP_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Draft)
        );
    }

    function test_revertToDraft_preservesStartDate() public {
        _createActive(CAMP_1, asso1);
        uint32 originalStart = registry.getCampaign(CAMP_1).startDate;

        vm.warp(block.timestamp + 30 days);

        vm.prank(curator);
        registry.revertCampaignToDraft(CAMP_1);
        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);

        assertEq(
            registry.getCampaign(CAMP_1).startDate,
            originalStart,
            "startDate must be immutable across Draft/Active cycles"
        );
    }

    function test_revertToDraft_emitsStatusChangedEvent() public {
        _createActive(CAMP_1, asso1);

        vm.expectEmit(true, true, true, true);
        emit CommonLinkRegistry.CampaignStatusChanged(
            CAMP_1,
            CommonLinkRegistry.CampaignStatus.Active,
            CommonLinkRegistry.CampaignStatus.Draft,
            curator
        );

        vm.prank(curator);
        registry.revertCampaignToDraft(CAMP_1);
    }

    function test_revertToDraft_worksUnderGlobalPause() public {
        _createActive(CAMP_1, asso1);

        vm.prank(admin);
        registry.pause();

        // Curation tools must remain available during a global incident.
        vm.prank(curator);
        registry.revertCampaignToDraft(CAMP_1);

        assertEq(
            uint8(registry.getCampaign(CAMP_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Draft)
        );
    }

    function test_revertToDraft_revertsIfRaisedGreaterThanZero() public {
        _createActive(CAMP_1, asso1);
        _donate(DON_1, donor1, CAMP_1, 1000);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.CampaignHasDonations.selector, CAMP_1, uint96(1000))
        );
        registry.revertCampaignToDraft(CAMP_1);
    }

    function test_revertToDraft_revertsIfRaisedGreaterThanZeroOnPaused() public {
        _createActive(CAMP_1, asso1);
        _donate(DON_1, donor1, CAMP_1, 500);
        vm.prank(curator);
        registry.pauseCampaign(CAMP_1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.CampaignHasDonations.selector, CAMP_1, uint96(500))
        );
        registry.revertCampaignToDraft(CAMP_1);
    }

    function test_revertToDraft_revertsIfAlreadyDraft() public {
        _createDraft(CAMP_1, asso1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Draft,
                CommonLinkRegistry.CampaignStatus.Draft
            )
        );
        registry.revertCampaignToDraft(CAMP_1);
    }

    function test_revertToDraft_revertsIfCompleted() public {
        _createCompleted(CAMP_1, asso1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Completed,
                CommonLinkRegistry.CampaignStatus.Draft
            )
        );
        registry.revertCampaignToDraft(CAMP_1);
    }

    function test_revertToDraft_revertsIfCancelled() public {
        _createCancelled(CAMP_1, asso1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Cancelled,
                CommonLinkRegistry.CampaignStatus.Draft
            )
        );
        registry.revertCampaignToDraft(CAMP_1);
    }

    function test_revertToDraft_revertsIfAssoRevoked() public {
        _createActive(CAMP_1, asso1);
        _revokeAsso(asso1);

        vm.prank(curator);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1));
        registry.revertCampaignToDraft(CAMP_1);
    }

    function test_revertToDraft_revertsIfCampaignDoesNotExist() public {
        bytes32 unknown = bytes32(uint256(99) << 128);

        vm.prank(curator);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.CampaignDoesNotExist.selector, unknown));
        registry.revertCampaignToDraft(unknown);
    }

    function test_revertToDraft_revertsForNonCurator() public {
        _createActive(CAMP_1, asso1);

        bytes32 role = registry.RECORDER_ROLE();
        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.UnauthorizedRole.selector, role, attacker
            )
        );
        registry.revertCampaignToDraft(CAMP_1);
    }

    // E2 — recorder must now be allowed to revert a campaign to Draft on behalf
    // of its association; the curator path remains valid (covered above).
    function test_revertToDraft_allowedForRecorder() public {
        _createActive(CAMP_1, asso1);

        vm.prank(recorder);
        registry.revertCampaignToDraft(CAMP_1);

        assertEq(
            uint8(registry.getCampaign(CAMP_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Draft)
        );
    }
}


// ═════════════════════════════════════════════════════════════════════════
// Section 4 — updateCampaignBudget
// ═════════════════════════════════════════════════════════════════════════

contract UpdateCampaignBudgetTest is CommonLinkBaseTest {
    bytes32 internal constant CAMP_1 = bytes32(uint256(1) << 128);

    function setUp() public override {
        super.setUp();
        _verifyAsso1();
    }

    function test_update_inDraft() public {
        _createDraft(CAMP_1, asso1);

        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);

        assertEq(registry.getCampaign(CAMP_1).budgetHash, BUDGET_V2);
    }

    function test_update_inActive() public {
        _createActive(CAMP_1, asso1);

        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);

        assertEq(registry.getCampaign(CAMP_1).budgetHash, BUDGET_V2);
    }

    function test_update_inPaused() public {
        _createPaused(CAMP_1, asso1);

        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);

        assertEq(registry.getCampaign(CAMP_1).budgetHash, BUDGET_V2);
    }

    function test_update_multipleTimes() public {
        _createActive(CAMP_1, asso1);

        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V3);

        assertEq(registry.getCampaign(CAMP_1).budgetHash, BUDGET_V3);
    }

    function test_update_emitsBudgetUpdatedEvent() public {
        _createActive(CAMP_1, asso1);

        vm.expectEmit(true, true, true, true);
        emit CommonLinkRegistry.CampaignBudgetUpdated(
            CAMP_1, BUDGET_V1, BUDGET_V2, recorder, uint32(block.timestamp)
        );

        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
    }

    function test_update_preservesAllOtherFields() public {
        _createActive(CAMP_1, asso1);
        CommonLinkRegistry.Campaign memory before = registry.getCampaign(CAMP_1);

        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);

        CommonLinkRegistry.Campaign memory after_ = registry.getCampaign(CAMP_1);
        assertEq(after_.association, before.association);
        assertEq(after_.goal, before.goal);
        assertEq(after_.raised, before.raised);
        assertEq(after_.startDate, before.startDate);
        assertEq(after_.endDate, before.endDate);
        assertEq(uint8(after_.status), uint8(before.status));
        assertEq(after_.milestoneCount, before.milestoneCount);
    }

    function test_update_revertsWhenCompleted() public {
        _createCompleted(CAMP_1, asso1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignTerminal.selector, CAMP_1, CommonLinkRegistry.CampaignStatus.Completed
            )
        );
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
    }

    function test_update_revertsWhenCancelled() public {
        _createCancelled(CAMP_1, asso1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignTerminal.selector, CAMP_1, CommonLinkRegistry.CampaignStatus.Cancelled
            )
        );
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
    }

    function test_update_revertsWhenCancelledFromDraft() public {
        _createDraft(CAMP_1, asso1);
        vm.prank(curator);
        registry.cancelCampaign(CAMP_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignTerminal.selector, CAMP_1, CommonLinkRegistry.CampaignStatus.Cancelled
            )
        );
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
    }

    function test_update_revertsWhenAssoRevoked() public {
        _createActive(CAMP_1, asso1);
        _revokeAsso(asso1);

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1));
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
    }

    function test_update_revertsForDraftWhenAssoRevoked() public {
        _createDraft(CAMP_1, asso1);
        _revokeAsso(asso1);

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1));
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
    }

    function test_update_succeedsAfterAssoRestored() public {
        _createActive(CAMP_1, asso1);
        _revokeAsso(asso1);
        vm.prank(curator);
        registry.restoreAssociation(asso1);

        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);

        assertEq(registry.getCampaign(CAMP_1).budgetHash, BUDGET_V2);
    }

    function test_update_revertsOnEmptyBudgetHash() public {
        _createActive(CAMP_1, asso1);

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.EmptyBudgetHash.selector));
        registry.updateCampaignBudget(CAMP_1, bytes32(0));
    }

    function test_update_revertsOnUnchangedHash() public {
        _createActive(CAMP_1, asso1);

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.BudgetHashUnchanged.selector));
        registry.updateCampaignBudget(CAMP_1, BUDGET_V1);
    }

    function test_update_revertsIfCampaignDoesNotExist() public {
        bytes32 unknown = bytes32(uint256(99) << 128);

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.CampaignDoesNotExist.selector, unknown));
        registry.updateCampaignBudget(unknown, BUDGET_V2);
    }

    function test_update_revertsForNonRecorder() public {
        _createActive(CAMP_1, asso1);

        bytes32 role = registry.RECORDER_ROLE();
        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, attacker, role
            )
        );
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
    }

    function test_update_revertsForCurator() public {
        _createActive(CAMP_1, asso1);

        bytes32 role = registry.RECORDER_ROLE();
        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, curator, role
            )
        );
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
    }

    function test_update_revertsWhenGloballyPaused() public {
        _createActive(CAMP_1, asso1);
        vm.prank(admin);
        registry.pause();

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(Pausable.EnforcedPause.selector));
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
    }
}


// ═════════════════════════════════════════════════════════════════════════
// Section 5 — status transitions (pause / cancel / complete / unpause)
// ═════════════════════════════════════════════════════════════════════════

contract StatusTransitionsTest is CommonLinkBaseTest {
    bytes32 internal constant CAMP_1 = bytes32(uint256(1) << 128);

    function setUp() public override {
        super.setUp();
        _verifyAsso1();
    }

    function test_cancel_fromDraft() public {
        _createDraft(CAMP_1, asso1);

        vm.prank(curator);
        registry.cancelCampaign(CAMP_1);

        CommonLinkRegistry.Campaign memory c = registry.getCampaign(CAMP_1);
        assertEq(uint8(c.status), uint8(CommonLinkRegistry.CampaignStatus.Cancelled));
        assertEq(c.endDate, uint32(block.timestamp));
    }

    function test_cancel_fromDraft_emitsBothEvents() public {
        _createDraft(CAMP_1, asso1);

        vm.expectEmit(true, true, true, true);
        emit CommonLinkRegistry.CampaignStatusChanged(
            CAMP_1,
            CommonLinkRegistry.CampaignStatus.Draft,
            CommonLinkRegistry.CampaignStatus.Cancelled,
            curator
        );
        vm.expectEmit(true, true, true, true);
        emit CommonLinkRegistry.CampaignClosed(
            CAMP_1, CommonLinkRegistry.CampaignStatus.Cancelled, uint32(block.timestamp), curator
        );

        vm.prank(curator);
        registry.cancelCampaign(CAMP_1);
    }

    function test_complete_revertsFromDraft() public {
        _createDraft(CAMP_1, asso1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Draft,
                CommonLinkRegistry.CampaignStatus.Completed
            )
        );
        registry.completeCampaign(CAMP_1);
    }

    function test_complete_setsEndDate() public {
        _createActive(CAMP_1, asso1);
        uint32 originalStart = registry.getCampaign(CAMP_1).startDate;

        vm.warp(block.timestamp + 60 days);
        uint32 closeTime = uint32(block.timestamp);

        vm.prank(curator);
        registry.completeCampaign(CAMP_1);

        CommonLinkRegistry.Campaign memory c = registry.getCampaign(CAMP_1);
        assertEq(c.startDate, originalStart, "startDate must remain immutable");
        assertEq(c.endDate, closeTime);
        assertGt(c.endDate, c.startDate);
    }

    function test_cancel_setsEndDate() public {
        _createActive(CAMP_1, asso1);
        vm.warp(block.timestamp + 10 days);
        uint32 closeTime = uint32(block.timestamp);

        vm.prank(curator);
        registry.cancelCampaign(CAMP_1);

        assertEq(registry.getCampaign(CAMP_1).endDate, closeTime);
    }

    function test_complete_emitsCampaignClosedEvent() public {
        _createActive(CAMP_1, asso1);

        vm.expectEmit(true, true, true, true);
        emit CommonLinkRegistry.CampaignClosed(
            CAMP_1, CommonLinkRegistry.CampaignStatus.Completed, uint32(block.timestamp), curator
        );

        vm.prank(curator);
        registry.completeCampaign(CAMP_1);
    }

    function test_cancel_emitsCampaignClosedEvent() public {
        _createActive(CAMP_1, asso1);

        vm.expectEmit(true, true, true, true);
        emit CommonLinkRegistry.CampaignClosed(
            CAMP_1, CommonLinkRegistry.CampaignStatus.Cancelled, uint32(block.timestamp), curator
        );

        vm.prank(curator);
        registry.cancelCampaign(CAMP_1);
    }

    function test_nonTerminalTransitions_doNotSetEndDate() public {
        _createActive(CAMP_1, asso1);

        vm.prank(curator);
        registry.pauseCampaign(CAMP_1);
        assertEq(registry.getCampaign(CAMP_1).endDate, 0);

        vm.prank(curator);
        registry.unpauseCampaign(CAMP_1);
        assertEq(registry.getCampaign(CAMP_1).endDate, 0);
    }

    function test_unpause_revertsIfAssoRevokedDuringPause() public {
        _createPaused(CAMP_1, asso1);

        _revokeAsso(asso1);

        vm.prank(curator);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1));
        registry.unpauseCampaign(CAMP_1);
    }

    function test_unpause_succeedsIfAssoRestoredDuringPause() public {
        _createPaused(CAMP_1, asso1);

        _revokeAsso(asso1);
        vm.prank(curator);
        registry.restoreAssociation(asso1);

        vm.prank(curator);
        registry.unpauseCampaign(CAMP_1);

        assertEq(
            uint8(registry.getCampaign(CAMP_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Active)
        );
    }

    function test_pause_revertsFromDraft() public {
        _createDraft(CAMP_1, asso1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Draft,
                CommonLinkRegistry.CampaignStatus.Paused
            )
        );
        registry.pauseCampaign(CAMP_1);
    }

    function test_cancel_revertsFromCompleted() public {
        _createCompleted(CAMP_1, asso1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Completed,
                CommonLinkRegistry.CampaignStatus.Cancelled
            )
        );
        registry.cancelCampaign(CAMP_1);
    }

    function test_complete_revertsFromCancelled() public {
        _createCancelled(CAMP_1, asso1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Cancelled,
                CommonLinkRegistry.CampaignStatus.Completed
            )
        );
        registry.completeCampaign(CAMP_1);
    }
}


// ═════════════════════════════════════════════════════════════════════════
// Section 6 — end-to-end lifecycle scenarios
// ═════════════════════════════════════════════════════════════════════════

contract CampaignLifecycleTest is CommonLinkBaseTest {
    bytes32 internal constant CAMP_1 = bytes32(uint256(1) << 128);
    bytes32 internal constant DON_1 = bytes32(((uint256(1) | (uint256(1) << 127)) << 128));
    bytes32 internal constant DON_2 = bytes32(((uint256(2) | (uint256(1) << 127)) << 128));
    bytes32 internal constant DON_3 = bytes32(((uint256(3) | (uint256(1) << 127)) << 128));

    function setUp() public override {
        super.setUp();
        _verifyAsso1();
    }

    function test_lifecycle_fullHappyPath() public {
        // 1. Create in Draft.
        uint32 createdAt = uint32(block.timestamp);
        vm.prank(recorder);
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, 2, BUDGET_V1);

        assertEq(
            uint8(registry.getCampaign(CAMP_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Draft)
        );

        // 2. Edit the budget while still in Draft.
        vm.warp(block.timestamp + 1 days);
        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
        assertEq(registry.getCampaign(CAMP_1).budgetHash, BUDGET_V2);

        // 3. Publish.
        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);
        assertEq(
            uint8(registry.getCampaign(CAMP_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Active)
        );

        // 4. Two donations from different donors.
        vm.warp(block.timestamp + 3 days);
        _donate(DON_1, donor1, CAMP_1, 1000);
        _donate(DON_2, donor2, CAMP_1, 500);
        assertEq(registry.getCampaign(CAMP_1).raised, 1500);

        // 5. Update budget mid-campaign.
        vm.warp(block.timestamp + 1 days);
        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V3);

        // 6. Donation can still happen on the new budget.
        _donate(DON_3, donor1, CAMP_1, 250);
        assertEq(registry.getCampaign(CAMP_1).raised, 1750);

        // 7. Complete the campaign. endDate stamped, budgetHash frozen.
        vm.warp(block.timestamp + 30 days);
        uint32 closedAt = uint32(block.timestamp);
        vm.prank(curator);
        registry.completeCampaign(CAMP_1);

        CommonLinkRegistry.Campaign memory final_ = registry.getCampaign(CAMP_1);
        assertEq(uint8(final_.status), uint8(CommonLinkRegistry.CampaignStatus.Completed));
        assertEq(final_.startDate, createdAt);
        assertEq(final_.endDate, closedAt);
        assertEq(final_.budgetHash, BUDGET_V3);
        assertEq(final_.raised, 1750);

        // 8. Post-close: every write is rejected.
        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignTerminal.selector, CAMP_1, CommonLinkRegistry.CampaignStatus.Completed
            )
        );
        registry.updateCampaignBudget(CAMP_1, BUDGET_V1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotActive.selector, CAMP_1, CommonLinkRegistry.CampaignStatus.Completed
            )
        );
        registry.recordDonation(
            bytes32(uint256(99) << 128), donor1, CAMP_1, 100, RECEIPT_HASH_1, TX_REF_1
        );
    }

    function test_lifecycle_revertAndRepublishPreservesIdentity() public {
        vm.prank(recorder);
        registry.createCampaign(CAMP_1, asso1, DEFAULT_GOAL, 0, BUDGET_V1);
        uint32 originalStart = registry.getCampaign(CAMP_1).startDate;

        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);

        vm.warp(block.timestamp + 7 days);
        vm.prank(curator);
        registry.revertCampaignToDraft(CAMP_1);

        vm.warp(block.timestamp + 14 days);
        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);

        CommonLinkRegistry.Campaign memory c = registry.getCampaign(CAMP_1);
        assertEq(c.startDate, originalStart, "startDate must be the original createCampaign timestamp");
        assertEq(c.budgetHash, BUDGET_V2);
        assertEq(uint8(c.status), uint8(CommonLinkRegistry.CampaignStatus.Active));

        _donate(DON_1, donor1, CAMP_1, 300);
        assertEq(registry.getCampaign(CAMP_1).raised, 300);
    }

    function test_lifecycle_revokeDuringPauseBlocksResume() public {
        _createActive(CAMP_1, asso1);
        _donate(DON_1, donor1, CAMP_1, 100);

        vm.prank(curator);
        registry.pauseCampaign(CAMP_1);

        _revokeAsso(asso1);

        vm.prank(curator);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1));
        registry.unpauseCampaign(CAMP_1);

        // Cancel remains available — curators must always be able to wind down.
        vm.prank(curator);
        registry.cancelCampaign(CAMP_1);
        assertEq(
            uint8(registry.getCampaign(CAMP_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Cancelled)
        );
        assertGt(registry.getCampaign(CAMP_1).endDate, 0);
    }

    function test_lifecycle_publishRetryAfterCreate() public {
        // Simulates the backend creating successfully but losing track of
        // whether the publish landed. A retry of publishCampaign must succeed
        // exactly once and surface InvalidStatusTransition on the next retry,
        // letting the backend detect the prior success.
        _createDraft(CAMP_1, asso1);

        vm.prank(recorder);
        registry.publishCampaign(CAMP_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Active,
                CommonLinkRegistry.CampaignStatus.Active
            )
        );
        registry.publishCampaign(CAMP_1);
    }
}


// ═════════════════════════════════════════════════════════════════════════
// Section 7 — association SIREN-slot lifecycle (audit E5)
// ═════════════════════════════════════════════════════════════════════════

/// @notice Covers the reverse-index hygiene introduced by audit point E5:
///         `sirenHashToAssociation` must be freed on revocation so a Safe
///         migration (SIREN moved to a new wallet) becomes possible, and must
///         be re-established on restore unless another wallet has taken the
///         slot in the meantime.
contract AssociationSirenSlotTest is CommonLinkBaseTest {
    function test_revoke_freesSirenSlot() public {
        _verifyAsso1();
        assertEq(registry.sirenHashToAssociation(SIREN_HASH_1), asso1);

        _revokeAsso(asso1);

        assertEq(
            registry.sirenHashToAssociation(SIREN_HASH_1),
            address(0),
            "reverse index must be cleared on revoke so the SIREN can migrate"
        );
    }

    function test_revoke_thenVerifyDifferentWalletWithSameSiren_succeeds() public {
        _verifyAsso1();
        _revokeAsso(asso1);

        // The new Safe takes over the same SIREN. Without E5 this would revert
        // with SirenAlreadyRegistered.
        vm.prank(curator);
        registry.verifyAssociation(asso2, SIREN_HASH_1);

        assertEq(registry.sirenHashToAssociation(SIREN_HASH_1), asso2);
        CommonLinkRegistry.Association memory a2 = registry.getAssociation(asso2);
        assertTrue(a2.verified);
        assertEq(a2.sirenHash, SIREN_HASH_1);

        // The old wallet retains its history but is no longer verified.
        CommonLinkRegistry.Association memory a1 = registry.getAssociation(asso1);
        assertFalse(a1.verified);
        assertGt(a1.revokedAt, 0);
    }

    function test_restore_reEstablishesSirenSlotWhenFree() public {
        _verifyAsso1();
        _revokeAsso(asso1);

        vm.prank(curator);
        registry.restoreAssociation(asso1);

        assertEq(
            registry.sirenHashToAssociation(SIREN_HASH_1),
            asso1,
            "restore must re-establish the reverse index when the slot is free"
        );
        assertTrue(registry.getAssociation(asso1).verified);
    }

    function test_restore_revertsIfSirenClaimedByAnother() public {
        _verifyAsso1();
        _revokeAsso(asso1);

        // Migration: a different wallet claims the SIREN.
        vm.prank(curator);
        registry.verifyAssociation(asso2, SIREN_HASH_1);

        // The original wallet can no longer be restored on the same SIREN —
        // its restoration would silently overwrite the new holder.
        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.SirenAlreadyRegistered.selector, SIREN_HASH_1, asso2
            )
        );
        registry.restoreAssociation(asso1);

        // Reverse index untouched.
        assertEq(registry.sirenHashToAssociation(SIREN_HASH_1), asso2);
    }
}

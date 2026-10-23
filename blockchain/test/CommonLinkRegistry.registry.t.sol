// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {CommonLinkBaseTest} from "./helpers/CommonLinkBaseTest.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";

/// @notice Constructor, roles and pause.
contract DeploymentTest is CommonLinkBaseTest {
    function test_version() public view {
        assertEq(registry.VERSION(), "2.1.0-mvp");
    }

    function test_constructor_grantsRoles() public view {
        assertTrue(registry.hasRole(registry.DEFAULT_ADMIN_ROLE(), admin));
        assertTrue(registry.hasRole(registry.RECORDER_ROLE(), recorder));
        assertFalse(registry.hasRole(registry.DEFAULT_ADMIN_ROLE(), recorder));
    }

    function test_constructor_revertsOnZeroAdmin() public {
        vm.expectRevert(CommonLinkRegistry.InvalidAdmin.selector);
        new CommonLinkRegistry(address(0), recorder);
    }

    function test_constructor_zeroRecorderSkipsGrant() public {
        CommonLinkRegistry r = new CommonLinkRegistry(admin, address(0));
        assertFalse(r.hasRole(r.RECORDER_ROLE(), address(0)));
    }

    function test_pause_onlyAdmin() public {
        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, attacker, ADMIN_ROLE
            )
        );
        registry.pause();
    }

    function test_pause_freezesEveryWrite() public {
        _registerAsso1AndCamp1();
        vm.prank(admin);
        registry.pause();

        vm.startPrank(recorder);
        vm.expectRevert(Pausable.EnforcedPause.selector);
        registry.registerAssociation(asso2, SIREN_HASH_2);
        vm.expectRevert(Pausable.EnforcedPause.selector);
        registry.registerCampaign(CAMP_2, asso1, BUDGET_V1);
        vm.expectRevert(Pausable.EnforcedPause.selector);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
        vm.expectRevert(Pausable.EnforcedPause.selector);
        registry.recordDonation(_donationId(1), donor1, CAMP_1, 100, RECEIPT_HASH_1, TX_REF_1);
        vm.expectRevert(Pausable.EnforcedPause.selector);
        registry.recordPayout(_payoutId(1), CAMP_1, 100, PAYOUT_TX_REF_1);
        vm.expectRevert(Pausable.EnforcedPause.selector);
        registry.mintBadge(donor1, 1);
        vm.stopPrank();

        vm.prank(admin);
        registry.unpause();
        _donate(_donationId(1), donor1, CAMP_1, 100);
        assertEq(registry.getCampaign(CAMP_1).raised, 100);
    }
}

/// @notice Associations: registered once and forever.
contract RegisterAssociationTest is CommonLinkBaseTest {
    event AssociationRegistered(address indexed association, bytes32 indexed sirenHash, uint32 registeredAt);

    function test_register_storesAndEmits() public {
        vm.expectEmit(true, true, false, true, address(registry));
        emit AssociationRegistered(asso1, SIREN_HASH_1, uint32(block.timestamp));
        _registerAsso1();

        CommonLinkRegistry.Association memory a = registry.getAssociation(asso1);
        assertEq(a.sirenHash, SIREN_HASH_1);
        assertEq(a.registeredAt, uint32(block.timestamp));
        assertEq(registry.sirenHashToAssociation(SIREN_HASH_1), asso1);
    }

    function test_register_revertsIfAlreadyRegistered() public {
        _registerAsso1();
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.AssociationAlreadyRegistered.selector, asso1));
        registry.registerAssociation(asso1, SIREN_HASH_2);
    }

    function test_register_revertsIfSirenAlreadyUsed() public {
        _registerAsso1();
        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.SirenAlreadyRegistered.selector, SIREN_HASH_1, asso1)
        );
        registry.registerAssociation(asso2, SIREN_HASH_1);
    }

    function test_register_revertsOnZeroAddress() public {
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.InvalidAssociation.selector);
        registry.registerAssociation(address(0), SIREN_HASH_1);
    }

    function test_register_revertsOnEmptySiren() public {
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptySirenHash.selector);
        registry.registerAssociation(asso1, bytes32(0));
    }

    function test_register_revertsForNonRecorder() public {
        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, attacker, RECORDER_ROLE
            )
        );
        registry.registerAssociation(asso1, SIREN_HASH_1);
    }

    function test_register_adminCannotWrite() public {
        vm.prank(admin);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, admin, RECORDER_ROLE
            )
        );
        registry.registerAssociation(asso1, SIREN_HASH_1);
    }
}

/// @notice Campaigns: registered once and forever, with their budget hash.
contract RegisterCampaignTest is CommonLinkBaseTest {
    event CampaignRegistered(bytes32 indexed campaignId, address indexed association, bytes32 budgetHash, uint32 registeredAt);
    event CampaignBudgetUpdated(bytes32 indexed campaignId, bytes32 oldBudgetHash, bytes32 newBudgetHash, uint32 timestamp);

    function setUp() public override {
        super.setUp();
        _registerAsso1();
    }

    function test_register_storesAndEmits() public {
        vm.expectEmit(true, true, false, true, address(registry));
        emit CampaignRegistered(CAMP_1, asso1, BUDGET_V1, uint32(block.timestamp));
        _registerCampaign(CAMP_1, asso1);

        CommonLinkRegistry.Campaign memory c = registry.getCampaign(CAMP_1);
        assertEq(c.association, asso1);
        assertEq(c.raised, 0);
        assertEq(c.registeredAt, uint32(block.timestamp));
        assertEq(c.budgetHash, BUDGET_V1);
    }

    function test_register_revertsIfAlreadyRegistered() public {
        _registerCampaign(CAMP_1, asso1);
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.CampaignAlreadyRegistered.selector, CAMP_1));
        registry.registerCampaign(CAMP_1, asso1, BUDGET_V2);
    }

    function test_register_revertsIfAssociationNotRegistered() public {
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.AssociationNotRegistered.selector, asso2));
        registry.registerCampaign(CAMP_1, asso2, BUDGET_V1);
    }

    function test_register_revertsOnEmptyId() public {
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptyCampaignId.selector);
        registry.registerCampaign(bytes32(0), asso1, BUDGET_V1);
    }

    function test_register_revertsOnEmptyBudget() public {
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptyBudgetHash.selector);
        registry.registerCampaign(CAMP_1, asso1, bytes32(0));
    }

    function test_register_revertsForNonRecorder() public {
        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, attacker, RECORDER_ROLE
            )
        );
        registry.registerCampaign(CAMP_1, asso1, BUDGET_V1);
    }

    function test_updateBudget_recordsAndEmits() public {
        _registerCampaign(CAMP_1, asso1);
        vm.warp(block.timestamp + 1 days);

        vm.expectEmit(true, false, false, true, address(registry));
        emit CampaignBudgetUpdated(CAMP_1, BUDGET_V1, BUDGET_V2, uint32(block.timestamp));
        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);

        CommonLinkRegistry.Campaign memory c = registry.getCampaign(CAMP_1);
        assertEq(c.budgetHash, BUDGET_V2);
        assertEq(c.association, asso1, "registration is untouched");
    }

    /// @dev Re-declaring the same hash is harmless (retry-safe).
    function test_updateBudget_acceptsSameHash() public {
        _registerCampaign(CAMP_1, asso1);
        vm.prank(recorder);
        registry.updateCampaignBudget(CAMP_1, BUDGET_V1);
        assertEq(registry.getCampaign(CAMP_1).budgetHash, BUDGET_V1);
    }

    function test_updateBudget_revertsOnUnregisteredCampaign() public {
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.CampaignNotRegistered.selector, CAMP_1));
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
    }

    function test_updateBudget_revertsOnEmptyHash() public {
        _registerCampaign(CAMP_1, asso1);
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptyBudgetHash.selector);
        registry.updateCampaignBudget(CAMP_1, bytes32(0));
    }

    function test_updateBudget_revertsForNonRecorder() public {
        _registerCampaign(CAMP_1, asso1);
        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, attacker, RECORDER_ROLE
            )
        );
        registry.updateCampaignBudget(CAMP_1, BUDGET_V2);
    }
}

// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {CommonLinkBaseTest} from "./helpers/CommonLinkBaseTest.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {IAccessControlDefaultAdminRules} from
    "@openzeppelin/contracts/access/extensions/IAccessControlDefaultAdminRules.sol";

/// @notice Constructor and initial roles. Pause, governance, ERC-165 and funds: CommonLinkRegistry.governance.t.sol.
contract DeploymentTest is CommonLinkBaseTest {
    function test_version() public view {
        assertEq(registry.VERSION(), "3.1.0-mvp");
        assertEq(registry.READER_VERSION(), 1);
    }

    function test_constructor_grantsRoles() public view {
        assertEq(registry.defaultAdmin(), admin);
        assertEq(registry.defaultAdminDelay(), 0);
        assertTrue(registry.hasRole(ADMIN_ROLE, admin));
        assertTrue(registry.hasRole(RECORDER_ROLE, recorder));
        assertTrue(registry.hasRole(PAUSER_ROLE, pauser));
        assertTrue(registry.hasRole(UNPAUSER_ROLE, pauser));
        assertFalse(registry.hasRole(UNPAUSER_ROLE, recorder));
        assertEq(registry.getRoleAdmin(UNPAUSER_ROLE), ADMIN_ROLE);
        assertFalse(registry.hasRole(ADMIN_ROLE, recorder));
        assertFalse(registry.hasRole(ADMIN_ROLE, pauser));
        assertFalse(registry.hasRole(PAUSER_ROLE, recorder));
        assertFalse(registry.hasRole(RECORDER_ROLE, pauser));
    }

    function test_constructor_storesAdminDelay() public {
        CommonLinkRegistry r = new CommonLinkRegistry(3 days, admin, recorder, pauser);
        assertEq(r.defaultAdminDelay(), 3 days);
    }

    function test_constructor_revertsOnZeroAdmin() public {
        vm.expectRevert(
            abi.encodeWithSelector(IAccessControlDefaultAdminRules.AccessControlInvalidDefaultAdmin.selector, address(0))
        );
        new CommonLinkRegistry(0, address(0), recorder, pauser);
    }

    function test_constructor_revertsOnZeroRecorder() public {
        vm.expectRevert(CommonLinkRegistry.InvalidRecorder.selector);
        new CommonLinkRegistry(0, admin, address(0), pauser);
    }

    function test_constructor_revertsOnZeroPauser() public {
        vm.expectRevert(CommonLinkRegistry.InvalidPauser.selector);
        new CommonLinkRegistry(0, admin, recorder, address(0));
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

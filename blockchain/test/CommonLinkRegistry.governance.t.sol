// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {CommonLinkBaseTest} from "./helpers/CommonLinkBaseTest.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";
import {ICommonLinkRegistryReader} from "../src/interfaces/ICommonLinkRegistryReader.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {IAccessControlDefaultAdminRules} from
    "@openzeppelin/contracts/access/extensions/IAccessControlDefaultAdminRules.sol";
import {IERC165} from "@openzeppelin/contracts/utils/introspection/IERC165.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";
import {ERC721} from "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import {IERC721Errors, IERC1155Errors} from "@openzeppelin/contracts/interfaces/draft-IERC6093.sol";
import {ERC1155} from "@openzeppelin/contracts/token/ERC1155/ERC1155.sol";

/// @dev Minimal ERC-721 used to try a safe transfer into the registry.
contract TestNft is ERC721 {
    constructor() ERC721("Test", "TST") {}

    function mint(address to, uint256 id) external {
        _mint(to, id);
    }
}

/// @dev Minimal ERC-1155 used to try a safe transfer into the registry.
contract TestMultiToken is ERC1155 {
    constructor() ERC1155("") {}

    function mint(address to, uint256 id, uint256 amount) external {
        _mint(to, id, amount, "");
    }
}

/// @dev Forces its balance into `target` without running any of the target's code.
contract ForceSender {
    constructor(address payable target) payable {
        selfdestruct(target);
    }
}

/// @notice Pause (PAUSER_ROLE) and resume (admin only).
contract PauseTest is CommonLinkBaseTest {
    function test_pause_byPauser() public {
        vm.prank(pauser);
        registry.pause();
        assertTrue(registry.paused());
    }

    function test_pause_refusedForOtherRoles() public {
        address[3] memory others = [admin, recorder, attacker];
        for (uint256 i = 0; i < others.length; i++) {
            vm.prank(others[i]);
            vm.expectRevert(
                abi.encodeWithSelector(
                    IAccessControl.AccessControlUnauthorizedAccount.selector, others[i], PAUSER_ROLE
                )
            );
            registry.pause();
        }
    }

    function test_unpause_onlyAdmin() public {
        vm.prank(pauser);
        registry.pause();

        address[3] memory others = [pauser, recorder, attacker];
        for (uint256 i = 0; i < others.length; i++) {
            vm.prank(others[i]);
            vm.expectRevert(
                abi.encodeWithSelector(
                    IAccessControl.AccessControlUnauthorizedAccount.selector, others[i], ADMIN_ROLE
                )
            );
            registry.unpause();
        }

        vm.prank(admin);
        registry.unpause();
        assertFalse(registry.paused());
    }

    function test_pause_freezesEveryWrite() public {
        _registerAsso1AndCamp1();
        vm.prank(pauser);
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
        vm.stopPrank();

        // Reads stay available while paused.
        assertEq(registry.getCampaign(CAMP_1).association, asso1);

        vm.prank(admin);
        registry.unpause();
        _donate(_donationId(1), donor1, CAMP_1, 100);
        assertEq(registry.getCampaign(CAMP_1).raised, 100);
    }
}

/// @notice Admin governance: two-step delayed transfer, never renounceable, role rotation.
contract GovernanceTest is CommonLinkBaseTest {
    address internal safe = makeAddr("safe");
    uint48 internal constant DELAY = 1 hours;

    function setUp() public override {
        super.setUp();
        registry = new CommonLinkRegistry(DELAY, admin, recorder, pauser);
    }

    function test_grantAdminRole_refused() public {
        vm.prank(admin);
        vm.expectRevert(IAccessControlDefaultAdminRules.AccessControlEnforcedDefaultAdminRules.selector);
        registry.grantRole(ADMIN_ROLE, safe);
    }

    function test_revokeAdminRole_refused() public {
        vm.prank(admin);
        vm.expectRevert(IAccessControlDefaultAdminRules.AccessControlEnforcedDefaultAdminRules.selector);
        registry.revokeRole(ADMIN_ROLE, admin);
    }

    function test_renounceAdmin_refused() public {
        vm.prank(admin);
        vm.expectRevert(CommonLinkRegistry.AdminRenounceForbidden.selector);
        registry.renounceRole(ADMIN_ROLE, admin);
    }

    /// @dev OpenZeppelin's own renounce path (transfer to address(0), wait the delay) is closed too.
    function test_renounceAdmin_refusedEvenAfterScheduledTransferToZero() public {
        vm.prank(admin);
        registry.beginDefaultAdminTransfer(address(0));
        vm.warp(block.timestamp + DELAY + 1);

        vm.prank(admin);
        vm.expectRevert(CommonLinkRegistry.AdminRenounceForbidden.selector);
        registry.renounceRole(ADMIN_ROLE, admin);
        assertEq(registry.defaultAdmin(), admin);
    }

    function test_renounceOtherRole_allowed() public {
        vm.prank(pauser);
        registry.renounceRole(PAUSER_ROLE, pauser);
        assertFalse(registry.hasRole(PAUSER_ROLE, pauser));
    }

    function test_adminTransfer_twoStepsAfterDelay() public {
        vm.prank(admin);
        registry.beginDefaultAdminTransfer(safe);
        (address pending, uint48 schedule) = registry.pendingDefaultAdmin();
        assertEq(pending, safe);
        assertEq(schedule, uint48(block.timestamp) + DELAY);

        vm.prank(safe);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControlDefaultAdminRules.AccessControlEnforcedDefaultAdminDelay.selector, schedule
            )
        );
        registry.acceptDefaultAdminTransfer();

        vm.warp(uint256(schedule) + 1);
        vm.prank(safe);
        registry.acceptDefaultAdminTransfer();

        assertEq(registry.defaultAdmin(), safe);
        assertFalse(registry.hasRole(ADMIN_ROLE, admin));
    }

    function test_adminTransfer_onlyPendingAdminAccepts() public {
        vm.prank(admin);
        registry.beginDefaultAdminTransfer(safe);
        vm.warp(block.timestamp + DELAY + 1);

        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(IAccessControlDefaultAdminRules.AccessControlInvalidDefaultAdmin.selector, attacker)
        );
        registry.acceptDefaultAdminTransfer();
    }

    function test_adminTransfer_cancellable() public {
        vm.prank(admin);
        registry.beginDefaultAdminTransfer(safe);
        vm.prank(admin);
        registry.cancelDefaultAdminTransfer();
        vm.warp(block.timestamp + DELAY + 1);

        vm.prank(safe);
        vm.expectRevert(
            abi.encodeWithSelector(IAccessControlDefaultAdminRules.AccessControlInvalidDefaultAdmin.selector, safe)
        );
        registry.acceptDefaultAdminTransfer();
        assertEq(registry.defaultAdmin(), admin);
    }

    function test_adminTransfer_refusedForNonAdmin() public {
        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(IAccessControl.AccessControlUnauthorizedAccount.selector, recorder, ADMIN_ROLE)
        );
        registry.beginDefaultAdminTransfer(recorder);
    }

    /// @dev A compromised hot key is rotated by the admin, without redeploying.
    function test_rotateRecorder() public {
        address newRecorder = makeAddr("new-recorder");
        vm.startPrank(admin);
        registry.revokeRole(RECORDER_ROLE, recorder);
        registry.grantRole(RECORDER_ROLE, newRecorder);
        vm.stopPrank();

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(IAccessControl.AccessControlUnauthorizedAccount.selector, recorder, RECORDER_ROLE)
        );
        registry.registerAssociation(asso1, SIREN_HASH_1);

        vm.prank(newRecorder);
        registry.registerAssociation(asso1, SIREN_HASH_1);
        assertTrue(registry.getAssociation(asso1).registeredAt != 0);
    }
}

/// @notice ERC-165 and the shape of the read surface satellites depend on.
contract ReaderInterfaceTest is CommonLinkBaseTest {
    /// @dev The reader interfaceId, rebuilt from the getter signatures: any added, removed or retyped getter fails here.
    function test_readerInterfaceId_pinned() public pure {
        bytes4 expected = bytes4(keccak256("READER_VERSION()")) ^ bytes4(keccak256("getAssociation(address)"))
            ^ bytes4(keccak256("getCampaign(bytes32)")) ^ bytes4(keccak256("getDonation(bytes32)"))
            ^ bytes4(keccak256("getDonorStats(address)")) ^ bytes4(keccak256("getDonorCampaignTotal(address,bytes32)"))
            ^ bytes4(keccak256("getPayout(bytes32)")) ^ bytes4(keccak256("getCampaignStats(bytes32)"));
        assertEq(type(ICommonLinkRegistryReader).interfaceId, expected);
        // Same value pinned on the backend (ProdConfigSecurityTest): the startup probe checks this exact id.
        assertEq(type(ICommonLinkRegistryReader).interfaceId, bytes4(0x08708a80));
    }

    function test_readerVersion_pinned() public view {
        assertEq(registry.READER_VERSION(), 1, "bump READER_VERSION with any reader change, then update this test");
    }

    function test_supportsInterface() public view {
        assertTrue(registry.supportsInterface(type(ICommonLinkRegistryReader).interfaceId));
        assertTrue(registry.supportsInterface(type(IAccessControl).interfaceId));
        assertTrue(registry.supportsInterface(type(IAccessControlDefaultAdminRules).interfaceId));
        assertTrue(registry.supportsInterface(type(IERC165).interfaceId));
        assertFalse(registry.supportsInterface(0xffffffff));
        assertFalse(registry.supportsInterface(0x12345678));
    }

    /// @dev Struct shapes are invisible to the interfaceId: pin field order and types with known values. A change here
    ///      MUST come with a READER_VERSION bump.
    function test_structShapes_pinned() public {
        _registerAsso1AndCamp1();
        _donate(_donationId(1), donor1, CAMP_1, 150);
        vm.prank(recorder);
        registry.recordPayout(_payoutId(1), CAMP_1, 40, PAYOUT_TX_REF_1);
        uint32 t = uint32(block.timestamp);

        assertEq(abi.encode(registry.getAssociation(asso1)), abi.encode(SIREN_HASH_1, t));
        assertEq(abi.encode(registry.getCampaign(CAMP_1)), abi.encode(asso1, uint96(150), t, BUDGET_V1));
        assertEq(
            abi.encode(registry.getDonation(_donationId(1))),
            abi.encode(donor1, uint96(150), CAMP_1, t, RECEIPT_HASH_1, TX_REF_1)
        );
        assertEq(abi.encode(registry.getPayout(_payoutId(1))), abi.encode(CAMP_1, uint96(40), t, PAYOUT_TX_REF_1));
        assertEq(
            abi.encode(registry.getCampaignStats(CAMP_1)),
            abi.encode(uint96(40), uint32(1), uint32(1), uint32(1), t, t)
        );
        assertEq(abi.encode(registry.getDonorStats(donor1)), abi.encode(uint128(150), uint32(1), uint32(1), t));
        assertEq(registry.getDonorCampaignTotal(donor1, CAMP_1), 150);
    }
}

/// @notice Decision 41: the registry cannot receive funds, even by accident.
contract NoFundsTest is CommonLinkBaseTest {
    function setUp() public override {
        super.setUp();
        vm.deal(attacker, 10 ether);
    }

    function test_plainTransfer_reverts() public {
        vm.prank(attacker);
        (bool ok,) = address(registry).call{value: 1}("");
        assertFalse(ok, "receive() must not exist");
        assertEq(address(registry).balance, 0);
    }

    function test_unknownCalldataWithValue_reverts() public {
        vm.prank(attacker);
        (bool ok,) = address(registry).call{value: 1}(hex"deadbeef");
        assertFalse(ok, "fallback() must not exist");
    }

    /// @dev Every state-changing entry point is non-payable: sending value reverts even for the authorised role.
    function test_valueOnEveryWrite_reverts() public {
        _registerAsso1AndCamp1();
        vm.deal(recorder, 1 ether);
        vm.deal(pauser, 1 ether);
        vm.deal(admin, 1 ether);

        _assertRejectsValue(recorder, abi.encodeCall(registry.registerAssociation, (asso2, SIREN_HASH_2)));
        _assertRejectsValue(recorder, abi.encodeCall(registry.registerCampaign, (CAMP_2, asso1, BUDGET_V1)));
        _assertRejectsValue(recorder, abi.encodeCall(registry.updateCampaignBudget, (CAMP_1, BUDGET_V2)));
        _assertRejectsValue(
            recorder,
            abi.encodeCall(registry.recordDonation, (_donationId(1), donor1, CAMP_1, 100, RECEIPT_HASH_1, TX_REF_1))
        );
        _assertRejectsValue(
            recorder, abi.encodeCall(registry.recordPayout, (_payoutId(1), CAMP_1, 100, PAYOUT_TX_REF_1))
        );
        _assertRejectsValue(pauser, abi.encodeCall(registry.pause, ()));
        _assertRejectsValue(admin, abi.encodeCall(registry.unpause, ()));
        _assertRejectsValue(admin, abi.encodeCall(registry.grantRole, (RECORDER_ROLE, donor1)));
        _assertRejectsValue(admin, abi.encodeCall(registry.beginDefaultAdminTransfer, (donor1)));
        _assertRejectsValue(recorder, abi.encodeCall(registry.renounceRole, (RECORDER_ROLE, recorder)));
        assertEq(address(registry).balance, 0);
    }

    function test_deployWithValue_reverts() public {
        bytes memory code = abi.encodePacked(
            type(CommonLinkRegistry).creationCode, abi.encode(uint48(0), admin, recorder, pauser)
        );
        address deployed;
        vm.deal(address(this), 1 ether);
        assembly {
            deployed := create(1, add(code, 0x20), mload(code))
        }
        assertEq(deployed, address(0), "the constructor must be non-payable");
    }

    function test_erc721SafeTransfer_reverts() public {
        TestNft nft = new TestNft();
        nft.mint(attacker, 1);
        vm.prank(attacker);
        vm.expectRevert(abi.encodeWithSelector(IERC721Errors.ERC721InvalidReceiver.selector, address(registry)));
        nft.safeTransferFrom(attacker, address(registry), 1);
    }

    function test_erc1155SafeTransfer_reverts() public {
        TestMultiToken token = new TestMultiToken();
        token.mint(attacker, 1, 10);
        vm.prank(attacker);
        vm.expectRevert(abi.encodeWithSelector(IERC1155Errors.ERC1155InvalidReceiver.selector, address(registry)));
        token.safeTransferFrom(attacker, address(registry), 1, 10, "");
    }

    /// @dev Code cannot stop a forced balance (SELFDESTRUCT still moves it after Cancun): it stays locked and changes
    ///      no record, no read and no write.
    function test_forcedBalance_isInert() public {
        new ForceSender{value: 1 ether}(payable(address(registry)));
        assertEq(address(registry).balance, 1 ether);

        _registerAsso1AndCamp1();
        _donate(_donationId(1), donor1, CAMP_1, 100);
        assertEq(registry.getCampaign(CAMP_1).raised, 100);
        assertEq(registry.getDonorStats(donor1).totalDonated, 100);
        assertEq(address(registry).balance, 1 ether, "no path moves the forced balance");
    }

    function _assertRejectsValue(address caller, bytes memory data) internal {
        vm.prank(caller);
        (bool ok,) = address(registry).call{value: 1}(data);
        assertFalse(ok, "a payable entry point exists");
    }
}

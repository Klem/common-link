// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {CommonLinkBaseTest} from "./helpers/CommonLinkBaseTest.sol";
import {CommonLinkRegistry} from "../src/CommonLinkRegistry.sol";
import {MockDonorBadge} from "./mocks/MockDonorBadge.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

/// @notice Badge mints relayed by the registry (the registry is the badge contract's single minter).
contract BadgeTest is CommonLinkBaseTest {
    event BadgeContractSet(address indexed badgeContract);
    event BadgeMinted(address indexed donor, uint16 indexed badgeType, uint256 tokenId);

    MockDonorBadge internal badge;

    function setUp() public override {
        super.setUp();
        badge = new MockDonorBadge(address(registry));
        _registerAsso1AndCamp1();
        _donate(_donationId(1), donor1, CAMP_1, 1000);
    }

    function _setBadge() internal {
        vm.prank(admin);
        registry.setBadgeContract(address(badge));
    }

    // ── setBadgeContract (write-once) ─────────────────────────────────────

    function test_setBadge_setsAndEmits() public {
        vm.expectEmit(true, false, false, false, address(registry));
        emit BadgeContractSet(address(badge));
        _setBadge();
        assertEq(address(registry.badgeContract()), address(badge));
    }

    function test_setBadge_isWriteOnce() public {
        _setBadge();
        MockDonorBadge other = new MockDonorBadge(address(registry));
        vm.prank(admin);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.BadgeContractAlreadySet.selector, address(badge)));
        registry.setBadgeContract(address(other));
    }

    function test_setBadge_revertsOnZeroOrEoa() public {
        vm.startPrank(admin);
        vm.expectRevert(CommonLinkRegistry.InvalidBadgeContract.selector);
        registry.setBadgeContract(address(0));
        vm.expectRevert(CommonLinkRegistry.InvalidBadgeContract.selector);
        registry.setBadgeContract(donor2);
        vm.stopPrank();
    }

    function test_setBadge_onlyAdmin() public {
        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, recorder, ADMIN_ROLE
            )
        );
        registry.setBadgeContract(address(badge));
    }

    // ── mintBadge ─────────────────────────────────────────────────────────

    function test_mint_relaysAndEmits() public {
        _setBadge();
        uint256 expectedId = uint256(keccak256(abi.encode(donor1, uint16(1))));

        vm.expectEmit(true, true, false, true, address(registry));
        emit BadgeMinted(donor1, 1, expectedId);
        vm.prank(recorder);
        uint256 tokenId = registry.mintBadge(donor1, 1);

        assertEq(tokenId, expectedId);
        assertTrue(badge.hasBadge(donor1, 1));
        assertEq(badge.mintCount(), 1);
    }

    function test_mint_revertsWhenBadgeNotSet() public {
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.BadgeContractNotSet.selector);
        registry.mintBadge(donor1, 1);
    }

    function test_mint_revertsForUnknownDonor() public {
        _setBadge();
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(CommonLinkRegistry.DonorUnknown.selector, donor2));
        registry.mintBadge(donor2, 1);
    }

    function test_mint_propagatesBadgeRevertOnDuplicate() public {
        _setBadge();
        vm.prank(recorder);
        registry.mintBadge(donor1, 1);

        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(MockDonorBadge.AlreadyMinted.selector, donor1, uint16(1)));
        registry.mintBadge(donor1, 1);
    }

    function test_mint_propagatesUndefinedType() public {
        _setBadge();
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(MockDonorBadge.UndefinedBadgeType.selector, uint16(9)));
        registry.mintBadge(donor1, 9);
    }

    function test_mint_revertsForNonRecorder() public {
        _setBadge();
        vm.prank(attacker);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, attacker, RECORDER_ROLE
            )
        );
        registry.mintBadge(donor1, 1);
    }

    function test_badgeRejectsDirectMintFromAnyoneButRegistry() public {
        vm.prank(recorder);
        vm.expectRevert(abi.encodeWithSelector(MockDonorBadge.NotMinter.selector, recorder));
        badge.mint(donor1, 1);
    }

    /// @dev A malicious or buggy badge contract re-entering the registry is stopped by nonReentrant.
    function test_mint_isNonReentrant() public {
        _setBadge();
        badge.setReentry(address(registry), abi.encodeCall(CommonLinkRegistry.mintBadge, (donor1, 2)));
        // The registry holds no RECORDER_ROLE, but the reentrancy guard trips first.
        vm.prank(recorder);
        vm.expectRevert(ReentrancyGuard.ReentrancyGuardReentrantCall.selector);
        registry.mintBadge(donor1, 1);
    }
}

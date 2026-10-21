// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {IDonorBadge} from "../../src/interfaces/IDonorBadge.sol";

/**
 * @title  MockDonorBadge
 * @notice Test double honouring the frozen IDonorBadge semantics: single minter, one badge per
 *         (address, type), reverts on undefined type. Can be told to re-enter the caller to test
 *         the registry's reentrancy guard.
 */
contract MockDonorBadge is IDonorBadge {
    error NotMinter(address caller);
    error AlreadyMinted(address to, uint16 badgeType);
    error UndefinedBadgeType(uint16 badgeType);

    address public immutable minter;
    mapping(uint16 => bool) public defined;
    mapping(address => mapping(uint16 => bool)) public hasBadge;
    uint256 public mintCount;

    /// @dev When set, `mint` calls back this target with `reentryPayload` (reentrancy test).
    address public reentryTarget;
    bytes public reentryPayload;

    constructor(address minter_) {
        minter = minter_;
        defined[1] = true;
        defined[2] = true;
    }

    function setReentry(address target, bytes calldata payload) external {
        reentryTarget = target;
        reentryPayload = payload;
    }

    /// @inheritdoc IDonorBadge
    function mint(address to, uint16 badgeType) external returns (uint256 tokenId) {
        if (msg.sender != minter) revert NotMinter(msg.sender);
        if (!defined[badgeType]) revert UndefinedBadgeType(badgeType);
        if (hasBadge[to][badgeType]) revert AlreadyMinted(to, badgeType);

        hasBadge[to][badgeType] = true;
        mintCount += 1;
        tokenId = uint256(keccak256(abi.encode(to, badgeType)));

        if (reentryTarget != address(0)) {
            (bool ok, bytes memory ret) = reentryTarget.call(reentryPayload);
            if (!ok) {
                assembly {
                    revert(add(ret, 32), mload(ret))
                }
            }
        }
    }
}

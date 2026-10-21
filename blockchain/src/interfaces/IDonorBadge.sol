// SPDX-License-Identifier: MIT
pragma solidity ^0.8.30;

/**
 * @title  IDonorBadge
 * @notice Mint entry point the CommonLinkRegistry calls on the soulbound donor-badge contract.
 * @dev    FROZEN INTERFACE. The registry is not upgradeable and stores the badge contract address
 *         once (`setBadgeContract`), so any badge implementation (sprint B5) MUST expose exactly
 *         this function with these semantics:
 *           - callable only by the registry (the registry is the single minter);
 *           - mints one soulbound token for `(to, badgeType)` and returns its id;
 *           - reverts if that `(to, badgeType)` pair already holds a badge (one badge per type
 *             per address) or if `badgeType` is not defined.
 */
interface IDonorBadge {
    /**
     * @notice Mints the badge `badgeType` to `to`.
     * @param  to        Donor pseudonymous address (no private key exists for it).
     * @param  badgeType Badge type identifier, defined beforehand on the badge contract.
     * @return tokenId   Id of the minted token.
     */
    function mint(address to, uint16 badgeType) external returns (uint256 tokenId);
}

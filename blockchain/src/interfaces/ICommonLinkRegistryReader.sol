// SPDX-License-Identifier: MIT
pragma solidity ^0.8.30;

/**
 * @title  ICommonLinkRegistryReader
 * @notice Read surface of the CommonLink proof registry (the hub). Satellite contracts read the hub only through this
 *         interface and never import the hub itself.
 * @dev    COMPATIBILITY: the hub advertises `type(ICommonLinkRegistryReader).interfaceId` via ERC-165. An interfaceId
 *         is the XOR of the function selectors, which only cover parameter types: it does NOT change when a struct
 *         below gains, loses or reorders a field. `READER_VERSION` is the marker that does: bump it on ANY change to
 *         this file (struct shape, getter, semantics). Satellites check both at deployment (see RegistryAware).
 *         Every struct holds static fields only, so its ABI encoding is the flat concatenation of its fields.
 */
interface ICommonLinkRegistryReader {
    /// @notice A registered association. Keyed by its pseudonymous address.
    /// @dev    `registeredAt == 0` means "never registered".
    struct Association {
        bytes32 sirenHash; // keccak256 of the canonical SIREN (or RNA identifier when there is no SIREN)
        uint32 registeredAt;
    }

    /// @notice A registered campaign. The `campaignId` (UUID encoded as bytes32) is the mapping key.
    /// @dev    `association == address(0)` means "never registered".
    struct Campaign {
        address association;
        uint96 raised; // EUR cents, sum of the recorded donations
        uint32 registeredAt;
        bytes32 budgetHash; // last declared budget hash (history in CampaignBudgetUpdated events)
    }

    /// @notice A recorded donation. The `donationId` (UUID encoded as bytes32) is the mapping key.
    /// @dev    `donor == address(0)` means "never recorded".
    struct Donation {
        address donor;
        uint96 amount; // EUR cents
        bytes32 campaignId;
        uint32 timestamp;
        bytes32 receiptHash; // hash of the fiscal receipt
        bytes32 txRef; // payment provider reference encoded as bytes32
    }

    /// @notice A settled payout. The `payoutId` (UUID encoded as bytes32) is the mapping key.
    /// @dev    `campaignId == bytes32(0)` means "never recorded".
    struct Payout {
        bytes32 campaignId;
        uint96 amount; // EUR cents
        uint32 timestamp;
        bytes32 txRef; // bank transfer reference (payment provider transaction id) encoded as bytes32
    }

    /// @notice Cumulative campaign statistics, packed into a single storage slot. The total raised stays in
    ///         `Campaign.raised`.
    struct CampaignStats {
        uint96 totalPaidOut; // EUR cents, sum of the recorded payouts
        uint32 payoutCount;
        uint32 donationCount;
        uint32 uniqueDonors; // distinct donor addresses with at least one recorded donation
        uint32 lastDonationAt;
        uint32 lastPayoutAt;
    }

    /// @notice Cumulative donor statistics, packed into a single storage slot.
    /// @dev    `firstDonationAt == 0` means "no recorded donation".
    struct DonorStats {
        uint128 totalDonated; // EUR cents
        uint32 donationCount;
        uint32 uniqueCampaigns;
        uint32 firstDonationAt;
    }

    /// @notice Version of this read surface. Bumped on any change to this interface, struct shapes included.
    function READER_VERSION() external view returns (uint16);

    /// @notice The association registered at `association` (zeroed struct if never registered).
    function getAssociation(address association) external view returns (Association memory);

    /// @notice The campaign registered under `campaignId` (zeroed struct if never registered).
    function getCampaign(bytes32 campaignId) external view returns (Campaign memory);

    /// @notice The donation recorded under `donationId` (zeroed struct if never recorded).
    function getDonation(bytes32 donationId) external view returns (Donation memory);

    /// @notice Cumulative statistics of `donor` (zeroed struct if the donor never donated).
    function getDonorStats(address donor) external view returns (DonorStats memory);

    /// @notice Total donated by `donor` to `campaignId`, in EUR cents.
    function getDonorCampaignTotal(address donor, bytes32 campaignId) external view returns (uint128);

    /// @notice The payout recorded under `payoutId` (zeroed struct if never recorded).
    function getPayout(bytes32 payoutId) external view returns (Payout memory);

    /// @notice Cumulative statistics of `campaignId` (zeroed struct if nothing was recorded).
    function getCampaignStats(bytes32 campaignId) external view returns (CampaignStats memory);
}

// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {Test} from "forge-std/Test.sol";
import {Vm} from "forge-std/Vm.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";

import {CommonLinkRegistry} from "src/CommonLinkRegistry.sol";
import {CommonLinkBaseTest} from "./helpers/CommonLinkBaseTest.sol";

/**
 * @title  CommonLinkRegistryDonationsTest
 * @notice Donation + status + milestone + pause + view tests for
 *         CommonLinkRegistry v1.2.0-mvp. Split from CommonLinkRegistryTest
 *         purely for file size; identical setup conventions.
 */
contract CommonLinkRegistryDonationsTest is CommonLinkBaseTest {

    // Re-declared for vm.expectEmit
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
    event CampaignStatusChanged(
        bytes32 indexed campaignId,
        CommonLinkRegistry.CampaignStatus indexed oldStatus,
        CommonLinkRegistry.CampaignStatus indexed newStatus,
        address by
    );
    event MilestoneReached(
        bytes32 indexed campaignId,
        uint8 indexed milestoneIndex,
        bytes32 proofHash,
        uint32 timestamp
    );

    // ═════════════════════════════════════════════════════════════════════
    // 7. recordDonation — happy paths
    // ═════════════════════════════════════════════════════════════════════

    function test_RecordDonation_FirstEverDonation_EmitsAllThreeEvents() public {
        _setupReadyToDonate();
        uint32 nowTs = uint32(block.timestamp);

        // Expected order: DonationRecorded, DonorFirstDonation, DonorJoinedCampaign
        vm.expectEmit(true, true, true, true);
        emit DonationRecorded(
            donor1, CAMPAIGN_ID_1, asso1, DONATION_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
        vm.expectEmit(true, false, false, true);
        emit DonorFirstDonation(donor1, nowTs);
        vm.expectEmit(true, true, false, false);
        emit DonorJoinedCampaign(donor1, CAMPAIGN_ID_1);

        _recordDonation(DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00);
    }

    function test_RecordDonation_PersistsDonationEntry() public {
        _setupReadyToDonate();
        uint32 nowTs = uint32(block.timestamp);

        _recordDonation(DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00);

        CommonLinkRegistry.Donation memory d = registry.getDonation(DONATION_ID_1);
        assertEq(d.donor, donor1);
        assertEq(d.amount, 100_00);
        assertEq(d.campaignId, CAMPAIGN_ID_1);
        assertEq(d.timestamp, nowTs);
        assertEq(d.receiptHash, RECEIPT_HASH_1);
        assertEq(d.txRef, TX_REF_1);
    }

    function test_RecordDonation_UpdatesCampaignRaised() public {
        _setupReadyToDonate();
        _recordDonation(DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00);
        _recordDonation(DONATION_ID_2, donor2, CAMPAIGN_ID_1, 250_00);

        assertEq(registry.getCampaign(CAMPAIGN_ID_1).raised, 350_00);
    }

    function test_RecordDonation_UpdatesDonorStatsOnFirstDonation() public {
        _setupReadyToDonate();
        uint32 nowTs = uint32(block.timestamp);

        _recordDonation(DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00);

        CommonLinkRegistry.DonorStats memory s = registry.getDonorStats(donor1);
        assertEq(s.totalDonated, 100_00);
        assertEq(s.donationCount, 1);
        assertEq(s.uniqueCampaigns, 1);
        assertEq(s.firstDonationAt, nowTs);

        assertEq(registry.getDonorCampaignTotal(donor1, CAMPAIGN_ID_1), 100_00);
    }

    function test_RecordDonation_SecondDonationSameCampaign_NoFirstEvents() public {
        _setupReadyToDonate();
        uint32 firstTs = uint32(block.timestamp);
        _recordDonation(DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00);

        // Move time forward; firstDonationAt must not change.
        vm.warp(block.timestamp + 1 hours);

        // Only DonationRecorded should fire. Use expectEmit selectively — we
        // can't easily "expect zero emits"; instead we record logs and assert.
        vm.recordLogs();
        _recordDonation(DONATION_ID_2, donor1, CAMPAIGN_ID_1, 50_00);
        Vm.Log[] memory logs = vm.getRecordedLogs();

        // Exactly one event: DonationRecorded
        assertEq(logs.length, 1, "only DonationRecorded should be emitted");
        assertEq(
            logs[0].topics[0],
            keccak256("DonationRecorded(address,bytes32,address,bytes32,uint96,bytes32,bytes32)")
        );

        // Stats: count++, total+=, uniqueCampaigns unchanged, firstDonationAt unchanged
        CommonLinkRegistry.DonorStats memory s = registry.getDonorStats(donor1);
        assertEq(s.totalDonated, 150_00);
        assertEq(s.donationCount, 2);
        assertEq(s.uniqueCampaigns, 1,             "uniqueCampaigns unchanged");
        assertEq(s.firstDonationAt, firstTs,       "firstDonationAt unchanged");

        assertEq(registry.getDonorCampaignTotal(donor1, CAMPAIGN_ID_1), 150_00);
    }

    function test_RecordDonation_DonorOnTwoCampaigns_IncrementsUniqueCampaigns() public {
        // Setup: two verified assos, two campaigns, both active.
        _verify(asso1, SIREN_HASH_1);
        _verify(asso2, SIREN_HASH_2);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);
        _createDefaultCampaign(CAMPAIGN_ID_2, asso2);
        vm.warp(T0 + 1 days);

        _recordDonation(DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00);

        // Second campaign should fire DonorJoinedCampaign again but not DonorFirstDonation.
        vm.recordLogs();
        _recordDonation(DONATION_ID_2, donor1, CAMPAIGN_ID_2, 200_00);
        Vm.Log[] memory logs = vm.getRecordedLogs();

        bool sawDonationRecorded;
        bool sawDonorJoinedCampaign;
        bool sawDonorFirstDonation;
        for (uint256 i; i < logs.length; ++i) {
            bytes32 sig = logs[i].topics[0];
            if (sig == keccak256("DonationRecorded(address,bytes32,address,bytes32,uint96,bytes32,bytes32)")) {
                sawDonationRecorded = true;
            } else if (sig == keccak256("DonorJoinedCampaign(address,bytes32)")) {
                sawDonorJoinedCampaign = true;
            } else if (sig == keccak256("DonorFirstDonation(address,uint32)")) {
                sawDonorFirstDonation = true;
            }
        }
        assertTrue(sawDonationRecorded,         "DonationRecorded must fire");
        assertTrue(sawDonorJoinedCampaign,      "DonorJoinedCampaign must fire on new campaign");
        assertFalse(sawDonorFirstDonation,      "DonorFirstDonation must NOT fire (donor already known)");

        CommonLinkRegistry.DonorStats memory s = registry.getDonorStats(donor1);
        assertEq(s.totalDonated, 300_00);
        assertEq(s.donationCount, 2);
        assertEq(s.uniqueCampaigns, 2);
    }

    function test_RecordDonation_AcceptsDonationAtExactStartDate() public {
        _verify(asso1, SIREN_HASH_1);
        _createCampaign(CAMPAIGN_ID_1, asso1, 100_00, T0 + 100, T0 + 1 days, 0, BUDGET_HASH);
        vm.warp(T0 + 100); // exact startDate — inclusive per spec
        _recordDonation(DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00);
        assertEq(registry.getDonation(DONATION_ID_1).amount, 100_00);
    }

    function test_RecordDonation_AcceptsDonationAtExactEndDate() public {
        _verify(asso1, SIREN_HASH_1);
        _createCampaign(CAMPAIGN_ID_1, asso1, 100_00, T0 + 1, T0 + 1 days, 0, BUDGET_HASH);
        vm.warp(T0 + 1 days); // exact endDate — inclusive per spec
        _recordDonation(DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00);
        assertEq(registry.getDonation(DONATION_ID_1).amount, 100_00);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 7bis. recordDonation — reverts
    // ═════════════════════════════════════════════════════════════════════

    function test_RevertWhen_RecordDonationWithEmptyId() public {
        _setupReadyToDonate();
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptyDonationId.selector);
        registry.recordDonation(
            bytes32(0), donor1, CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    function test_RevertWhen_RecordDonationDuplicateId() public {
        _setupReadyToDonate();
        _recordDonation(DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.DonationAlreadyExists.selector, DONATION_ID_1)
        );
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    function test_RevertWhen_RecordDonationWithZeroDonor() public {
        _setupReadyToDonate();
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.InvalidDonor.selector);
        registry.recordDonation(
            DONATION_ID_1, address(0), CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    function test_RevertWhen_RecordDonationOnNonexistentCampaign() public {
        bytes32 ghost = bytes32(uint256(0xDEAD));
        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.CampaignDoesNotExist.selector, ghost)
        );
        registry.recordDonation(
            DONATION_ID_1, donor1, ghost, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    /// @dev Soft-revoke runtime check: even with an existing Active campaign,
    ///      donations are rejected once the association is revoked.
    function test_RevertWhen_RecordDonationOnRevokedAssociation() public {
        _setupReadyToDonate();
        _revoke(asso1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1)
        );
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    /// @dev After restore, donations should flow again. Companion to the test above.
    function test_RecordDonation_WorksAgainAfterRestore() public {
        _setupReadyToDonate();
        _revoke(asso1);
        _restore(asso1);

        _recordDonation(DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00);
        assertEq(registry.getCampaign(CAMPAIGN_ID_1).raised, 100_00);
    }

    function test_RevertWhen_RecordDonationOnPausedCampaign() public {
        _setupReadyToDonate();
        _pauseCampaign(CAMPAIGN_ID_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotActive.selector,
                CAMPAIGN_ID_1,
                CommonLinkRegistry.CampaignStatus.Paused
            )
        );
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    function test_RevertWhen_RecordDonationOnCompletedCampaign() public {
        _setupReadyToDonate();
        _completeCampaign(CAMPAIGN_ID_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotActive.selector,
                CAMPAIGN_ID_1,
                CommonLinkRegistry.CampaignStatus.Completed
            )
        );
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    function test_RevertWhen_RecordDonationOnCancelledCampaign() public {
        _setupReadyToDonate();
        _cancelCampaign(CAMPAIGN_ID_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotActive.selector,
                CAMPAIGN_ID_1,
                CommonLinkRegistry.CampaignStatus.Cancelled
            )
        );
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    function test_RevertWhen_RecordDonationBeforeStartDate() public {
        _verify(asso1, SIREN_HASH_1);
        _createCampaign(CAMPAIGN_ID_1, asso1, 100_00, T0 + 1 hours, T0 + 1 days, 0, BUDGET_HASH);
        // We're still at T0 — before startDate.

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotInWindow.selector,
                uint32(T0),
                uint32(T0 + 1 hours),
                uint32(T0 + 1 days)
            )
        );
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    function test_RevertWhen_RecordDonationAfterEndDate() public {
        _verify(asso1, SIREN_HASH_1);
        _createCampaign(CAMPAIGN_ID_1, asso1, 100_00, T0 + 1, T0 + 1 days, 0, BUDGET_HASH);
        vm.warp(T0 + 1 days + 1); // 1 second past endDate

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotInWindow.selector,
                uint32(T0 + 1 days + 1),
                uint32(T0 + 1),
                uint32(T0 + 1 days)
            )
        );
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    function test_RevertWhen_RecordDonationWithZeroAmount() public {
        _setupReadyToDonate();
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.InvalidAmount.selector);
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 0, RECEIPT_HASH_1, TX_REF_1
        );
    }

    function test_RevertWhen_RecordDonationWithEmptyReceiptHash() public {
        _setupReadyToDonate();
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptyReceiptHash.selector);
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00, bytes32(0), TX_REF_1
        );
    }

    function test_RevertWhen_RecordDonationWithEmptyTxRef() public {
        _setupReadyToDonate();
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptyTxRef.selector);
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, bytes32(0)
        );
    }

    function test_RevertWhen_RecordDonationCalledByNonRecorder() public {
        _setupReadyToDonate();
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector,
                stranger,
                RECORDER_ROLE
            )
        );
        vm.prank(stranger);
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    function test_RevertWhen_RecordDonationWhilePaused() public {
        _setupReadyToDonate();
        vm.prank(admin);
        registry.pause();

        vm.expectRevert(Pausable.EnforcedPause.selector);
        vm.prank(recorder);
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, 100_00, RECEIPT_HASH_1, TX_REF_1
        );
    }

    // ═════════════════════════════════════════════════════════════════════
    // 8. Campaign status transitions
    // ═════════════════════════════════════════════════════════════════════

    function test_PauseCampaign_FromActive() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);

        vm.expectEmit(true, true, true, true);
        emit CampaignStatusChanged(
            CAMPAIGN_ID_1,
            CommonLinkRegistry.CampaignStatus.Active,
            CommonLinkRegistry.CampaignStatus.Paused,
            curator
        );

        _pauseCampaign(CAMPAIGN_ID_1);
        assertEq(
            uint8(registry.getCampaign(CAMPAIGN_ID_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Paused)
        );
    }

    function test_UnpauseCampaign_FromPaused() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);
        _pauseCampaign(CAMPAIGN_ID_1);

        _unpauseCampaign(CAMPAIGN_ID_1);
        assertEq(
            uint8(registry.getCampaign(CAMPAIGN_ID_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Active)
        );
    }

    function test_CancelCampaign_FromActive() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);
        _cancelCampaign(CAMPAIGN_ID_1);
        assertEq(
            uint8(registry.getCampaign(CAMPAIGN_ID_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Cancelled)
        );
    }

    function test_CancelCampaign_FromPaused() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);
        _pauseCampaign(CAMPAIGN_ID_1);
        _cancelCampaign(CAMPAIGN_ID_1);
        assertEq(
            uint8(registry.getCampaign(CAMPAIGN_ID_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Cancelled)
        );
    }

    function test_CompleteCampaign_FromActive() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);
        _completeCampaign(CAMPAIGN_ID_1);
        assertEq(
            uint8(registry.getCampaign(CAMPAIGN_ID_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Completed)
        );
    }

    function test_CompleteCampaign_FromPaused() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);
        _pauseCampaign(CAMPAIGN_ID_1);
        _completeCampaign(CAMPAIGN_ID_1);
        assertEq(
            uint8(registry.getCampaign(CAMPAIGN_ID_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Completed)
        );
    }

    // ── Invalid transitions ─────────────────────────────────────────────

    function test_RevertWhen_PauseCampaignFromPaused() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);
        _pauseCampaign(CAMPAIGN_ID_1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Paused,
                CommonLinkRegistry.CampaignStatus.Paused
            )
        );
        registry.pauseCampaign(CAMPAIGN_ID_1);
    }

    function test_RevertWhen_UnpauseCampaignFromActive() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Active,
                CommonLinkRegistry.CampaignStatus.Active
            )
        );
        registry.unpauseCampaign(CAMPAIGN_ID_1);
    }

    function test_RevertWhen_CancelCancelledCampaign() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);
        _cancelCampaign(CAMPAIGN_ID_1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.CampaignAlreadyTerminal.selector, CAMPAIGN_ID_1)
        );
        registry.cancelCampaign(CAMPAIGN_ID_1);
    }

    function test_RevertWhen_CompleteCompletedCampaign() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);
        _completeCampaign(CAMPAIGN_ID_1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.CampaignAlreadyTerminal.selector, CAMPAIGN_ID_1)
        );
        registry.completeCampaign(CAMPAIGN_ID_1);
    }

    function test_RevertWhen_PauseCompletedCampaign() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);
        _completeCampaign(CAMPAIGN_ID_1);

        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.InvalidStatusTransition.selector,
                CommonLinkRegistry.CampaignStatus.Completed,
                CommonLinkRegistry.CampaignStatus.Paused
            )
        );
        registry.pauseCampaign(CAMPAIGN_ID_1);
    }

    function test_RevertWhen_StatusTransitionOnNonexistentCampaign() public {
        bytes32 ghost = bytes32(uint256(0xDEAD));
        vm.prank(curator);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.CampaignDoesNotExist.selector, ghost)
        );
        registry.pauseCampaign(ghost);
    }

    function test_RevertWhen_StatusTransitionCalledByNonCurator() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);

        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector,
                stranger,
                CURATOR_ROLE
            )
        );
        vm.prank(stranger);
        registry.pauseCampaign(CAMPAIGN_ID_1);
    }

    /// @dev Status transitions remain callable even when the global pause is active.
    ///      This matters for incident triage (spec §10).
    function test_StatusTransition_AllowedWhileGloballyPaused() public {
        _verify(asso1, SIREN_HASH_1);
        _createDefaultCampaign(CAMPAIGN_ID_1, asso1);

        vm.prank(admin);
        registry.pause();

        _pauseCampaign(CAMPAIGN_ID_1); // does not revert
        assertEq(
            uint8(registry.getCampaign(CAMPAIGN_ID_1).status),
            uint8(CommonLinkRegistry.CampaignStatus.Paused)
        );
    }

    // ═════════════════════════════════════════════════════════════════════
    // 9. markMilestoneReached
    // ═════════════════════════════════════════════════════════════════════

    function test_MarkMilestoneReached_HappyPath() public {
        _setupReadyToDonate(); // campaign has milestoneCount = 3
        uint32 nowTs = uint32(block.timestamp);

        vm.expectEmit(true, true, true, true);
        emit MilestoneReached(CAMPAIGN_ID_1, 0, PROOF_HASH, nowTs);

        vm.prank(recorder);
        registry.markMilestoneReached(CAMPAIGN_ID_1, 0, PROOF_HASH);
    }

    function test_MarkMilestoneReached_LastValidIndex() public {
        _setupReadyToDonate(); // milestoneCount = 3 → valid indices 0,1,2
        vm.prank(recorder);
        registry.markMilestoneReached(CAMPAIGN_ID_1, 2, PROOF_HASH);
    }

    function test_RevertWhen_MarkMilestoneOnNonexistentCampaign() public {
        bytes32 ghost = bytes32(uint256(0xDEAD));
        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.CampaignDoesNotExist.selector, ghost)
        );
        registry.markMilestoneReached(ghost, 0, PROOF_HASH);
    }

    function test_RevertWhen_MarkMilestoneOnPausedCampaign() public {
        _setupReadyToDonate();
        _pauseCampaign(CAMPAIGN_ID_1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(
                CommonLinkRegistry.CampaignNotActive.selector,
                CAMPAIGN_ID_1,
                CommonLinkRegistry.CampaignStatus.Paused
            )
        );
        registry.markMilestoneReached(CAMPAIGN_ID_1, 0, PROOF_HASH);
    }

    function test_RevertWhen_MarkMilestoneOutOfBounds() public {
        _setupReadyToDonate(); // milestoneCount = 3
        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.InvalidMilestoneIndex.selector, uint8(3), uint8(3))
        );
        registry.markMilestoneReached(CAMPAIGN_ID_1, 3, PROOF_HASH);
    }

    /// @dev With milestoneCount=0 ANY index is invalid (edge case spec §7.2).
    function test_RevertWhen_MarkMilestoneOnCampaignWithZeroMilestones() public {
        _verify(asso1, SIREN_HASH_1);
        _createCampaign(CAMPAIGN_ID_1, asso1, 1, T0 + 1, T0 + 30 days, 0, BUDGET_HASH);
        vm.warp(T0 + 1 days);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.InvalidMilestoneIndex.selector, uint8(0), uint8(0))
        );
        registry.markMilestoneReached(CAMPAIGN_ID_1, 0, PROOF_HASH);
    }

    function test_RevertWhen_MarkMilestoneWithEmptyProofHash() public {
        _setupReadyToDonate();
        vm.prank(recorder);
        vm.expectRevert(CommonLinkRegistry.EmptyProofHash.selector);
        registry.markMilestoneReached(CAMPAIGN_ID_1, 0, bytes32(0));
    }

    function test_RevertWhen_MarkMilestoneCalledByNonRecorder() public {
        _setupReadyToDonate();
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector,
                stranger,
                RECORDER_ROLE
            )
        );
        vm.prank(stranger);
        registry.markMilestoneReached(CAMPAIGN_ID_1, 0, PROOF_HASH);
    }

    function test_RevertWhen_MarkMilestoneWhileGloballyPaused() public {
        _setupReadyToDonate();
        vm.prank(admin);
        registry.pause();

        vm.expectRevert(Pausable.EnforcedPause.selector);
        vm.prank(recorder);
        registry.markMilestoneReached(CAMPAIGN_ID_1, 0, PROOF_HASH);
    }

    /// @dev No on-chain idempotency: same milestone can be marked twice
    ///      (spec §6: idempotency is enforced by the backend). We verify the
    ///      contract does not revert, deferring to the backend.
    function test_MarkMilestoneReached_NoOnChainIdempotency() public {
        _setupReadyToDonate();
        vm.startPrank(recorder);
        registry.markMilestoneReached(CAMPAIGN_ID_1, 0, PROOF_HASH);
        registry.markMilestoneReached(CAMPAIGN_ID_1, 0, PROOF_HASH); // does not revert
        vm.stopPrank();
    }

    // ═════════════════════════════════════════════════════════════════════
    // 10. Global pause / unpause
    // ═════════════════════════════════════════════════════════════════════

    function test_Pause_OnlyAdmin() public {
        vm.prank(admin);
        registry.pause();
        assertTrue(registry.paused());
    }

    function test_Unpause_OnlyAdmin() public {
        vm.startPrank(admin);
        registry.pause();
        registry.unpause();
        vm.stopPrank();
        assertFalse(registry.paused());
    }

    function test_RevertWhen_PauseCalledByNonAdmin() public {
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector,
                stranger,
                DEFAULT_ADMIN_ROLE
            )
        );
        vm.prank(stranger);
        registry.pause();
    }

    function test_RevertWhen_UnpauseCalledByNonAdmin() public {
        vm.prank(admin);
        registry.pause();

        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector,
                stranger,
                DEFAULT_ADMIN_ROLE
            )
        );
        vm.prank(stranger);
        registry.unpause();
    }

    /// @dev Curation must remain callable while globally paused so the team
    ///      can respond to an incident (spec §10).
    function test_GlobalPause_DoesNotBlockCuration() public {
        _verify(asso1, SIREN_HASH_1); // pre-existing verified asso

        vm.prank(admin);
        registry.pause();

        // Still callable
        _verify(asso2, SIREN_HASH_2);
        _revoke(asso1);
        _restore(asso1);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 11. View helpers — primarily there for completeness
    // ═════════════════════════════════════════════════════════════════════

    function test_GetAssociation_ReturnsZeroForUnknown() public view {
        CommonLinkRegistry.Association memory a = registry.getAssociation(asso1);
        assertFalse(a.verified);
        assertEq(a.sirenHash, bytes32(0));
        assertEq(a.verifiedAt, 0);
        assertEq(a.revokedAt, 0);
    }

    function test_GetCampaign_ReturnsZeroForUnknown() public view {
        CommonLinkRegistry.Campaign memory c = registry.getCampaign(CAMPAIGN_ID_1);
        assertEq(c.association, address(0));
        assertEq(c.goal, 0);
    }

    function test_GetDonation_ReturnsZeroForUnknown() public view {
        CommonLinkRegistry.Donation memory d = registry.getDonation(DONATION_ID_1);
        assertEq(d.donor, address(0));
        assertEq(d.amount, 0);
    }

    function test_GetDonorStats_ReturnsZeroForUnknown() public view {
        CommonLinkRegistry.DonorStats memory s = registry.getDonorStats(donor1);
        assertEq(s.totalDonated, 0);
        assertEq(s.donationCount, 0);
        assertEq(s.uniqueCampaigns, 0);
        assertEq(s.firstDonationAt, 0);
    }

    // ═════════════════════════════════════════════════════════════════════
    // 12. Single-function fuzz tests
    // ═════════════════════════════════════════════════════════════════════

    /// @dev Multiple donations from the same donor to the same campaign:
    ///      raised, totalDonated, donorCampaignTotal must equal the sum.
    function testFuzz_RecordDonation_SumsCorrectly(uint96[5] memory amounts) public {
        _setupReadyToDonate();

        // Bound each amount to a realistic ceiling (1 € — 10M €) and non-zero
        uint256 expectedSum;
        for (uint256 i; i < amounts.length; ++i) {
            amounts[i] = uint96(bound(uint256(amounts[i]), 100, 1_000_000_00));
            expectedSum += amounts[i];
        }

        for (uint256 i; i < amounts.length; ++i) {
            bytes32 dId = bytes32(uint256(0xD000) + i);
            _recordDonation(dId, donor1, CAMPAIGN_ID_1, amounts[i]);
        }

        assertEq(registry.getCampaign(CAMPAIGN_ID_1).raised, expectedSum);
        assertEq(registry.getDonorStats(donor1).totalDonated, expectedSum);
        assertEq(registry.getDonorStats(donor1).donationCount, amounts.length);
        assertEq(registry.getDonorStats(donor1).uniqueCampaigns, 1);
        assertEq(registry.getDonorCampaignTotal(donor1, CAMPAIGN_ID_1), expectedSum);
    }

    /// @dev verifyAssociation must accept any non-zero (address, sirenHash) pair.
    function testFuzz_VerifyAssociation_AcceptsAnyValidPair(
        address assoAddr, bytes32 sirenHash
    ) public {
        vm.assume(assoAddr != address(0));
        vm.assume(sirenHash != bytes32(0));

        _verify(assoAddr, sirenHash);

        CommonLinkRegistry.Association memory a = registry.getAssociation(assoAddr);
        assertTrue(a.verified);
        assertEq(a.sirenHash, sirenHash);
    }

    /// @dev Soft-revoke: donating to a campaign whose asso is revoked must
    ///      always revert, regardless of the amount or campaign status.
    function testFuzz_RecordDonation_RevertsWhenAssoRevoked(uint96 amount) public {
        amount = uint96(bound(amount, 1, type(uint96).max));

        _setupReadyToDonate();
        _revoke(asso1);

        vm.prank(recorder);
        vm.expectRevert(
            abi.encodeWithSelector(CommonLinkRegistry.AssociationNotVerified.selector, asso1)
        );
        registry.recordDonation(
            DONATION_ID_1, donor1, CAMPAIGN_ID_1, amount, RECEIPT_HASH_1, TX_REF_1
        );
    }
}

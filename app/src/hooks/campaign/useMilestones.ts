'use client';

import { useState, useCallback } from 'react';
import { addMilestone, updateMilestone, deleteMilestone } from '@/lib/api/campaign';
import { useToastStore } from '@/stores/toastStore';
import type { MilestoneDto, UpdateMilestoneRequest } from '@/types/campaign';

/** Return type of {@link useMilestones}. */
export interface UseMilestonesReturn {
  /** True while any milestone API call is in-flight. */
  isSaving: boolean;
  /**
   * Creates a new milestone with default values and returns it.
   * @param campaignId - UUID of the campaign.
   * @param sortOrder - Initial sort order for the new milestone.
   * @param defaultTargetAmount - Starting target amount (≥ previous milestone's amount).
   */
  addNewMilestone: (campaignId: string, sortOrder: number, defaultTargetAmount: number) => Promise<MilestoneDto | null>;
  /**
   * Persists partial milestone changes.
   * @param campaignId - UUID of the campaign.
   * @param milestoneId - UUID of the milestone to update.
   * @param data - Fields to update.
   */
  updateExistingMilestone: (
    campaignId: string,
    milestoneId: string,
    data: UpdateMilestoneRequest,
  ) => Promise<void>;
  /**
   * Deletes a milestone.
   * @param campaignId - UUID of the campaign.
   * @param milestoneId - UUID of the milestone to delete.
   */
  removeExistingMilestone: (campaignId: string, milestoneId: string) => Promise<void>;
}

/**
 * Hook for milestone CRUD operations.
 *
 * Does not manage local milestone state — milestones are sourced from
 * the parent `campaign` prop and refreshed via `onMilestonesChanged`.
 */
export function useMilestones(): UseMilestonesReturn {
  const [isSaving, setIsSaving] = useState(false);
  const { addToast } = useToastStore();

  /** Creates a new milestone with sensible defaults. */
  const addNewMilestone = useCallback(
    async (campaignId: string, sortOrder: number, defaultTargetAmount: number): Promise<MilestoneDto | null> => {
      setIsSaving(true);
      try {
        const milestone = await addMilestone(campaignId, {
          title: 'Nouveau palier',
          emoji: '🎯',
          targetAmount: defaultTargetAmount,
          sortOrder,
        });
        addToast('success', 'milestoneCreated');
        return milestone;
      } catch {
        addToast('error', 'errors.serverError');
        return null;
      } finally {
        setIsSaving(false);
      }
    },
    [addToast],
  );

  /** Persists partial milestone changes. Shows a toast only on error (silent on success for debounced saves). */
  const updateExistingMilestone = useCallback(
    async (
      campaignId: string,
      milestoneId: string,
      data: UpdateMilestoneRequest,
    ): Promise<void> => {
      try {
        await updateMilestone(campaignId, milestoneId, data);
      } catch {
        addToast('error', 'errors.serverError');
      }
    },
    [addToast],
  );

  /** Deletes a milestone and shows a success toast. */
  const removeExistingMilestone = useCallback(
    async (campaignId: string, milestoneId: string): Promise<void> => {
      setIsSaving(true);
      try {
        await deleteMilestone(campaignId, milestoneId);
        addToast('success', 'milestoneDeleted');
      } catch {
        addToast('error', 'errors.serverError');
      } finally {
        setIsSaving(false);
      }
    },
    [addToast],
  );

  return { isSaving, addNewMilestone, updateExistingMilestone, removeExistingMilestone };
}

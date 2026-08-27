import type { AssociationStatus } from '@/types/association';
import type { CampaignStatus } from '@/types/campaign';

/** Maps an AssociationStatus (IC-44) to the corresponding badge CSS classes. */
export const ASSOCIATION_STATUS_BADGE_CLASS: Record<AssociationStatus, string> = {
  ACTIVE: 'badge badge-success',
  ALERT: 'badge badge-warning',
  SUSPENDED: 'badge badge-error',
};

/** Maps a CampaignStatus to the corresponding badge CSS classes. */
export const CAMPAIGN_STATUS_BADGE_CLASS: Record<CampaignStatus, string> = {
  DRAFT: 'badge badge-neutral',
  LIVE: 'badge badge-success',
  PAUSED: 'badge badge-warning',
  REVERT_REQUESTED: 'badge badge-warning',
  CANCELLED: 'badge badge-error',
  COMPLETED: 'badge badge-success',
  ENDED: 'badge badge-neutral',
};

/**
 * Reads one field out of a journal entry payload.
 *
 * Mirrors `payloadText` in `alerts/[alertId]/page.tsx` — shapes differ per event type
 * (a CAMPAIGN_REVIEW_RETAINED payload has no `reason`), hence the tolerant lookup.
 */
export function payloadText(payload: Record<string, unknown>, key: string): string {
  const value = payload?.[key];
  if (value === null || value === undefined) return '—';
  return String(value);
}

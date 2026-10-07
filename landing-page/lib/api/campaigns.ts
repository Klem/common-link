import { API_URL } from '@/lib/constants';
import type { ActionPlaceType, CampaignCause, CampaignScope } from '@/lib/causes';

/**
 * Place of action of a campaign, mirror of the API `ActionPlaceDto`.
 * Label and coordinates are resolved server-side.
 */
export interface ActionPlace {
  type: ActionPlaceType;
  /** INSEE commune code, department code or ISO country code; `null` for the whole of France. */
  code: string | null;
  /** Display label, e.g. `Vallauris (06)`, `France entière`, `Sénégal`. */
  label: string;
  /** Reach derived from `type`. */
  scope: CampaignScope;
  /** Commune centre; `null` unless `type` is `COMMUNE`. */
  latitude: number | null;
  longitude: number | null;
}

/**
 * Published campaign, as returned by `GET /api/public/campaigns`.
 *
 * Exact mirror of the API `PublicCampaignListItemDto`. No status, no internal identifier:
 * the API only returns what the card displays and what the discovery filters need.
 */
export interface PublicCampaign {
  campaignId: string;
  campaignName: string;
  campaignEmoji: string;
  /**
   * Cause of the campaign, or `null`. Typed as `string` on purpose: an API deployed ahead of this
   * page may send a value it does not know yet — filter with `isDisplayedCause`.
   */
  campaignCategory: CampaignCause | string | null;
  /** Place of action with its derived scope, or `null` when the association has not set it. */
  actionPlace: ActionPlace | null;
  /**
   * Serving path of the cover image, or `null`.
   * Never build an `<img>` when it is null: the URL answers 404, not a placeholder.
   */
  coverImage: string | null;
  /**
   * ISO-8601 timestamp of the campaign's last modification. `coverImage` is served on a stable URL
   * (it does not change on replacement) with a 5-minute public cache on the API side: this field
   * is the cache-busting token, see {@link coverImageUrl}.
   */
  campaignUpdatedAt: string;
  goal: number;
  raised: number;
  milestoneCount: number;
  associationName: string;
  /** Serving path of the association logo, or `null`. Same caveat as `coverImage`. */
  associationLogo: string | null;
  /** Absolute URL of the public donation page, built by the API on `app.frontend-url`. */
  donationUrl: string;
}

/**
 * Resolves `coverImage` to an absolute URL with a cache-busting parameter.
 *
 * The serving endpoint is public and stable (campaign id only), served with
 * `Cache-Control: max-age=300, public` by the API — without a version, a browser or a CDN may
 * keep showing the old image up to 5 minutes after a replacement.
 *
 * @param coverImage - Path returned by the API (`PublicCampaign.coverImage`), never `null` here:
 *   check `coverImage !== null` before calling this function.
 * @param version - Cache-busting token, `PublicCampaign.campaignUpdatedAt`.
 */
export function coverImageUrl(coverImage: string, version: string): string {
  return `${API_URL}${coverImage}?v=${encodeURIComponent(version)}`;
}

/**
 * Fetches the campaigns currently open to donations.
 *
 * Server-side call revalidated every 5 minutes, aligned on the API `Cache-Control`.
 *
 * Never throws: an unreachable API must yield an empty state on the public page, not a 500.
 * The failure is logged server-side.
 */
export async function fetchLiveCampaigns(): Promise<PublicCampaign[]> {
  try {
    const res = await fetch(`${API_URL}/api/public/campaigns`, {
      next: { revalidate: 300 },
    });

    if (!res.ok) {
      console.error(`[campaigns] GET /api/public/campaigns answered ${res.status}`);
      return [];
    }

    return (await res.json()) as PublicCampaign[];
  } catch (error) {
    console.error('[campaigns] API unreachable:', error);
    return [];
  }
}

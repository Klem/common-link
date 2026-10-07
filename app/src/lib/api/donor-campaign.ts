import api from '@/lib/api';
import { DonorSort, SortDirection } from '@/types/donor-campaign';
import type { CampaignDonorDto, DonationDto, Page } from '@/types/donor-campaign';

const base = (campaignId: string) => `/api/campaigns/${campaignId}`;

/**
 * Returns a paginated list of donor aggregates for a campaign, sorted server-side by [sort]/[direction].
 * Anonymous donors appear as "Anonyme" (masked by the API) and sort as such on [DonorSort.NAME].
 * Calls `GET /api/campaigns/{campaignId}/donors`.
 */
export const listDonors = (
  campaignId: string,
  page = 0,
  size = 12,
  search?: string,
  sort: DonorSort = DonorSort.AMOUNT,
  direction: SortDirection = SortDirection.DESC,
): Promise<Page<CampaignDonorDto>> =>
  api
    .get<Page<CampaignDonorDto>>(`${base(campaignId)}/donors`, {
      params: { page, size, ...(search ? { search } : {}), sort, direction },
    })
    .then((r) => r.data);

/**
 * Returns a paginated list of donations made by a donor on a campaign.
 * Calls `GET /api/campaigns/{campaignId}/donors/{donorId}/donations`.
 */
export const getDonorDonations = (
  campaignId: string,
  donorId: string,
  page = 0,
  size = 20,
): Promise<Page<DonationDto>> =>
  api
    .get<Page<DonationDto>>(`${base(campaignId)}/donors/${donorId}/donations`, {
      params: { page, size },
    })
    .then((r) => r.data);

/**
 * Returns a single donation scoped to a campaign.
 * Calls `GET /api/campaigns/{campaignId}/donations/{donationId}`.
 */
export const getDonation = (campaignId: string, donationId: string): Promise<DonationDto> =>
  api.get<DonationDto>(`${base(campaignId)}/donations/${donationId}`).then((r) => r.data);

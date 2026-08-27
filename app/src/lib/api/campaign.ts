import api, { apiUrl } from '@/lib/api';
import type {
  CampaignDto,
  CampaignSummaryDto,
  MilestoneDto,
  CreateCampaignRequest,
  UpdateCampaignRequest,
  SaveBudgetRequest,
  CreateMilestoneRequest,
  UpdateMilestoneRequest,
  ReorderMilestonesRequest,
} from '@/types/campaign';

/**
 * Fetches the list of campaigns for the current association.
 * Calls `GET /api/association/campaigns`.
 *
 * @returns Array of campaign summary DTOs.
 */
export const getCampaigns = (): Promise<CampaignSummaryDto[]> =>
  api.get<CampaignSummaryDto[]>('/api/association/campaigns').then((r) => r.data);

/**
 * Fetches a single campaign with its full budget and milestones.
 * Calls `GET /api/association/campaigns/:id`.
 *
 * @param id - UUID of the campaign.
 * @returns Full campaign DTO.
 */
export const getCampaign = (id: string): Promise<CampaignDto> =>
  api.get<CampaignDto>(`/api/association/campaigns/${id}`).then((r) => r.data);

/**
 * Creates a new campaign for the current association.
 * Calls `POST /api/association/campaigns`.
 *
 * @param data - Campaign creation payload.
 * @returns The newly created campaign DTO.
 */
export const createCampaign = (data: CreateCampaignRequest): Promise<CampaignDto> =>
  api.post<CampaignDto>('/api/association/campaigns', data).then((r) => r.data);

/**
 * Updates an existing campaign.
 * Calls `PUT /api/association/campaigns/:id`.
 *
 * @param id - UUID of the campaign to update.
 * @param data - Fields to update.
 * @returns The updated campaign DTO.
 */
export const updateCampaign = (id: string, data: UpdateCampaignRequest): Promise<CampaignDto> =>
  api.put<CampaignDto>(`/api/association/campaigns/${id}`, data).then((r) => r.data);

/**
 * Deletes a campaign by ID.
 * Calls `DELETE /api/association/campaigns/:id`.
 *
 * @param id - UUID of the campaign to delete.
 */
export const deleteCampaign = (id: string): Promise<void> =>
  api.delete(`/api/association/campaigns/${id}`);

/**
 * Uploads (or replaces) the campaign cover image.
 * Calls `PUT /api/association/campaigns/:id/cover` as multipart form data.
 *
 * Accepted types and size limit are mirrored from the backend (JPEG/PNG/WebP, 5 MB max) and
 * checked client-side before the call — the backend still re-validates (rule 8).
 *
 * @param id - UUID of the campaign.
 * @param file - Image file to upload.
 * @returns The updated campaign DTO, with `coverImage` set to the public serving path.
 */
export const uploadCampaignCover = (id: string, file: File): Promise<CampaignDto> => {
  const form = new FormData();
  form.append('file', file);
  return api
    .put<CampaignDto>(`/api/association/campaigns/${id}/cover`, form)
    .then((r) => r.data);
};

/**
 * Removes the campaign cover image.
 * Calls `DELETE /api/association/campaigns/:id/cover`.
 *
 * @param id - UUID of the campaign.
 * @returns The updated campaign DTO, with `coverImage` null.
 */
export const deleteCampaignCover = (id: string): Promise<CampaignDto> =>
  api.delete<CampaignDto>(`/api/association/campaigns/${id}/cover`).then((r) => r.data);

/**
 * Resolves the API-relative `coverImage` path of a campaign into an absolute URL usable
 * in an `<img src>`. The serving endpoint is public, so no token is needed.
 *
 * The serving path is stable across replacements (it only carries the campaign id), so a
 * `version` must be appended — otherwise neither React nor the browser cache would notice that
 * the image behind the URL changed. Pass `CampaignDto.updatedAt`, bumped by every upload.
 *
 * @param coverImage - Path stored in `CampaignDto.coverImage`, or an already absolute URL.
 * @param version - Cache-busting token, typically the campaign's `updatedAt`.
 * @returns Absolute URL of the cover image.
 */
export const campaignCoverUrl = (coverImage: string, version?: string): string => {
  const absolute = apiUrl(coverImage);
  return version ? `${absolute}?v=${encodeURIComponent(version)}` : absolute;
};

/**
 * Replaces the full budget for a campaign.
 * Calls `PUT /api/association/campaigns/:campaignId/budget`.
 *
 * @param campaignId - UUID of the campaign.
 * @param data - Full budget payload (replaces existing sections and items).
 * @returns The updated campaign DTO.
 */
export const saveBudget = (campaignId: string, data: SaveBudgetRequest): Promise<CampaignDto> =>
  api.put<CampaignDto>(`/api/association/campaigns/${campaignId}/budget`, data).then((r) => r.data);

/**
 * Adds a milestone to a campaign.
 * Calls `POST /api/association/campaigns/:campaignId/milestones`.
 *
 * @param campaignId - UUID of the campaign.
 * @param data - Milestone creation payload.
 * @returns The newly created milestone DTO.
 */
export const addMilestone = (
  campaignId: string,
  data: CreateMilestoneRequest,
): Promise<MilestoneDto> =>
  api
    .post<MilestoneDto>(`/api/association/campaigns/${campaignId}/milestones`, data)
    .then((r) => r.data);

/**
 * Updates a milestone.
 * Calls `PUT /api/association/campaigns/:campaignId/milestones/:milestoneId`.
 *
 * @param campaignId - UUID of the campaign.
 * @param milestoneId - UUID of the milestone to update.
 * @param data - Fields to update.
 * @returns The updated milestone DTO.
 */
export const updateMilestone = (
  campaignId: string,
  milestoneId: string,
  data: UpdateMilestoneRequest,
): Promise<MilestoneDto> =>
  api
    .put<MilestoneDto>(
      `/api/association/campaigns/${campaignId}/milestones/${milestoneId}`,
      data,
    )
    .then((r) => r.data);

/**
 * Deletes a milestone from a campaign.
 * Calls `DELETE /api/association/campaigns/:campaignId/milestones/:milestoneId`.
 *
 * @param campaignId - UUID of the campaign.
 * @param milestoneId - UUID of the milestone to delete.
 */
export const deleteMilestone = (campaignId: string, milestoneId: string): Promise<void> =>
  api.delete(`/api/association/campaigns/${campaignId}/milestones/${milestoneId}`);

/**
 * Publishes a campaign (DRAFT → LIVE).
 * Calls `PUT /api/association/campaigns/:id` with status LIVE.
 *
 * @param id - UUID of the campaign to publish.
 * @returns The updated campaign DTO.
 */
/**
 * @param cguAccepted Must be true on the first publish for this association's current CGU
 *   version (art. 1740 A CGI proof of acceptance); ignored once already accepted.
 */
export const publishCampaign = (id: string, cguAccepted: boolean): Promise<CampaignDto> =>
  api.put<CampaignDto>(`/api/association/campaigns/${id}`, { status: 'LIVE', cguAccepted }).then((r) => r.data);

/**
 * Reorders milestones for a campaign.
 * Calls `PUT /api/association/campaigns/:campaignId/milestones/reorder`.
 *
 * @param campaignId - UUID of the campaign.
 * @param data - Ordered list of milestone UUIDs.
 * @returns The reordered list of milestone DTOs.
 */
export const reorderMilestones = (
  campaignId: string,
  data: ReorderMilestonesRequest,
): Promise<MilestoneDto[]> =>
  api
    .put<MilestoneDto[]>(`/api/association/campaigns/${campaignId}/milestones/reorder`, data)
    .then((r) => r.data);

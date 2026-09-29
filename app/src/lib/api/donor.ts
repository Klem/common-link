import api from '@/lib/api';
import type {
  DonorAssociationDto,
  DonorDonationFiltersDto,
  DonorDonationsPage,
  DonorDonationsQuery,
  DonorProfileDto,
  DonorStatsDto,
  UpdateDonorProfileRequest,
} from '@/types/donor';

/**
 * Fetches the current donor's profile from `GET /api/donor/me`.
 * Requires a valid Bearer token (attached automatically by the Axios interceptor).
 *
 * @returns The donor profile DTO.
 */
export const getDonorProfile = (): Promise<DonorProfileDto> =>
  api.get<DonorProfileDto>('/api/donor/me').then((r) => r.data);

/**
 * Updates the current donor's profile via `PATCH /api/donor/me`.
 * Only the fields provided in `data` are modified on the backend.
 *
 * @param data - Partial profile update payload.
 * @returns The updated donor profile DTO.
 */
export const updateDonorProfile = (data: UpdateDonorProfileRequest): Promise<DonorProfileDto> =>
  api.patch<DonorProfileDto>('/api/donor/me', data).then((r) => r.data);

/**
 * Fetches a page of the current donor's confirmed donations via `GET /api/donor/me/donations`.
 * Both `associationId` and `year` are optional filters.
 *
 * @param query - Pagination and optional filters.
 * @returns The requested page of donations.
 */
export const getDonorDonations = (query: DonorDonationsQuery = {}): Promise<DonorDonationsPage> =>
  api
    .get<DonorDonationsPage>('/api/donor/me/donations', {
      params: { page: 0, size: 20, ...query },
    })
    .then((r) => r.data);

/**
 * Fetches the values available in the donation history filters via
 * `GET /api/donor/me/donations/filters`.
 *
 * @returns Associations funded and years with at least one confirmed donation.
 */
export const getDonorDonationFilters = (): Promise<DonorDonationFiltersDto> =>
  api.get<DonorDonationFiltersDto>('/api/donor/me/donations/filters').then((r) => r.data);

/**
 * Fetches the donor's headline figures via `GET /api/donor/me/stats`.
 *
 * @returns Total donated, donation count, association count, and estimated tax reduction.
 */
export const getDonorStats = (): Promise<DonorStatsDto> =>
  api.get<DonorStatsDto>('/api/donor/me/stats').then((r) => r.data);

/**
 * Fetches the associations supported by the current donor via `GET /api/donor/me/associations`.
 *
 * @returns One entry per funded association, most funded first.
 */
export const getDonorAssociations = (): Promise<DonorAssociationDto[]> =>
  api.get<DonorAssociationDto[]>('/api/donor/me/associations').then((r) => r.data);

/**
 * Downloads the fiscal receipt PDF of a donation via
 * `GET /api/donor/me/donations/{donationId}/receipt`.
 * Ownership is enforced server-side by `DonorReadScope.assertOwnsDonation`.
 *
 * @param donationId - Id of the donation to fetch the receipt for.
 * @returns The PDF as a Blob.
 */
export const downloadDonationReceipt = (donationId: string): Promise<Blob> =>
  api
    .get<Blob>(`/api/donor/me/donations/${donationId}/receipt`, { responseType: 'blob' })
    .then((r) => r.data);

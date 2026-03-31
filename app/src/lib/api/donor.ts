import api from '@/lib/api';
import type { DonorProfileDto, UpdateDonorProfileRequest } from '@/types/donor';

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

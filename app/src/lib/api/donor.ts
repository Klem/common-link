import api from '@/lib/api';
import type { DonorProfileDto, UpdateDonorProfileRequest } from '@/types/donor';

export const getDonorProfile = (): Promise<DonorProfileDto> =>
  api.get<DonorProfileDto>('/api/donor/me').then((r) => r.data);

export const updateDonorProfile = (data: UpdateDonorProfileRequest): Promise<DonorProfileDto> =>
  api.patch<DonorProfileDto>('/api/donor/me', data).then((r) => r.data);

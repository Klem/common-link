import api from '@/lib/api';
import type { MollieKycStatus } from '@/types/mollie-connect';

interface MollieAuthUrlDto {
  authUrl: string;
}

export const getMollieAuthUrl = (): Promise<MollieAuthUrlDto> =>
  api.get<MollieAuthUrlDto>('/api/mollie/connect/auth-url').then((r) => r.data);

export const getMollieKycStatus = (): Promise<MollieKycStatus> =>
  api.get<MollieKycStatus>('/api/mollie/connect/status').then((r) => r.data);

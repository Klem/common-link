import api from '@/lib/api';
import type { AssociationProfileDto, UpdateAssociationProfileRequest } from '@/types/association';

export const getAssociationProfile = (): Promise<AssociationProfileDto> =>
  api.get<AssociationProfileDto>('/api/association/me').then((r) => r.data);

export const updateAssociationProfile = (
  data: UpdateAssociationProfileRequest,
): Promise<AssociationProfileDto> =>
  api.patch<AssociationProfileDto>('/api/association/me', data).then((r) => r.data);

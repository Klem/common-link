import api from '@/lib/api';
import type { AssociationProfileDto, UpdateAssociationProfileRequest, DashboardStats } from '@/types/association';

/**
 * Fetches the current association's profile from `GET /api/association/me`.
 * Requires a valid Bearer token (attached automatically by the Axios interceptor).
 *
 * @returns The association profile DTO.
 */
export const getAssociationProfile = (): Promise<AssociationProfileDto> =>
  api.get<AssociationProfileDto>('/api/association/me').then((r) => r.data);

/**
 * Updates the current association's profile via `PATCH /api/association/me`.
 * Only the fields provided in `data` are modified on the backend.
 *
 * @param data - Partial profile update payload.
 * @returns The updated association profile DTO.
 */
export const updateAssociationProfile = (
  data: UpdateAssociationProfileRequest,
): Promise<AssociationProfileDto> =>
  api.patch<AssociationProfileDto>('/api/association/me', data).then((r) => r.data);

/**
 * Fetches aggregated dashboard statistics for the authenticated association
 * from `GET /api/association/dashboard`.
 *
 * @returns Stats DTO including totals, 6-month chart data, and recent activity.
 */
export const getDashboard = (): Promise<DashboardStats> =>
  api.get<DashboardStats>('/api/association/dashboard').then((r) => r.data);

/**
 * Generates (or rotates) the widget token for the current association.
 * Calls `POST /api/association/me/widget/token`.
 * Rotating revokes the previous token and breaks existing integrations.
 *
 * @returns The newly generated widget token.
 */
export const generateWidgetToken = (): Promise<{ widgetToken: string }> =>
  api.post<{ widgetToken: string }>('/api/association/me/widget/token').then((r) => r.data);

/**
 * Deletes the widget token for the current association, disabling the widget.
 * Calls `DELETE /api/association/me/widget/token`.
 */
export const deleteWidgetToken = (): Promise<void> =>
  api.delete('/api/association/me/widget/token');

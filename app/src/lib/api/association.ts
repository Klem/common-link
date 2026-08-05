import api from '@/lib/api';
import type {
  AssociationProfileDto,
  UpdateAssociationProfileRequest,
  UpdateLandingConfigRequest,
  LandingPreviewToken,
  DashboardStats,
} from '@/types/association';

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

/**
 * Updates widget configuration via `PATCH /api/association/me/widget`.
 * Sets the allowed origin for post-payment redirects.
 */
export const updateWidgetConfig = (data: { widgetAllowedOrigin?: string | null }): Promise<void> =>
  api.patch('/api/association/me/widget', data);

/**
 * Updates the landing page configuration via `PATCH /api/association/me/landing`.
 * Only the provided fields are modified — send one field per user interaction.
 *
 * @param data - Theme and/or section visibility flags.
 * @returns The updated association profile DTO.
 */
export const updateLandingConfig = (
  data: UpdateLandingConfigRequest,
): Promise<AssociationProfileDto> =>
  api.patch<AssociationProfileDto>('/api/association/me/landing', data).then((r) => r.data);

/**
 * Uploads (or replaces) the landing page logo.
 * Calls `PUT /api/association/me/landing/logo` as multipart form data.
 *
 * Accepted types and size limit are mirrored from the backend (JPEG/PNG/WebP, 2 MB max) and
 * checked client-side before the call — the backend still re-validates (rule 8).
 *
 * @param file - Image file to upload.
 * @returns The updated profile, with `landingLogo` set to the public serving path.
 */
export const uploadLandingLogo = (file: File): Promise<AssociationProfileDto> => {
  const form = new FormData();
  form.append('file', file);
  return api
    .put<AssociationProfileDto>('/api/association/me/landing/logo', form)
    .then((r) => r.data);
};

/**
 * Removes the landing page logo.
 * Calls `DELETE /api/association/me/landing/logo`.
 *
 * @returns The updated profile, with `landingLogo` set to null.
 */
export const deleteLandingLogo = (): Promise<AssociationProfileDto> =>
  api.delete<AssociationProfileDto>('/api/association/me/landing/logo').then((r) => r.data);

/**
 * Requests a short-lived landing page preview token.
 * Calls `POST /api/association/me/landing/preview-session`.
 *
 * Must be called before every preview load: the token lives 10 minutes, and a cached one would
 * eventually render an error page indistinguishable from a real failure.
 */
export const createLandingPreviewSession = (): Promise<LandingPreviewToken> =>
  api
    .post<LandingPreviewToken>('/api/association/me/landing/preview-session')
    .then((r) => r.data);

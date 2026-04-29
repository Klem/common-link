import api from '@/lib/api';
import type { MoneriumAuthUrlDto, MoneriumStatusDto } from '@/types/monerium';

/**
 * Fetches a fresh Monerium OAuth2 authorization URL from `GET /api/monerium/auth-url`.
 * The backend generates a PKCE code_verifier, stores it server-side, and returns the
 * authorization URL that should be opened in a popup window.
 *
 * @returns The authorization URL DTO.
 */
export const getMoneriumAuthUrl = (): Promise<MoneriumAuthUrlDto> =>
  api.get<MoneriumAuthUrlDto>('/api/monerium/auth-url').then((r) => r.data);

/**
 * Fetches the current Monerium connection status from `GET /api/monerium/status`.
 * Returns whether the authenticated association has a valid active Monerium connection.
 *
 * @returns The connection status DTO.
 */
export const getMoneriumStatus = (): Promise<MoneriumStatusDto> =>
  api.get<MoneriumStatusDto>('/api/monerium/status').then((r) => r.data);

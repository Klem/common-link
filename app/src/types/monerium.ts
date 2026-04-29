/**
 * Message types posted from the OAuth popup window to the opener via `window.postMessage`.
 */
export const MoneriumPopupMessage = {
  CONNECTED: 'MONERIUM_CONNECTED',
  ERROR: 'MONERIUM_ERROR',
} as const;

export type MoneriumPopupMessage = typeof MoneriumPopupMessage[keyof typeof MoneriumPopupMessage];

/**
 * Response from `GET /api/monerium/auth-url`.
 * Contains the Monerium OAuth2 authorization URL to open in a popup.
 */
export interface MoneriumAuthUrlDto {
  /** The full Monerium OAuth2 authorization URL including PKCE challenge. */
  authUrl: string;
}

/**
 * Response from `GET /api/monerium/status`.
 * Indicates whether the association has an active Monerium connection.
 */
export interface MoneriumStatusDto {
  /** True if the association has a valid Monerium OAuth2 connection. */
  connected: boolean;
  /** True if an OAuth2 flow was started but the code exchange has not completed yet. */
  pending: boolean;
}

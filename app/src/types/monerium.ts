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
}

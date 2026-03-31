/**
 * Represents an authenticated user returned by the backend.
 * The `role` field drives all post-login routing and dashboard access control.
 */
export interface UserDto {
  /** Unique user identifier (UUID). */
  id: string;
  email: string;
  /** Determines which dashboard the user is redirected to after login. */
  role: 'ASSOCIATION' | 'DONOR';
  displayName: string;
  avatarUrl?: string;
  /** The authentication provider used to create or log in this account. */
  provider: 'GOOGLE' | 'EMAIL' | 'MAGIC_LINK';
  emailVerified: boolean;
  /** ISO 8601 creation timestamp. */
  createdAt: string;
}

/**
 * Shape of the response returned by all authentication endpoints
 * (`/api/auth/login`, `/api/auth/login/google`, `/api/auth/refresh`, etc.).
 *
 * - `accessToken` is kept in memory (Zustand) — short-lived (15 min).
 * - `refreshToken` is persisted in the `cl-refresh` cookie — long-lived (30 days).
 */
export interface AuthResponseDto {
  accessToken: string;
  refreshToken: string;
  user: UserDto;
}

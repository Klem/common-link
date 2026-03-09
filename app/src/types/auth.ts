export interface UserDto {
  id: string;
  email: string;
  role: 'ASSOCIATION' | 'DONOR';
  displayName: string;
  avatarUrl?: string;
  provider: 'GOOGLE' | 'EMAIL' | 'MAGIC_LINK';
  emailVerified: boolean;
  createdAt: string;
}

export interface AuthResponseDto {
  accessToken: string;
  refreshToken: string;
  user: UserDto;
}

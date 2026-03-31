export interface DonorProfileDto {
  id: string;
  displayName: string | null;
  anonymous: boolean;
}

export interface UpdateDonorProfileRequest {
  displayName?: string;
  anonymous?: boolean;
}

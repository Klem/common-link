import type { Page } from '@/types/payment';

export { type Page };

export const DonorSort = {
  AMOUNT: 'amount',
  DATE: 'date',
  NAME: 'name',
} as const;
export type DonorSort = (typeof DonorSort)[keyof typeof DonorSort];

/**
 * Aggregated view of a donor's contributions to a single campaign.
 * displayName is already masked by the API: anonymous donors are shown as "Anonyme".
 */
export interface CampaignDonorDto {
  donorId: string;
  displayName: string;
  totalAmount: number;
  txCount: number;
  lastDonationAt: string | null;
}

/**
 * Public representation of a single donation.
 * onChain is true when confirmedAt is set (recorded on-chain).
 */
export interface DonationDto {
  id: string;
  amount: number;
  providerRef: string;
  confirmedAt: string | null;
  createdAt: string;
  onChain: boolean;
}

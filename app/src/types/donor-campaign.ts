import type { Page } from '@/types/payment';

export { type Page };

export const DonorSort = {
  AMOUNT: 'amount',
  DATE: 'date',
  NAME: 'name',
  COUNT: 'count',
} as const;
export type DonorSort = (typeof DonorSort)[keyof typeof DonorSort];

export const SortDirection = {
  ASC: 'asc',
  DESC: 'desc',
} as const;
export type SortDirection = (typeof SortDirection)[keyof typeof SortDirection];

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

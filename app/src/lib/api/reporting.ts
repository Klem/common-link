import api from '@/lib/api';
import type { BudgetVariance } from '@/types/reporting';

/**
 * Fetches the budget variance report for a campaign.
 * Calls `GET /api/campaigns/{campaignId}/reporting`.
 */
export const getReporting = (campaignId: string): Promise<BudgetVariance> =>
  api.get<BudgetVariance>(`/api/campaigns/${campaignId}/reporting`).then((r) => r.data);

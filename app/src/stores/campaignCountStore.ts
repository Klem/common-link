import { create } from 'zustand';

interface CampaignCountState {
  count: number;
  setCampaignCount: (count: number) => void;
}

export const useCampaignCountStore = create<CampaignCountState>((set) => ({
  count: 0,
  setCampaignCount: (count) => set({ count }),
}));

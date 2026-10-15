'use client';

import { useEffect, useState } from 'react';
import { getCampaigns } from '@/lib/api/campaign';
import { CampaignStatus } from '@/types/campaign';
import type { AssociationProfileDto } from '@/types/association';
import type { CampaignSummaryDto } from '@/types/campaign';
import { CampaignSetupCard } from './CampaignSetupCard';
import { LandingAppearanceCard } from './LandingAppearanceCard';
import { DiffusionMethodCard } from './DiffusionMethodCard';

interface Props {
  profile: AssociationProfileDto | null;
  /** Re-fetches the full profile after any change — the blocks read their state from it. */
  onProfileChanged: () => Promise<void>;
}

/**
 * Diffusion tab — everything the association needs to publish its collection, in the order it fills
 * it in: the campaign and its diffusion link, the donation page appearance, then ONE diffusion method
 * with its own settings (campaign page URL, code, GTM).
 */
export function DiffusionTab({ profile, onProfileChanged }: Props) {
  const [campaigns, setCampaigns] = useState<CampaignSummaryDto[]>([]);

  useEffect(() => {
    getCampaigns().then(setCampaigns).catch(() => {});
  }, []);

  const destination = campaigns.find((c) => c.id === profile?.widgetDestinationCampaignId) ?? null;
  const isDestinationLive = destination?.status === CampaignStatus.LIVE;

  return (
    <div>
      <CampaignSetupCard profile={profile} campaigns={campaigns} onProfileChanged={onProfileChanged} />
      <LandingAppearanceCard profile={profile} onProfileChanged={onProfileChanged} />
      <DiffusionMethodCard
        profile={profile}
        isDestinationLive={isDestinationLive}
        onProfileChanged={onProfileChanged}
      />
    </div>
  );
}

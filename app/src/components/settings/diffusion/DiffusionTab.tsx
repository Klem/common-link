'use client';

import { useEffect, useState } from 'react';
import { getCampaigns } from '@/lib/api/campaign';
import { CampaignStatus } from '@/types/campaign';
import type { AssociationProfileDto } from '@/types/association';
import type { CampaignSummaryDto } from '@/types/campaign';
import { CampaignSetupCard } from './CampaignSetupCard';
import { CampaignPageUrlCard } from './CampaignPageUrlCard';
import { TrackingCard } from './TrackingCard';
import { LandingAppearanceCard } from './LandingAppearanceCard';
import { EmbedMethods } from './EmbedMethods';

interface Props {
  profile: AssociationProfileDto | null;
  /** Re-fetches the full profile after any change — the blocks read their state from it. */
  onProfileChanged: () => Promise<void>;
}

/**
 * Diffusion tab — everything the association needs to publish its collection on its own site,
 * in the order it fills it in: the campaign and its token, the campaign page URL, GTM tracking,
 * the landing page appearance, then one embed method to copy.
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
      <CampaignPageUrlCard profile={profile} onProfileChanged={onProfileChanged} />
      <TrackingCard profile={profile} onProfileChanged={onProfileChanged} />
      <LandingAppearanceCard profile={profile} onProfileChanged={onProfileChanged} />
      <EmbedMethods profile={profile} isDestinationLive={isDestinationLive} />
    </div>
  );
}

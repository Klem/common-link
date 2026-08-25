'use client';

import { useState, type ReactNode } from 'react';
import { DonationPanel } from './DonationPanel';
import { StickyBar } from './StickyBar';
import type { DonationTrackingContext } from '@/lib/donation/useGuestDonation';

interface Props {
  widgetToken: string;
  sourceSite: string | null;
  locale: string;
  campaignName: string;
  tracking: DonationTrackingContext;
  /** False in preview mode on an unpublished campaign — the form is then rendered disabled. */
  donationsEnabled?: boolean;
  /** Amount the campaign may still accept; forwarded to the donation form's collection-cap guard. */
  remainingCapacity?: number;
  children: ReactNode;
}

export function LandingClient({
  widgetToken,
  sourceSite,
  locale,
  campaignName,
  tracking,
  donationsEnabled = true,
  remainingCapacity,
  children,
}: Props) {
  const [selectedAmount, setSelectedAmount] = useState<number | undefined>(undefined);

  return (
    <>
      <div className="lp-layout">
        <main className="lp-main">{children}</main>
        <aside className="lp-sidebar">
          <div className="lp-sidebar-sticky">
            <DonationPanel
              widgetToken={widgetToken}
              sourceSite={sourceSite}
              locale={locale}
              tracking={tracking}
              disabled={!donationsEnabled}
              remainingCapacity={remainingCapacity}
              onAmountChange={setSelectedAmount}
            />
          </div>
        </aside>
      </div>
      <StickyBar campaignName={campaignName} selectedAmount={selectedAmount} />
    </>
  );
}

'use client';

import { useState, type ReactNode } from 'react';
import { DonationPanel } from './DonationPanel';
import { StickyBar } from './StickyBar';

interface Props {
  widgetToken: string;
  sourceSite: string | null;
  locale: string;
  campaignName: string;
  /** False in preview mode on an unpublished campaign — the form is then rendered disabled. */
  donationsEnabled?: boolean;
  children: ReactNode;
}

export function LandingClient({
  widgetToken,
  sourceSite,
  locale,
  campaignName,
  donationsEnabled = true,
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
              disabled={!donationsEnabled}
              onAmountChange={setSelectedAmount}
            />
          </div>
        </aside>
      </div>
      <StickyBar campaignName={campaignName} selectedAmount={selectedAmount} />
    </>
  );
}

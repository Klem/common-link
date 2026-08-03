'use client';

import { useState, type ReactNode } from 'react';
import { DonationPanel } from './DonationPanel';
import { StickyBar } from './StickyBar';

interface Props {
  widgetToken: string;
  sourceSite: string | null;
  locale: string;
  campaignName: string;
  children: ReactNode;
}

export function LandingClient({ widgetToken, sourceSite, locale, campaignName, children }: Props) {
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
              onAmountChange={setSelectedAmount}
            />
          </div>
        </aside>
      </div>
      <StickyBar campaignName={campaignName} selectedAmount={selectedAmount} />
    </>
  );
}

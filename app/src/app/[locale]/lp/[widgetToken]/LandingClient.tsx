'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { DonationPanel } from './DonationPanel';
import { StickyBar } from './StickyBar';
import type { DonationTrackingContext } from '@/lib/donation/useGuestDonation';

interface Props {
  widgetToken: string;
  /** The association's campaign page URL (`widgetAllowedOrigin`) — the post-payment return target when embedded. */
  sourceSite: string | null;
  locale: string;
  campaignName: string;
  tracking: DonationTrackingContext;
  /** False in preview mode on an unpublished campaign — the form is then rendered disabled. */
  donationsEnabled?: boolean;
  /** Amount the campaign may still accept; forwarded to the donation form's collection-cap guard. */
  remainingCapacity?: number;
  /** Validated host origin (see `resolveParentOrigin` in `page.tsx`) — forwarded to the `begin_checkout` bridge. */
  parentOrigin?: string | null;
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
  parentOrigin,
  children,
}: Props) {
  const [selectedAmount, setSelectedAmount] = useState<number | undefined>(undefined);

  // Opened directly, top-level (the "Lien direct" card, e.g. an ad's destination URL), this page
  // *is* the campaign page: the donor comes back here after payment, where `begin_checkout` fired,
  // under the same GTM and consent, and `purchase` is pushed here too (see PurchaseRelay in
  // page.tsx). Origin and path only: the query (UTM, gclid…) is not needed to come back — the
  // session lives in cookies — and would only lengthen the stored source. Embedded, or framed
  // without a CommonLink loader, the association's campaign page URL stays the return target.
  const [directPageUrl, setDirectPageUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!parentOrigin && window.top === window) {
      setDirectPageUrl(window.location.origin + window.location.pathname);
    }
  }, [parentOrigin]);

  return (
    <>
      <div className="lp-layout">
        <main className="lp-main">{children}</main>
        <aside className="lp-sidebar">
          <div className="lp-sidebar-sticky">
            <DonationPanel
              widgetToken={widgetToken}
              sourceSite={directPageUrl ?? sourceSite}
              locale={locale}
              tracking={tracking}
              disabled={!donationsEnabled}
              remainingCapacity={remainingCapacity}
              onAmountChange={setSelectedAmount}
              parentOrigin={parentOrigin}
            />
          </div>
        </aside>
      </div>
      <StickyBar campaignName={campaignName} selectedAmount={selectedAmount} />
    </>
  );
}

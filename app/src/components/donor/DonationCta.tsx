'use client';

import { useTranslations } from 'next-intl';

interface Props {
  associationName: string;
  /**
   * Absolute public donation URL, or null when the association's widget isn't currently reachable.
   * Renders nothing when null -- never a disabled button or a link that would 404/409.
   */
  donationUrl: string | null;
  className?: string;
}

/**
 * Single "Faire un don" link, reused everywhere the donor dashboard offers to donate (home,
 * "Mes associations" cards, impact gallery, recommendations).
 *
 * Always points at the *association*, never at a specific project: the public widget shows only
 * the association's current live campaign (`AssociationProfile.widgetDestinationCampaign`), which
 * may differ from the campaign this screen is about. The label says so honestly.
 */
export function DonationCta({ associationName, donationUrl, className }: Props) {
  const t = useTranslations('dashboard.donor.cta');

  if (!donationUrl) return null;

  return (
    <a
      href={donationUrl}
      target="_blank"
      rel="noopener noreferrer"
      className={className ?? 'btn btn-primary btn-sm'}
    >
      {t('donate', { name: associationName })}
    </a>
  );
}

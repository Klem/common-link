'use client';

import { useEffect } from 'react';
import { useAssociationProfile } from '@/hooks/dashboard/useAssociationProfile';
import { useMollieKycStatus } from '@/hooks/mollie/useMollieKycStatus';
import { useAccStatusStore } from '@/stores/accStatusStore';
import { deriveBankSetupStatus } from '@/lib/bankSetupStatus';

/**
 * Headless component that keeps `accStatusStore` in sync with the association profile and the
 * Mollie KYC status. Rendered once inside DashboardShell for association users so the Sidebar
 * pill, the completion card and PrePublishModal are accurate on every association page.
 *
 * The profile alone hydrates the store — an association that never connected Mollie must still see
 * its own verification state. But the bank state needs one more bit: while the
 * Mollie request is in flight `deriveBankSetupStatus` yields `NOT_CONNECTED`, which is
 * indistinguishable from "never connected". `mollieResolved` carries that distinction to the store
 * so consumers can wait instead of acting on a loading value. A failed Mollie request counts as
 * resolved — the status is then genuinely unknown and the association must not stay blocked.
 */
export function AssociationStatusSync() {
  const { profile } = useAssociationProfile();
  const { connected, broken, onboardingStatus, dashboardUrl, isLoading } = useMollieKycStatus();
  const setAccStatus = useAccStatusStore((s) => s.setAccStatus);

  useEffect(() => {
    if (profile === null) return;
    setAccStatus({
      verificationStatus: profile.verificationStatus,
      bankStatus: deriveBankSetupStatus({ connected, broken, onboardingStatus }),
      rejectionReason: profile.verificationRejectionReason,
      mollieDashboardUrl: dashboardUrl,
      mollieResolved: !isLoading,
    });
  }, [profile, connected, broken, onboardingStatus, dashboardUrl, isLoading, setAccStatus]);

  return null;
}

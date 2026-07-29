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
 * The profile alone is enough to hydrate the store: an association that never connected Mollie
 * has a null Mollie status, and must still see its own verification state correctly.
 */
export function AssociationStatusSync() {
  const { profile } = useAssociationProfile();
  const { connected, broken, onboardingStatus, dashboardUrl } = useMollieKycStatus();
  const setAccStatus = useAccStatusStore((s) => s.setAccStatus);

  useEffect(() => {
    if (profile === null) return;
    setAccStatus({
      verificationStatus: profile.verificationStatus,
      bankStatus: deriveBankSetupStatus({ connected, broken, onboardingStatus }),
      rejectionReason: profile.verificationRejectionReason,
      mollieDashboardUrl: dashboardUrl,
    });
  }, [profile, connected, broken, onboardingStatus, dashboardUrl, setAccStatus]);

  return null;
}

'use client';

import { useEffect } from 'react';
import { useAssociationProfile } from '@/hooks/dashboard/useAssociationProfile';
import { useMollieKycStatus } from '@/hooks/mollie/useMollieKycStatus';
import { useAccStatusStore } from '@/stores/accStatusStore';

/**
 * Headless component that keeps `accStatusStore` in sync with Mollie KYC status.
 * Rendered once inside DashboardShell for association users so the Sidebar pill
 * and PrePublishModal are accurate on every association page, not just the home page.
 */
export function AssociationStatusSync() {
  const { profile } = useAssociationProfile();
  const { canReceivePayments } = useMollieKycStatus();
  const setAccStatus = useAccStatusStore((s) => s.setAccStatus);

  useEffect(() => {
    if (profile !== null && canReceivePayments !== null) {
      const verified = profile.verificationStatus === 'VERIFIED';
      const bankConnected = canReceivePayments === true;
      const done = (verified ? 1 : 0) + (bankConnected ? 1 : 0);
      setAccStatus(done, 2, verified, bankConnected);
    }
  }, [profile, canReceivePayments, setAccStatus]);

  return null;
}

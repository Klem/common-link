'use client';

import { useEffect } from 'react';
import { useAssociationProfile } from '@/hooks/dashboard/useAssociationProfile';
import { usePaymentStatus } from '@/hooks/payment/usePaymentStatus';
import { useAccStatusStore } from '@/stores/accStatusStore';
import { BankSetupStatus } from '@/lib/bankSetupStatus';

/**
 * Headless component that keeps `accStatusStore` in sync with the association profile and the
 * provider-neutral payment status (`GET /api/association/payment/status`, computed server-side by the
 * association's provider). Rendered once inside DashboardShell for association users so the Sidebar
 * pill, the completion card and PrePublishModal are accurate on every association page.
 *
 * The profile alone hydrates the store — an association that never connected a provider must still
 * see its own verification state. But the bank state needs one more bit: while the status request
 * is in flight the bank status defaults to `NOT_CONNECTED`, which is indistinguishable from "never
 * connected". `bankResolved` carries that distinction to the store so consumers can wait instead of
 * acting on a loading value. A failed request counts as resolved — the status is then genuinely
 * unknown and the association must not stay blocked.
 *
 * It also pushes the HelloAsso link details (`helloasso`) and exposes the hook's `refresh` as
 * `refreshPaymentStatus`, so the HelloAsso journey re-reads through this single status hook.
 */
export function AssociationStatusSync() {
  const { profile } = useAssociationProfile();
  const { provider, status, actionUrl, helloasso, isLoading, refresh } = usePaymentStatus();
  const setAccStatus = useAccStatusStore((s) => s.setAccStatus);
  const setPaymentStatusRefresh = useAccStatusStore((s) => s.setPaymentStatusRefresh);

  useEffect(() => {
    if (profile === null) return;
    setAccStatus({
      verificationStatus: profile.verificationStatus,
      bankStatus: status ?? BankSetupStatus.NOT_CONNECTED,
      rejectionReason: profile.verificationRejectionReason,
      bankActionUrl: actionUrl,
      bankResolved: !isLoading,
      paymentProvider: provider,
      helloasso: helloasso ?? null,
    });
  }, [profile, provider, status, actionUrl, helloasso, isLoading, setAccStatus]);

  // The HelloAsso journey re-reads the status after linking, disconnecting or checking eligibility.
  useEffect(() => {
    setPaymentStatusRefresh(refresh);
  }, [refresh, setPaymentStatusRefresh]);

  return null;
}

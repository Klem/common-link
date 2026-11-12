'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { disconnect, markOnboardingStarted } from '@/lib/api/helloasso';
import { useAccStatusStore } from '@/stores/accStatusStore';
import { useToastStore } from '@/stores/toastStore';
import { HelloAssoLink, HelloAssoWebhookState, KeyCheck } from '@/types/helloasso';
import type { AssociationProfileDto } from '@/types/association';
import {
  HelloAssoFlowView,
  HelloAssoPath,
  HelloAssoScreen,
  canCollectDonations,
  currentScreen,
  faqVisible,
  formatClock,
  fundraisingPageUrl,
  isSuspended,
  resolveFlowView,
  stepperItems,
} from './helloAssoFlow';
import HelloAssoStepper from './HelloAssoStepper';
import { BrokenState, HelpCard, LoadingState } from './FlowStates';
import WelcomeScreen from './WelcomeScreen';
import OpenAccountScreen from './OpenAccountScreen';
import LinkScreen from './LinkScreen';
import ReviewScreen from './ReviewScreen';
import ActiveScreen from './ActiveScreen';
import DisconnectModal from './DisconnectModal';
import HelloAssoFaq from './HelloAssoFaq';
import type { HelloAssoFaqHandle } from './HelloAssoFaq';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

interface HelloAssoOnboardingFlowProps {
  /** Association profile (name for the "several associations" hint, widget token for the test donation). */
  profile: AssociationProfileDto | null | undefined;
}

/**
 * Content of the "Encaissement des dons" tab for an association whose payment provider is HelloAsso (brief
 * `.tasks/handoff-front-helloasso-v2.md`, mockup `app/minisite/onboarding-helloasso-v2.html`).
 *
 * The link state is the `helloasso` field of the neutral payment status, read once dashboard-wide by
 * `AssociationStatusSync` and re-read through the store's `refreshPaymentStatus` after linking, disconnecting,
 * checking eligibility, and when the user comes back to the page while linked but not collecting (the server reads
 * eligibility again at most once a minute). Views: loading skeleton, "accès à renouveler" (BROKEN), or the flow —
 * stepper, "Statut vérifié à HH:MM", one screen (0 / 3 / 4 chosen locally while not linked; 5 / 6 derived from the
 * server once linked), the shared FAQ (0, 5, 6) and the help card. Nothing here unlocks donations: the server does.
 */
export default function HelloAssoOnboardingFlow({ profile }: HelloAssoOnboardingFlowProps) {
  const t = useTranslations('settings.helloasso.onboarding');
  const locale = useLocale();
  const status = useAccStatusStore((s) => s.helloasso);
  const resolved = useAccStatusStore((s) => s.bankResolved);
  const refreshPaymentStatus = useAccStatusStore((s) => s.refreshPaymentStatus);
  const addToast = useToastStore((s) => s.addToast);

  const [chosen, setChosen] = useState<HelloAssoScreen>(HelloAssoScreen.WELCOME);
  const [path, setPath] = useState<HelloAssoPath | null>(null);
  const [replacing, setReplacing] = useState(false);
  const [justLinked, setJustLinked] = useState(false);
  const [keyCheck, setKeyCheck] = useState<KeyCheck>(KeyCheck.IDLE);
  const [disconnectOpen, setDisconnectOpen] = useState(false);
  const [disconnecting, setDisconnecting] = useState(false);
  const disconnectButton = useRef<HTMLButtonElement>(null);
  const faq = useRef<HelloAssoFaqHandle>(null);

  const view = resolveFlowView(resolved, status, replacing);
  const screen = replacing ? HelloAssoScreen.LINK : currentScreen(status, chosen);
  const linked = status?.link === HelloAssoLink.OK;
  const collecting = canCollectDonations(status);
  const suspended = isSuspended(status);

  // Back from HelloAsso (focus / tab visible) while linked and not collecting: a silent re-read of our status, which
  // re-checks eligibility server-side (at most once a minute). Never the rate-limited POST, which would answer 429.
  useEffect(() => {
    if (!linked || collecting) return;
    const reread = () => {
      if (document.visibilityState === 'visible') void refreshPaymentStatus({ silent: true });
    };
    window.addEventListener('focus', reread);
    document.addEventListener('visibilitychange', reread);
    return () => {
      window.removeEventListener('focus', reread);
      document.removeEventListener('visibilitychange', reread);
    };
  }, [linked, collecting, refreshPaymentStatus]);

  const goTo = (next: HelloAssoScreen) => {
    setChosen(next);
    if (typeof window !== 'undefined') window.scrollTo?.({ top: 0, behavior: 'smooth' });
  };

  const startNew = () => {
    setPath(HelloAssoPath.NEW);
    goTo(HelloAssoScreen.OPEN_ACCOUNT);
    // D+1 / D+7 reminders; best effort, the journey goes on regardless.
    markOnboardingStarted().catch(() => undefined);
  };

  const startExisting = () => {
    setPath(HelloAssoPath.EXISTING);
    goTo(HelloAssoScreen.LINK);
  };

  const replaceKey = () => {
    setReplacing(true);
    setJustLinked(false);
    if (typeof window !== 'undefined') window.scrollTo?.({ top: 0, behavior: 'smooth' });
  };

  const backFromLink = () => {
    if (replacing) {
      setReplacing(false);
      return;
    }
    goTo(path === HelloAssoPath.EXISTING ? HelloAssoScreen.WELCOME : HelloAssoScreen.OPEN_ACCOUNT);
  };

  // The status is re-read BEFORE leaving the replacement mode: with the old BROKEN status still in the store, leaving it
  // first would flash the "accès à renouveler" view until the re-read lands.
  const onLinked = useCallback(async () => {
    try {
      await refreshPaymentStatus();
    } finally {
      setReplacing(false);
      setJustLinked(true);
      setKeyCheck(KeyCheck.IDLE);
    }
    if (typeof window !== 'undefined') window.scrollTo?.({ top: 0, behavior: 'smooth' });
  }, [refreshPaymentStatus]);

  const closeDisconnect = useCallback(() => {
    setDisconnectOpen(false);
    // Focus goes back to the button that opened the modal.
    setTimeout(() => disconnectButton.current?.focus(), 0);
  }, []);

  const confirmDisconnect = async () => {
    setDisconnecting(true);
    try {
      await disconnect();
      setDisconnectOpen(false);
      setJustLinked(false);
      setPath(null);
      setChosen(HelloAssoScreen.WELCOME);
      addToast('success', 'helloasso.disconnected');
      await refreshPaymentStatus();
    } catch {
      // The global interceptor already toasted the failure; the modal stays open to retry.
    } finally {
      setDisconnecting(false);
    }
  };

  if (view === HelloAssoFlowView.LOADING) {
    return <div className="hao-flow"><LoadingState /></div>;
  }

  if (view === HelloAssoFlowView.BROKEN) {
    return (
      <div className="hao-flow">
        <BrokenState onReplaceKey={replaceKey} />
        <HelpCard />
      </div>
    );
  }

  const checkedAt = formatClock(status?.lastEligibilityCheckAt, locale);
  const wipedAt = formatClock(status?.secretWipedAt, locale);

  return (
    <div className="hao-flow">
      <HelloAssoStepper items={stepperItems(screen, path, status, keyCheck)} />
      {checkedAt && (
        <p className="small muted hao-refresh-info" aria-live="polite">{t('checkedAt', { time: checkedAt })}</p>
      )}

      {justLinked && linked && (
        <div className="alert alert-success hao-linked-banner" role="status">
          <span className="alert-icon" aria-hidden="true">✅</span>
          <div className="hao-grow">{t.rich('linkedBanner', { strong, time: wipedAt ?? '' })}</div>
        </div>
      )}

      {screen === HelloAssoScreen.WELCOME && <WelcomeScreen onExisting={startExisting} onNew={startNew} />}
      {screen === HelloAssoScreen.OPEN_ACCOUNT && (
        <OpenAccountScreen onBack={() => goTo(HelloAssoScreen.WELCOME)} onContinue={() => goTo(HelloAssoScreen.LINK)} />
      )}
      {screen === HelloAssoScreen.LINK && (
        <LinkScreen
          replacing={replacing}
          notificationUrl={status?.notificationUrl ?? null}
          associationName={profile?.name ?? null}
          onBack={backFromLink}
          onLinked={onLinked}
          onKeyCheckChange={setKeyCheck}
        />
      )}
      {screen === HelloAssoScreen.REVIEW && (
        <ReviewScreen
          organizationSlug={status?.organizationSlug ?? null}
          lastEligibilityCheckAt={status?.lastEligibilityCheckAt ?? null}
          onRefreshed={() => refreshPaymentStatus()}
        />
      )}
      {screen === HelloAssoScreen.ACTIVE && (
        <ActiveScreen
          ref={disconnectButton}
          collecting={collecting}
          suspended={suspended}
          organizationSlug={status?.organizationSlug ?? null}
          webhook={status?.webhook ?? HelloAssoWebhookState.UNKNOWN}
          notificationUrl={status?.notificationUrl ?? null}
          fundraisingUrl={fundraisingPageUrl(profile)}
          onAskDisconnect={() => setDisconnectOpen(true)}
          onLearnMore={() => faq.current?.reveal('payoutMode')}
        />
      )}

      {faqVisible(screen) && (
        <HelloAssoFaq
          ref={faq}
          beforeStart={screen === HelloAssoScreen.WELCOME}
          status={status}
          onReplaceKey={replaceKey}
        />
      )}
      <HelpCard />

      {disconnectOpen && (
        <DisconnectModal
          secretWipedAt={status?.secretWipedAt ?? null}
          busy={disconnecting}
          onCancel={closeDisconnect}
          onConfirm={confirmDisconnect}
        />
      )}
    </div>
  );
}

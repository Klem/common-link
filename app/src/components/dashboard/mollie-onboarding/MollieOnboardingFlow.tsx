'use client';

import { useMemo, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import type { UseMollieKycStatusReturn } from '@/hooks/mollie/useMollieKycStatus';
import { attestMollieSelfCompletion } from '@/lib/api/mollie-connect';
import type { AssociationProfileDto } from '@/types/association';
import { MollieOnboardingStatus } from '@/types/mollie-connect';
import { buildKitRows, onboardingPeople } from './documentKit';
import { FlowNotice, FlowView, OnboardingScreen, SCREEN_TO_STEP, flowNotice, resolveFlowView } from './onboardingFlow';
import OnboardingStepper from './OnboardingStepper';
import WelcomeScreen from './WelcomeScreen';
import StructureScreen from './StructureScreen';
import KitScreen from './KitScreen';
import FillScreen from './FillScreen';
import TestPaymentScreen from './TestPaymentScreen';
import ReviewScreen from './ReviewScreen';
import ActiveScreen from './ActiveScreen';
import type { CollectionTab } from './ActiveScreen';
import { BrokenState, HelpCard, LoadingState } from './FlowStates';
import { useMollieOnboardingContext } from './useMollieOnboardingContext';
import { useBankTabAutoRefresh } from './useBankTabAutoRefresh';

/** Current RNA format: `W` followed by 9 alphanumerics (Corsican départements use `W2A`/`W2B`). */
const RNA_PATTERN = /^W[0-9A-Z]{9}$/i;

interface MollieOnboardingFlowProps {
  /** Mollie connection state, from the page's `useMollieKycStatus` (never re-instantiated here). */
  kyc: UseMollieKycStatusReturn;
  profile: AssociationProfileDto | null | undefined;
  /** The page's collection-lock predicate, passed through unchanged (tab lock and badge invariant). */
  bankDone: boolean;
  /** The OAuth popup was closed before completion. */
  interrupted: boolean;
  /** Starts a first Mollie connection; the page checks contact email and name (toasts) before opening the modal. */
  onConnect: () => void;
  /** Re-opens the connection modal after a broken connection. */
  onReconnect: () => void;
  /** Retries after an interrupted connection. */
  onRetryConnect: () => void;
  /** Opens the "Why Mollie?" modal, from the "En savoir plus" link under the FAQ of screen 0 (R10). */
  onLearnMore?: () => void;
  /** DEV/STAGING only — simulates a Mollie KYC validation. */
  onForceComplete: () => void;
  /** Switches the page to the Widget or Landing tab (only offered when `bankDone`). */
  onOpenTab: (tab: CollectionTab) => void;
}

/**
 * Content of the "Bank account" tab: the guided Mollie onboarding flow of the validated mockup
 * (`app/minisite/onboarding-mollie.html`).
 *
 * The view comes from the server status ([resolveFlowView]): loading skeleton, broken connection,
 * local screens 0-4 (no onboarding yet or NEEDS_DATA), screen 5 (in review / complement requested)
 * and screen 6 (active, or COMPLETED without payments). Navigation between screens 0-4 is local.
 *
 * While mounted (i.e. while the bank tab is shown), the status is refreshed on return to the tab
 * and every 60 s ([useBankTabAutoRefresh]); "Statut actualisé à HH:MM" sits in the flow header
 * (Q4). A Mollie connection is never started from here once one exists: `onConnect` is only called
 * when not connected.
 *
 * The flow never unlocks anything: `bankDone`, the tab lock and the badge stay computed by the page;
 * the self-completion attestation and the test-payment acknowledgement change no lock.
 */
export default function MollieOnboardingFlow({
  kyc,
  profile,
  bankDone,
  interrupted,
  onConnect,
  onReconnect,
  onRetryConnect,
  onLearnMore,
  onForceComplete,
  onOpenTab,
}: MollieOnboardingFlowProps) {
  const tM = useTranslations('settings');
  const tO = useTranslations('settings.mollie.onboarding');
  const locale = useLocale();
  const [screen, setScreen] = useState<OnboardingScreen>(OnboardingScreen.WELCOME);
  const [ticked, setTicked] = useState<Record<string, boolean>>({});
  const [testAck, setTestAck] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [attestedAt, setAttestedAt] = useState<string | null>(null);
  const [attesting, setAttesting] = useState(false);
  const [attestError, setAttestError] = useState(false);

  useBankTabAutoRefresh(kyc.refresh, kyc.forceRefresh);

  const flowState = {
    isLoading: kyc.isLoading,
    connected: kyc.connected,
    pending: kyc.pending,
    broken: kyc.broken,
    onboardingStatus: kyc.onboardingStatus,
    canReceivePayments: kyc.canReceivePayments,
    complementRequested: kyc.complementRequested,
    bankDone,
    interrupted,
  };
  const view = resolveFlowView(flowState);
  const notice = view === FlowView.FLOW ? flowNotice(flowState) : null;
  const connected = kyc.connected === true;
  const connectedNeedsData = connected && kyc.onboardingStatus === MollieOnboardingStatus.NEEDS_DATA;
  const isActiveView = view === FlowView.ACTIVE || view === FlowView.ACTIVE_NO_PAYMENTS;

  const { context, isLoading: contextLoading, error: contextError, reload } = useMollieOnboardingContext(
    view === FlowView.FLOW || isActiveView,
  );
  const people = useMemo(() => onboardingPeople(context?.beneficialOwners ?? []), [context]);
  const rows = useMemo(() => buildKitRows(context), [context]);

  const identifier = profile?.identifier?.trim() ?? '';
  const rna = RNA_PATTERN.test(identifier) ? identifier.toUpperCase() : null;
  const siren = context?.siren ?? profile?.siren ?? null;
  const openDisabled = connected ? !kyc.dashboardUrl : notice === FlowNotice.PENDING;

  const goTo = (next: OnboardingScreen) => {
    setScreen(next);
    if (typeof window !== 'undefined') window.scrollTo?.({ top: 0, behavior: 'smooth' });
  };

  /** Opens the Mollie hosted onboarding in a new tab (no window handle: refresh on return, R6). */
  const openHostedOnboarding = () => {
    if (kyc.dashboardUrl) window.open(kyc.dashboardUrl, '_blank', 'noopener,noreferrer');
  };

  /**
   * Opens Mollie, then shows screen 3 next to it: the hosted onboarding when a connection exists
   * (never a second connection), otherwise the first connection through the page (contact toasts).
   */
  const openMollie = () => {
    if (connected) {
      if (!kyc.dashboardUrl) return;
      openHostedOnboarding();
    } else {
      onConnect();
    }
    goTo(OnboardingScreen.FILL);
  };

  const forceRefresh = async () => {
    setRefreshing(true);
    try {
      await kyc.forceRefresh();
    } finally {
      setRefreshing(false);
    }
  };

  const attest = async () => {
    setAttesting(true);
    setAttestError(false);
    try {
      setAttestedAt((await attestMollieSelfCompletion()).attestedAt);
    } catch {
      setAttestError(true);
    } finally {
      setAttesting(false);
    }
  };

  const devForceCompleteButton = kyc.canForceComplete && kyc.connected && kyc.onboardingStatus !== 'COMPLETED' && (
    <button type="button" onClick={onForceComplete} className="btn btn-ghost btn-sm mt-3">
      {tM('mollie.devForceComplete')}
    </button>
  );

  const refreshedAt = kyc.lastUpdatedAt && (
    <p className="small muted mob-refresh-info" aria-live="polite">
      {tO('refreshedAt', {
        time: kyc.lastUpdatedAt.toLocaleTimeString(locale === 'fr' ? 'fr-FR' : 'en-GB', { hour: '2-digit', minute: '2-digit' }),
      })}
    </p>
  );

  if (view === FlowView.LOADING) {
    return (
      <div className="mob-flow">
        <LoadingState />
        {devForceCompleteButton}
      </div>
    );
  }

  if (view === FlowView.BROKEN) {
    return (
      <div className="mob-flow">
        {refreshedAt}
        <BrokenState
          onReconnect={() => {
            // Q5: after any connection, the association lands on screen 3.
            setScreen(OnboardingScreen.FILL);
            onReconnect();
          }}
        />
        {devForceCompleteButton}
      </div>
    );
  }

  const contextSlot = contextLoading ? (
    <div className="mollie-spinner" />
  ) : contextError ? (
    <div className="alert alert-error mob-alert-bottom" role="alert">
      <span className="alert-icon" aria-hidden="true">⚠️</span>
      <div className="mob-warn-body">
        <div>{tO('contextError')}</div>
        <div className="row mob-warn-actions">
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => void reload()}>
            {tO('retry')}
          </button>
        </div>
      </div>
    </div>
  ) : undefined;

  const stepper =
    view === FlowView.REVIEW ? (
      <OnboardingStepper step={SCREEN_TO_STEP[OnboardingScreen.REVIEW]} warn={kyc.complementRequested === true} />
    ) : view === FlowView.ACTIVE ? (
      <OnboardingStepper step={SCREEN_TO_STEP[OnboardingScreen.ACTIVE]} done />
    ) : view === FlowView.ACTIVE_NO_PAYMENTS ? (
      <OnboardingStepper step={SCREEN_TO_STEP[OnboardingScreen.ACTIVE]} warn />
    ) : (
      <OnboardingStepper step={SCREEN_TO_STEP[screen]} />
    );

  return (
    <div className="mob-flow">
      {refreshedAt}
      {stepper}

      {notice && (
        <div className={`alert ${notice === FlowNotice.INTERRUPTED ? 'alert-warning' : 'alert-info'} mob-alert-bottom`} role="status">
          <span className="alert-icon" aria-hidden="true">{notice === FlowNotice.INTERRUPTED ? '⚠️' : '⏳'}</span>
          <div className="mob-warn-body">
            <div>{tO(`notices.${notice}`)}</div>
            {notice === FlowNotice.INTERRUPTED && (
              <div className="row mob-warn-actions">
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => {
                    setScreen(OnboardingScreen.FILL);
                    onRetryConnect();
                  }}
                >
                  {tO('notices.retry')}
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {view === FlowView.FLOW && screen === OnboardingScreen.WELCOME && (
        <WelcomeScreen
          resumeUrl={connectedNeedsData ? kyc.dashboardUrl : null}
          needsData={connectedNeedsData}
          onStart={() => goTo(OnboardingScreen.STRUCTURE)}
          onResume={() => goTo(OnboardingScreen.FILL)}
          onLearnMore={onLearnMore}
        />
      )}
      {view === FlowView.FLOW && screen === OnboardingScreen.STRUCTURE && (
        <StructureScreen
          context={context}
          people={people}
          onBack={() => goTo(OnboardingScreen.WELCOME)}
          onContinue={() => goTo(OnboardingScreen.KIT)}
          contextErrorSlot={contextSlot}
        />
      )}
      {view === FlowView.FLOW && screen === OnboardingScreen.KIT && (
        <KitScreen
          context={context}
          rows={rows}
          ticked={ticked}
          onToggle={(rowId, checked) => setTicked((prev) => ({ ...prev, [rowId]: checked }))}
          associationName={profile?.name?.trim() || null}
          rna={rna}
          onBack={() => goTo(OnboardingScreen.STRUCTURE)}
          onOpenMollie={openMollie}
          openDisabled={openDisabled}
          contextErrorSlot={contextSlot}
        />
      )}
      {view === FlowView.FLOW && screen === OnboardingScreen.FILL && (
        <FillScreen
          connected={connected}
          needsData={connectedNeedsData}
          profile={profile}
          siren={siren}
          people={people}
          onOpenMollie={openMollie}
          openDisabled={openDisabled}
          onRefresh={() => void forceRefresh()}
          refreshing={refreshing}
          onBack={() => goTo(OnboardingScreen.KIT)}
          onNext={() => goTo(OnboardingScreen.TEST_PAYMENT)}
        />
      )}
      {view === FlowView.FLOW && screen === OnboardingScreen.TEST_PAYMENT && (
        <TestPaymentScreen
          dashboardUrl={connected ? kyc.dashboardUrl : null}
          acknowledged={testAck}
          onAcknowledge={() => setTestAck(true)}
          onBack={() => goTo(OnboardingScreen.FILL)}
        />
      )}
      {view === FlowView.REVIEW && (
        <ReviewScreen
          complementRequested={kyc.complementRequested === true}
          testAcknowledged={testAck}
          dashboardUrl={kyc.dashboardUrl}
          onCompleteFile={openHostedOnboarding}
        />
      )}
      {isActiveView && (
        <ActiveScreen
          collecting={view === FlowView.ACTIVE}
          attestedAt={attestedAt ?? context?.selfCompletionAttestedAt ?? null}
          attestBusy={attesting || contextLoading}
          attestError={attestError}
          onAttest={() => void attest()}
          onOpenTab={onOpenTab}
        />
      )}

      <HelpCard />
      {devForceCompleteButton}
    </div>
  );
}

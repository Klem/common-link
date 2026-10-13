'use client';

import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import MollieOnboardingGuide from '@/components/dashboard/MollieOnboardingGuide';
import type { AssociationProfileDto } from '@/types/association';
import type { OnboardingBeneficialOwner } from '@/types/mollie-connect';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

interface FillScreenProps {
  /** A Mollie connection exists: the hosted onboarding can be (re)opened and refreshed. */
  connected: boolean;
  /** Shows the "Dossier à compléter" badge (connected, NEEDS_DATA). */
  needsData: boolean;
  profile: AssociationProfileDto | null | undefined;
  /** SIREN from the onboarding context, falling back to the profile one. */
  siren: string | null;
  /** People to declare as stakeholders (see `onboardingPeople`). */
  people: OnboardingBeneficialOwner[];
  /**
   * Opens Mollie: the hosted onboarding (`dashboardUrl`) when connected, otherwise the first
   * connection (`MollieOnboardModal`, contact toasts handled by the page).
   */
  onOpenMollie: () => void;
  /** Disables the open action (connected without a hosted onboarding link, or authorization pending). */
  openDisabled: boolean;
  /** Forces a status refresh (`POST /api/mollie/connect/status/refresh`). */
  onRefresh: () => void;
  refreshing: boolean;
  onBack: () => void;
  onNext: () => void;
}

/**
 * Screen 3 "Remplir chez Mollie" (mockup `screen-3`): the Mollie window panel (open / reopen /
 * refresh) next to the 10-step translator of Mollie's hosted wizard.
 *
 * There is no automatic return from Mollie: the tab refreshes the status when the association comes
 * back to it (visibility / focus), periodically, and on the explicit "refresh" button.
 */
export default function FillScreen({
  connected,
  needsData,
  profile,
  siren,
  people,
  onOpenMollie,
  openDisabled,
  onRefresh,
  refreshing,
  onBack,
  onNext,
}: FillScreenProps) {
  const t = useTranslations('settings.mollie.onboarding.fill');

  return (
    <section className="screen visible" aria-labelledby="mob-s3-title">
      <div className="row-between mob-title-row">
        <h2 id="mob-s3-title" className="mob-kit-title">{t('title')}</h2>
        {needsData && <span className="badge badge-warning">{t('badge')}</span>}
      </div>
      <p className="lead mob-lead-spaced">{t.rich('lead', { strong })}</p>

      <div className="translator">
        <aside className="mollie-window" aria-label={t('window.aria')}>
          <div className="mw-bar">
            <span className="mw-dots" aria-hidden="true"><i /><i /><i /></span>
            <span className="mw-url">
              <span aria-hidden="true">🔒 </span>
              <b>{t('window.urlHost')}</b>
              {t('window.urlPath')}
            </span>
          </div>
          <div className="mw-body">
            <div className="empty-state-icon" aria-hidden="true">🪟</div>
            <h4 className="mob-mw-title">{connected ? t('window.openedTitle') : t('window.notOpenedTitle')}</h4>
            <p className="small muted mob-mw-text">{connected ? t('window.openedText') : t('window.notOpenedText')}</p>
            <div className="alert alert-info small mob-mw-alert">
              <span className="alert-icon" aria-hidden="true">🔒</span>
              <div>{t.rich('window.checkAddress', { strong })}</div>
            </div>
            <div className="mob-mw-actions">
              <button type="button" className="btn btn-secondary btn-sm" onClick={onOpenMollie} disabled={openDisabled}>
                {connected ? t('window.reopen') : t('window.open')}
              </button>
              {connected && (
                <button type="button" className="btn btn-tertiary btn-sm" onClick={onRefresh} disabled={refreshing}>
                  {refreshing ? t('window.refreshing') : t('window.refresh')}
                </button>
              )}
            </div>
          </div>
        </aside>

        <div>
          <MollieOnboardingGuide
            name={profile?.name}
            addressLine1={profile?.addressLine1}
            postalCode={profile?.postalCode}
            city={profile?.city}
            siren={siren}
            legalObject={profile?.legalObject}
            contactName={profile?.contactName}
            contactEmail={profile?.contactEmail}
            people={people}
          />
        </div>
      </div>

      <div className="screen-foot">
        <button type="button" className="btn btn-ghost" onClick={onBack}>
          <span aria-hidden="true">←</span> {t('back')}
        </button>
        <button type="button" className="btn btn-primary" onClick={onNext}>
          {t('next')}
        </button>
      </div>
    </section>
  );
}

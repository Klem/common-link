'use client';

import { forwardRef } from 'react';
import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { HelloAssoWebhookState } from '@/types/helloasso';
import { CopyRow, PathCrumbs } from './FlowStates';
import { helloAssoVisioUrl, verificationUrl } from './helloAssoFlow';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

const WEBHOOK_BADGE: Record<HelloAssoWebhookState, string> = {
  [HelloAssoWebhookState.UNKNOWN]: 'badge-info',
  [HelloAssoWebhookState.RECEIVED]: 'badge-success',
  [HelloAssoWebhookState.MISSING]: 'badge-error',
};

interface ActiveScreenProps {
  /** Linked and HelloAsso accepts payments. */
  collecting: boolean;
  /** Linked, HelloAsso no longer accepts payments although it did. */
  suspended: boolean;
  organizationSlug: string | null;
  webhook: HelloAssoWebhookState;
  notificationUrl: string | null;
  /**
   * Fundraising page the test donation opens (`/fr/lp/{widgetToken}`, French-only like `landing.js`); null while the
   * association has no widget token or no destination campaign (the page would only show an error).
   */
  fundraisingUrl: string | null;
  /** Opens the disconnect modal; the button keeps focus return through the forwarded ref. */
  onAskDisconnect: () => void;
}

/**
 * Screen 6 (mockup `screen-6`): "Votre association peut recevoir des dons" with "Se déconnecter de HelloAsso", or the
 * "Encaissement suspendu par HelloAsso" alert, then the test-donation card whose badge follows the notification
 * health-check (`UNKNOWN` advised · `RECEIVED` verified · `MISSING` to fix, `role=alert`). The ref is the disconnect
 * button, to give focus back when the modal closes.
 */
const ActiveScreen = forwardRef<HTMLButtonElement, ActiveScreenProps>(function ActiveScreen(
  { collecting, suspended, organizationSlug, webhook, notificationUrl, fundraisingUrl, onAskDisconnect },
  disconnectRef,
) {
  const t = useTranslations('settings.helloasso.onboarding.active');
  const tLink = useTranslations('settings.helloasso.onboarding.link');
  const tReview = useTranslations('settings.helloasso.onboarding.review');
  const visioUrl = helloAssoVisioUrl();
  const crumbs = [tLink('crumbs.account'), tLink('crumbs.api')];

  const testButton = (label: string) =>
    fundraisingUrl ? (
      <a className="btn btn-primary btn-sm" href={fundraisingUrl} target="_blank" rel="noopener noreferrer">
        {label}
      </a>
    ) : (
      <span className="small muted">{t('test.noPage')}</span>
    );

  return (
    <section className="screen visible" aria-labelledby={collecting ? 'hao-s6-title' : undefined}>
      {collecting && (
        <div className="card card-no-hover">
          <div className="card-body">
            <div className="empty-state hao-active-state">
              <div className="empty-state-icon" aria-hidden="true">🎉</div>
              <h4 id="hao-s6-title">{t('title')}</h4>
              <p>{t('text')}</p>
              <span className="badge badge-success">{t('badge')}</span>
              <div className="hao-row-spaced-lg">
                <button
                  ref={disconnectRef}
                  type="button"
                  className="btn btn-ghost btn-sm"
                  aria-haspopup="dialog"
                  onClick={onAskDisconnect}
                >
                  {t('disconnect')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {suspended && (
        <div className="alert alert-error" role="status">
          <span className="alert-icon" aria-hidden="true">⏸️</span>
          <div className="hao-grow">
            {t.rich('suspended.text', { strong })}
            <div className="small hao-mismatch-hint">{t('suspended.hint')}</div>
            <div className="row hao-row-spaced">
              {organizationSlug && (
                <a
                  className="btn btn-primary btn-sm"
                  href={verificationUrl(organizationSlug)}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {tReview('openVerification')}
                </a>
              )}
              {visioUrl && (
                <a className="btn btn-ghost btn-sm" href={visioUrl} target="_blank" rel="noopener noreferrer">
                  {t('suspended.contact')}
                </a>
              )}
              <button
                ref={collecting ? undefined : disconnectRef}
                type="button"
                className="btn btn-ghost btn-sm"
                aria-haspopup="dialog"
                onClick={onAskDisconnect}
              >
                {t('disconnect')}
              </button>
            </div>
          </div>
        </div>
      )}

      {collecting && (
        <div className="card card-no-hover hao-card-spaced">
          <div className="card-header-bar">
            <h3>{t('test.title')}</h3>
            <span className={`badge ${WEBHOOK_BADGE[webhook]}`}>{t(`test.badge.${webhook}`)}</span>
          </div>
          <div className="card-body">
            {webhook === HelloAssoWebhookState.UNKNOWN && (
              <div>
                <p className="small">{t.rich('test.unknownText', { strong })}</p>
                <p className="form-hint">{t('test.contribution')}</p>
                <div className="row hao-row-spaced">
                  {testButton(t('test.cta'))}
                  <span className="small muted">{t('test.ctaAlt')}</span>
                </div>
              </div>
            )}
            {webhook === HelloAssoWebhookState.RECEIVED && (
              <div className="alert alert-success">
                <span className="alert-icon" aria-hidden="true">✅</span>
                <div>{t.rich('test.received', { strong })}</div>
              </div>
            )}
            {webhook === HelloAssoWebhookState.MISSING && (
              <div>
                <div className="alert alert-error" role="alert">
                  <span className="alert-icon" aria-hidden="true">📭</span>
                  <div className="hao-grow">
                    {t.rich('test.missing', { strong })}
                    <div className="small hao-mismatch-hint">{t('test.missingImpact')}</div>
                  </div>
                </div>
                <p className="small hao-row-spaced">
                  {t('test.missingFixBefore')} <PathCrumbs label={tLink('crumbs.label')} steps={crumbs} />
                  {t('test.missingFixAfter')}
                </p>
                {notificationUrl && (
                  <CopyRow
                    className="hao-copy-row-spaced"
                    label={tLink('step3.addressLabel')}
                    value={notificationUrl}
                    copyAria={tLink('step3.copyAria')}
                    copyLabel={tLink('step3.copy')}
                    copiedLabel={tLink('step3.copied')}
                  />
                )}
                <div className="row hao-row-spaced">
                  {testButton(t('test.retry'))}
                  {visioUrl && (
                    <a className="btn btn-ghost btn-sm" href={visioUrl} target="_blank" rel="noopener noreferrer">
                      {t('test.visio')}
                    </a>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
});

export default ActiveScreen;

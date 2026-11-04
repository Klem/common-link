'use client';

import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { ensureNotificationUrl, helloAssoErrorCode, linkAccount, verifyCredentials } from '@/lib/api/helloasso';
import { HelloAssoErrorCode, KeyCheck } from '@/types/helloasso';
import type { HelloAssoVerifyResponse } from '@/types/helloasso';
import {
  HELLOASSO_ADMIN_URL,
  HELLOASSO_MANDATE_VERSION,
  KEY_CHECK_DEBOUNCE_MS,
  KEY_MAX_LENGTH,
  helloAssoVisioUrl,
  keysReady,
  linkMissing,
} from './helloAssoFlow';
import { CopyRow, PathCrumbs } from './FlowStates';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

/** Error shown under the submit button after a refused link. */
const SubmitError = {
  EXPIRED: 'expired',
  MANDATE: 'mandate',
  GENERIC: 'generic',
} as const;
type SubmitError = typeof SubmitError[keyof typeof SubmitError];

/** The two code inputs. */
const CodeField = {
  CLIENT_ID: 'CLIENT_ID',
  CLIENT_SECRET: 'CLIENT_SECRET',
} as const;
type CodeField = typeof CodeField[keyof typeof CodeField];

/** Last checked pair and its outcome, so re-typing the same codes does not spend another HelloAsso call. */
interface CheckedPair {
  pair: string;
  outcome: KeyCheck;
  result: HelloAssoVerifyResponse | null;
}

interface LinkScreenProps {
  /** Re-pasting the key after a broken link (or from "Recoller une clé"): shorter copy, notification URL pre-acked. */
  replacing: boolean;
  /** Notification URL of the status; created on open when null. */
  notificationUrl: string | null;
  /** Association name (KYB), shown in "Vous gérez plusieurs associations ?". */
  associationName: string | null;
  /** "← Retour". */
  onBack: () => void;
  /** Called after a successful link; the flow re-reads the status. */
  onLinked: () => void;
  /** Reports the key check so the stepper can warn on invalid codes / another association. */
  onKeyCheckChange: (keyCheck: KeyCheck) => void;
}

/**
 * Screen 4 "Relier votre compte HelloAsso" (mockup `screen-4`): open the keys page, paste the two codes (checked
 * live by the server: valid, and the association verified on CommonLink), paste our notification URL, accept the
 * mandate, link.
 *
 * The client secret lives only in this component's state: sent once to `credentials/verify`, never stored, logged or
 * put in a URL, and cleared after linking. A superseded check is aborted. Every rule is re-checked by the server
 * (`verifyId` valid and unexpired, organization = KYB, mandate in its current version).
 */
export default function LinkScreen({
  replacing,
  notificationUrl,
  associationName,
  onBack,
  onLinked,
  onKeyCheckChange,
}: LinkScreenProps) {
  const t = useTranslations('settings.helloasso.onboarding.link');
  const visioUrl = helloAssoVisioUrl();

  const [clientId, setClientId] = useState('');
  const [clientSecret, setClientSecret] = useState('');
  const [revealed, setRevealed] = useState(false);
  const [pasteFailed, setPasteFailed] = useState(false);
  const [keyCheck, setKeyCheck] = useState<KeyCheck>(KeyCheck.IDLE);
  const [verified, setVerified] = useState<HelloAssoVerifyResponse | null>(null);
  const [orgConfirmed, setOrgConfirmed] = useState(false);
  const [webhookAck, setWebhookAck] = useState(replacing);
  const [mandateAccepted, setMandateAccepted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<SubmitError | null>(null);
  const [url, setUrl] = useState<string | null>(notificationUrl);
  const [urlFailed, setUrlFailed] = useState(false);
  const lastChecked = useRef<CheckedPair | null>(null);

  useEffect(() => onKeyCheckChange(keyCheck), [keyCheck, onKeyCheckChange]);

  // The notification URL is created on first opening of this screen (idempotent, the token never changes).
  useEffect(() => {
    if (notificationUrl) {
      setUrl(notificationUrl);
      return;
    }
    let cancelled = false;
    ensureNotificationUrl()
      .then((value) => !cancelled && setUrl(value))
      .catch(() => !cancelled && setUrlFailed(true));
    return () => {
      cancelled = true;
    };
  }, [notificationUrl]);

  // Live key check: debounced, previous request aborted, same pair never re-sent.
  useEffect(() => {
    setOrgConfirmed(false);
    if (!keysReady(clientId, clientSecret)) {
      setKeyCheck(KeyCheck.IDLE);
      setVerified(null);
      return;
    }
    const pair = `${clientId}\n${clientSecret}`;
    if (lastChecked.current?.pair === pair) {
      setKeyCheck(lastChecked.current.outcome);
      setVerified(lastChecked.current.result);
      return;
    }
    setKeyCheck(KeyCheck.IDLE);
    setVerified(null);
    const controller = new AbortController();
    const timer = setTimeout(() => {
      setKeyCheck(KeyCheck.CHECKING);
      verifyCredentials({ clientId, clientSecret }, controller.signal)
        .then((result) => {
          const outcome = result.matchesKyb && result.verifyId ? KeyCheck.OK : KeyCheck.MISMATCH;
          lastChecked.current = { pair, outcome, result };
          setVerified(result);
          setKeyCheck(outcome);
        })
        .catch((error: unknown) => {
          // Never log `error`: its request config carries the secret.
          if (controller.signal.aborted) return;
          const code = helloAssoErrorCode(error);
          const outcome =
            code === HelloAssoErrorCode.INVALID_KEYS || code === HelloAssoErrorCode.ORGANIZATION_UNRESOLVED
              ? KeyCheck.INVALID
              : KeyCheck.UNAVAILABLE;
          // An outage is not cached: the same codes may work a minute later.
          if (outcome === KeyCheck.INVALID) lastChecked.current = { pair, outcome, result: null };
          setKeyCheck(outcome);
        });
    }, KEY_CHECK_DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [clientId, clientSecret]);

  /** A code typed or pasted by the user: trimmed, and any previous link refusal is cleared. */
  const editCode = (target: CodeField, value: string) => {
    setSubmitError(null);
    if (target === CodeField.CLIENT_ID) setClientId(value.trim());
    else setClientSecret(value.trim());
  };

  const paste = async (target: CodeField) => {
    setPasteFailed(false);
    try {
      editCode(target, await navigator.clipboard.readText());
    } catch {
      setPasteFailed(true);
    }
  };

  const missing = linkMissing({ keyCheck, verifyId: verified?.verifyId ?? null, orgConfirmed, webhookAck, mandateAccepted });
  const disabled = missing.length > 0 || submitting;

  const submit = async () => {
    if (disabled || !verified?.verifyId) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await linkAccount({ verifyId: verified.verifyId, mandate: { accepted: true, version: HELLOASSO_MANDATE_VERSION } });
      // The secret leaves the page with the link.
      setClientSecret('');
      setClientId('');
      lastChecked.current = null;
      onLinked();
    } catch (error) {
      const code = helloAssoErrorCode(error);
      if (code === HelloAssoErrorCode.VERIFICATION_EXPIRED) {
        // The handle is single-use and short-lived: the codes must be checked again.
        lastChecked.current = null;
        setClientSecret('');
        setSubmitError(SubmitError.EXPIRED);
      } else if (code === HelloAssoErrorCode.MANDATE_REQUIRED) {
        setMandateAccepted(false);
        setSubmitError(SubmitError.MANDATE);
      } else {
        setSubmitError(SubmitError.GENERIC);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const invalid = keyCheck === KeyCheck.INVALID;
  const ok = keyCheck === KeyCheck.OK;
  const inputState = invalid ? ' error' : ok ? ' success' : '';
  const crumbs = [t('crumbs.account'), t('crumbs.api')];

  return (
    <section className="screen visible" aria-labelledby="hao-s4-title">
      <div className="row-between hao-title-row-lg">
        <h2 id="hao-s4-title" className="hao-screen-title">{t('title')}</h2>
        <span className="badge badge-neutral">{t('badge')}</span>
      </div>
      <p className="lead">{t('lead')}</p>

      {replacing && (
        <div className="alert alert-info hao-alert-top">
          <span className="alert-icon" aria-hidden="true">🔁</span>
          <div>{t.rich('replaceBanner', { strong })}</div>
        </div>
      )}

      {/* Step 1 — open the keys page */}
      <div className={`link-step${clientId || ok ? ' done' : ''}`}>
        <div className="link-step-head">
          <span className="tr-num" aria-hidden="true">{clientId || ok ? '✓' : 1}</span>
          <h3>{t('step1.title')}</h3>
        </div>
        <div className="link-step-body">
          <p>
            {t('step1.where')} <PathCrumbs label={t('crumbs.label')} steps={crumbs} />
          </p>
          <div>
            <a className="btn btn-secondary btn-sm" href={HELLOASSO_ADMIN_URL} target="_blank" rel="noopener noreferrer">
              {t('step1.openAdmin')}
            </a>
          </div>
          <div>
            <details className="disclose">
              <summary>{t('step1.existingKeyQ')}</summary>
              <p>
                {t('step1.existingKeyA')} <span className="badge badge-warning">{t('toConfirm')}</span>
              </p>
            </details>
            <details className="disclose">
              <summary>{t('step1.severalQ')}</summary>
              <p>
                {associationName
                  ? t.rich('step1.severalA', { strong, name: associationName })
                  : t('step1.severalAnoName')}
              </p>
            </details>
          </div>
        </div>
      </div>

      {/* Step 2 — paste the two codes */}
      <div className={`link-step${ok && orgConfirmed ? ' done' : ''}`}>
        <div className="link-step-head">
          <span className="tr-num" aria-hidden="true">{ok && orgConfirmed ? '✓' : 2}</span>
          <h3>{t('step2.title')}</h3>
          {ok && <span className="badge badge-success">{t('step2.validBadge')}</span>}
        </div>
        <div className="link-step-body">
          <div className="form-group hao-form-group-flat">
            <label className="form-label" htmlFor="hao-client-id">
              {t('step2.clientIdLabel')} <span className="muted hao-label-hint">{t('step2.clientIdHint')}</span>
            </label>
            <div className="key-field">
              <input
                id="hao-client-id"
                className={`form-input${inputState}`}
                autoComplete="off"
                data-1p-ignore
                data-lpignore="true"
                spellCheck={false}
                maxLength={KEY_MAX_LENGTH}
                placeholder={t('step2.clientIdPlaceholder')}
                value={clientId}
                aria-invalid={invalid}
                onChange={(e) => editCode(CodeField.CLIENT_ID, e.target.value)}
              />
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                aria-label={t('step2.pasteClientIdAria')}
                onClick={() => paste(CodeField.CLIENT_ID)}
              >
                {t('step2.paste')}
              </button>
            </div>
          </div>
          <div className="form-group hao-form-group-flat">
            <label className="form-label" htmlFor="hao-client-secret">
              {t('step2.secretLabel')} <span className="muted hao-label-hint">{t('step2.secretHint')}</span>
            </label>
            <div className="key-field">
              <input
                id="hao-client-secret"
                className={`form-input${inputState}`}
                type={revealed ? 'text' : 'password'}
                // "off" is ignored by browsers on password fields: "new-password" plus the password-manager opt-outs
                // keep the secret from being offered for saving (it must never be stored client-side).
                autoComplete="new-password"
                data-1p-ignore
                data-lpignore="true"
                spellCheck={false}
                maxLength={KEY_MAX_LENGTH}
                placeholder={t('step2.secretPlaceholder')}
                value={clientSecret}
                aria-invalid={invalid}
                onChange={(e) => editCode(CodeField.CLIENT_SECRET, e.target.value)}
              />
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                aria-pressed={revealed}
                aria-controls="hao-client-secret"
                onClick={() => setRevealed((v) => !v)}
              >
                {revealed ? t('step2.hide') : t('step2.show')}
              </button>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                aria-label={t('step2.pasteSecretAria')}
                onClick={() => paste(CodeField.CLIENT_SECRET)}
              >
                {t('step2.paste')}
              </button>
            </div>
            <p className="form-hint secret-hint">
              <span aria-hidden="true">🗑️ </span>
              {t.rich('step2.secretNote', { strong })}
            </p>
            {pasteFailed && <p className="form-hint" role="status">{t('step2.pasteFailed')}</p>}
          </div>

          {keyCheck === KeyCheck.CHECKING && (
            <div className="key-result" role="status">
              <span className="spinner" aria-hidden="true" />
              <span className="small">{t('step2.checking')}</span>
            </div>
          )}

          {ok && verified && (
            <div className="key-result ok" role="status">
              <div className="avatar avatar-md avatar-teal" aria-hidden="true">🌱</div>
              <div className="hao-grow">
                <div className="small muted hao-org-label">{t('step2.okLabel')}</div>
                <div className="hao-org-name">{verified.orgName}</div>
                <div className="small hao-org-match"><span aria-hidden="true">✓ </span>{t('step2.okMatch')}</div>
              </div>
              <label className="form-checkbox">
                <input type="checkbox" checked={orgConfirmed} onChange={(e) => setOrgConfirmed(e.target.checked)} />
                {t('step2.confirm')}
              </label>
            </div>
          )}

          {invalid && (
            <div className="alert alert-error" role="alert">
              <span className="alert-icon" aria-hidden="true">✋</span>
              <div>
                <strong>{t('step2.invalidTitle')}</strong> {t('step2.nothingSaved')}
                <ul className="ol-steps small hao-tips">
                  <li>{t('step2.invalidTips.whole')}</li>
                  <li>{t('step2.invalidTips.regenerated')}</li>
                  <li>{t('step2.invalidTips.sandbox')}</li>
                </ul>
              </div>
            </div>
          )}

          {keyCheck === KeyCheck.MISMATCH && verified && (
            <div className="alert alert-warning" role="alert">
              <span className="alert-icon" aria-hidden="true">🔀</span>
              <div className="hao-grow">
                <strong>{t('step2.mismatchTitle', { orgName: verified.orgName })}</strong> {t('step2.mismatchText')}
                <div className="small hao-mismatch-hint">
                  {t('step2.mismatchHint')}
                  {visioUrl && (
                    <>
                      {' '}
                      {t.rich('step2.mismatchHelp', {
                        link: (chunks) => <a href={visioUrl} target="_blank" rel="noopener noreferrer">{chunks}</a>,
                      })}
                    </>
                  )}
                </div>
              </div>
            </div>
          )}

          {keyCheck === KeyCheck.UNAVAILABLE && (
            <div className="alert alert-warning" role="alert">
              <span className="alert-icon" aria-hidden="true">⏳</span>
              <div>{t('step2.unavailable')}</div>
            </div>
          )}
        </div>
      </div>

      {/* Step 3 — paste our notification URL */}
      <div className={`link-step${webhookAck ? ' done' : ''}`}>
        <div className="link-step-head">
          <span className="tr-num" aria-hidden="true">{webhookAck ? '✓' : 3}</span>
          <h3>{t('step3.title')}</h3>
        </div>
        <div className="link-step-body">
          <p>{t('step3.text')}</p>
          {url ? (
            <CopyRow
              label={t('step3.addressLabel')}
              value={url}
              copyAria={t('step3.copyAria')}
              copyLabel={t('step3.copy')}
              copiedLabel={t('step3.copied')}
            />
          ) : urlFailed ? (
            <div className="alert alert-warning" role="alert">
              <span className="alert-icon" aria-hidden="true">⏳</span>
              <div>{t('step3.urlUnavailable')}</div>
            </div>
          ) : (
            <p className="small muted" role="status">{t('step3.urlLoading')}</p>
          )}
          <details className="disclose">
            <summary>{t('step3.existingQ')}</summary>
            <p>
              {t('step3.existingA')} <span className="badge badge-warning">{t('toConfirm')}</span>
            </p>
          </details>
          <label className="form-checkbox">
            <input type="checkbox" checked={webhookAck} onChange={(e) => setWebhookAck(e.target.checked)} />
            {t('step3.ack')}
          </label>
        </div>
      </div>

      {/* Mandate + submit */}
      <div className="link-submit">
        <label className="form-checkbox hao-mandate">
          <input type="checkbox" checked={mandateAccepted} onChange={(e) => setMandateAccepted(e.target.checked)} />
          <span className="small">{t('mandate')}</span>
        </label>
        <div className="row-between hao-submit-row">
          <span className="small muted" id="hao-link-hint">
            {missing.length
              ? t('hint.remaining', { items: missing.map((m) => t(`hint.${m}`)).join(t('hint.separator')) })
              : t('hint.ready')}
          </span>
          <button
            type="button"
            className="btn btn-primary btn-lg"
            disabled={disabled}
            aria-describedby="hao-link-hint"
            onClick={submit}
          >
            {submitting ? t('submitting') : replacing ? t('submitReplace') : t('submit')}
          </button>
        </div>
        {submitError && (
          <div className="alert alert-error hao-alert-top" role="alert">
            <span className="alert-icon" aria-hidden="true">✋</span>
            <div>{t(`errors.${submitError}`)}</div>
          </div>
        )}
      </div>

      <div className="screen-foot">
        <button type="button" className="btn btn-ghost" onClick={onBack}>{t('back')}</button>
        {visioUrl && <span className="small muted">{t('blocked')}</span>}
      </div>
    </section>
  );
}

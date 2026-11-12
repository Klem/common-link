'use client';

import { forwardRef, useImperativeHandle, useState } from 'react';
import type { ReactNode } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { HelloAssoLink } from '@/types/helloasso';
import type { HelloAssoStatus } from '@/types/helloasso';
import { HELLOASSO_HOW_IT_WORKS_URL } from '@/lib/helloasso';
import { HELLOASSO_FEES_DECISION_PENDING, REGAFI_URL, formatClock, formatDay } from './helloAssoFlow';
import HelloAssoPayButton, { HelloAssoPayButtonVariant } from '@/components/helloasso/HelloAssoPayButton';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

interface FaqEntry {
  /** Entry id, also the i18n key prefix under `faq.`. */
  id: string;
  icon: string;
  panel: () => ReactNode;
}

interface HelloAssoFaqProps {
  /** Screen 0 wording ("Vos questions avant de commencer" + subtitle), otherwise "Vos questions". */
  beforeStart: boolean;
  /** Link details, for the "Votre accès aujourd'hui" block (shown only when linked). */
  status: HelloAssoStatus | null;
  /** "Recoller une clé" of the access block. */
  onReplaceKey: () => void;
}

/** Imperative handle of [HelloAssoFaq], for links pointing at one of its entries ("En savoir plus"). */
export interface HelloAssoFaqHandle {
  /** Opens entry [id], scrolls it into view and moves focus to its question. */
  reveal: (id: string) => void;
}

/**
 * Shared FAQ of the HelloAsso journey (mockup `faq-card`, v3.7: 13 entries in 2 groups), shown on screens 0, 5 and 6.
 * Accordions with `aria-expanded` / `aria-controls`, panels `role=region`. HelloAsso's imposed texts (presentation,
 * cart text, ACPR approval) are rendered verbatim from `settings.helloasso.imposed` — never reworded nor translated by
 * us. "Combien ça coûte ?" carries a visible "Décision en attente" placeholder until the CEO decides
 * ([HELLOASSO_FEES_DECISION_PENDING]).
 *
 * Exposes [HelloAssoFaqHandle.reveal] so another part of the journey can open one entry, e.g. the "Conseil" box of the
 * active screen opening "Quand l'argent arrive-t-il sur votre compte ?".
 */
const HelloAssoFaq = forwardRef<HelloAssoFaqHandle, HelloAssoFaqProps>(function HelloAssoFaq(
  { beforeStart, status, onReplaceKey },
  ref,
) {
  const t = useTranslations('settings.helloasso.onboarding.faq');
  const tImposed = useTranslations('settings.helloasso.imposed');
  const locale = useLocale();
  const [open, setOpen] = useState<Record<string, boolean>>({});
  const linked = status?.link === HelloAssoLink.OK;

  useImperativeHandle(ref, () => ({
    reveal: (id: string) => {
      setOpen((o) => ({ ...o, [id]: true }));
      // After the panel has rendered: bring the question on screen and give it focus (keyboard and screen readers).
      setTimeout(() => {
        const trigger = document.getElementById(`hao-faq-${id}-t`);
        trigger?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
        trigger?.focus();
      }, 0);
    },
  }), []);

  const imposed = (label: string, text: string) => (
    <div className="imposed hao-imposed-spaced">
      <div className="imposed-label">
        {label} <span className="badge badge-neutral">{t('imposedBadge')}</span>
      </div>
      <blockquote>{text}</blockquote>
    </div>
  );

  const how: FaqEntry[] = [
    {
      id: 'roles',
      icon: '🧭',
      panel: () => (
        <>
          <p>{t('roles.intro')}</p>
          <div className="role-split" role="group" aria-label={t('roles.splitLabel')}>
            <div className="role-col main">
              <h4><span aria-hidden="true">🧭</span> {t('roles.commonlink')}</h4>
              <ul>
                {(['campaigns', 'widget', 'certification', 'receipts', 'reporting'] as const).map((k) => (
                  <li key={k}>{t(`roles.commonlinkItems.${k}`)}</li>
                ))}
              </ul>
            </div>
            <div className="role-col">
              <h4><span aria-hidden="true">💳</span> {t('roles.helloasso')}</h4>
              <ul>
                {(['page', 'collection', 'verification'] as const).map((k) => (
                  <li key={k}>{t(`roles.helloassoItems.${k}`)}</li>
                ))}
              </ul>
            </div>
          </div>
        </>
      ),
    },
    {
      id: 'cert',
      icon: '✅',
      panel: () => (
        <>
          <div className="cert-rule faq-compact">
            <div className="cert-cols">
              <div className="cert-col yes">
                <strong><span aria-hidden="true">✓ </span>{t('cert.yes')}</strong>
                <div>{t('cert.yesItems.widget')}</div>
                <div>{t('cert.yesItems.page')}</div>
              </div>
              <div className="cert-col no">
                <strong><span aria-hidden="true">✗ </span>{t('cert.no')}</strong>
                <div>{t('cert.noItems.form')}</div>
                <div>{t('cert.noItems.link')}</div>
              </div>
            </div>
          </div>
          <p className="hao-row-spaced">{t('cert.text')}</p>
        </>
      ),
    },
    {
      id: 'why',
      icon: '🏦',
      panel: () => (
        <>
          <p>{t('why.intro')}</p>
          {imposed(t('why.imposedLabel'), tImposed('presentation'))}
          <p className="hao-row-spaced">{t.rich('why.contribution', { strong })}</p>
          <p>
            {t.rich('why.approvalLine', {
              approval: tImposed('approval'),
              em: (chunks) => <em>{chunks}</em>,
              link: (chunks) => <a href={REGAFI_URL} target="_blank" rel="noopener noreferrer">{chunks}</a>,
            })}
          </p>
          <p>
            <a href={HELLOASSO_HOW_IT_WORKS_URL} target="_blank" rel="noopener noreferrer">{t('why.howItWorks')}</a>
          </p>
        </>
      ),
    },
    {
      id: 'donors',
      icon: '👀',
      panel: () => (
        <>
          <p>{t('donors.intro')}</p>
          {imposed(t('donors.imposedLabel'), tImposed('cartText'))}
          <div className="hao-row-spaced-lg">
            <HelloAssoPayButton
              variant={HelloAssoPayButtonVariant.PREVIEW}
              payWith={t('donors.payWith')}
              secured={t('donors.secured')}
            />
          </div>
        </>
      ),
    },
    {
      id: 'money',
      icon: '💶',
      panel: () => (
        <>
          <p>{t.rich('money.intro', { strong })}</p>
          <div className="mini-figs">
            <MiniFig icon="💶" tone={FigTone.TEAL} value={t('money.feesValue')} label={t('money.feesLabel')} />
            <MiniFig icon="🎯" tone={FigTone.INDIGO} value={t('money.shareValue')} label={t('money.shareLabel')} />
            <MiniFig icon="🔐" tone={FigTone.AMBER} value={t('money.heldValue')} label={t('money.heldLabel')} />
          </div>
          <p className="hao-row-spaced-lg">{t('money.rhythm')}</p>
          <div className="mini-figs two">
            <MiniFig icon="👆" tone={FigTone.TEAL} value={t('money.onDemandValue')} label={t('money.onDemandLabel')} />
            <MiniFig icon="📆" tone={FigTone.INDIGO} value={t('money.monthlyValue')} label={t('money.monthlyLabel')} />
          </div>
          <p className="form-hint hao-note-spaced">{t('money.note')}</p>
        </>
      ),
    },
    {
      // CEO decision Q2: "on request" recommended, never required.
      id: 'payoutMode',
      icon: '👆',
      panel: () => (
        <>
          <p>{t.rich('payoutMode.intro', { strong })}</p>
          <ul>
            <li>{t('payoutMode.onDemand')}</li>
            <li>{t('payoutMode.automatic')}</li>
          </ul>
          <p>{t('payoutMode.why')}</p>
        </>
      ),
    },
    {
      id: 'receipts',
      icon: '📄',
      panel: () => <p>{t.rich('receipts.text', { strong })}</p>,
    },
    {
      id: 'fees',
      icon: '🧾',
      panel: () => (
        <>
          <p>{t.rich('fees.payment', { strong })}</p>
          {HELLOASSO_FEES_DECISION_PENDING && (
            <p data-testid="hao-fees-placeholder">
              {t.rich('fees.platform', { strong })} <span className="badge badge-warning">{t('fees.pending')}</span>
            </p>
          )}
        </>
      ),
    },
  ];

  const wipedDay = formatDay(status?.secretWipedAt, locale);
  const wipedTime = formatClock(status?.secretWipedAt, locale);
  const refreshDay = formatDay(status?.lastRefreshAt, locale);
  const refreshTime = formatClock(status?.lastRefreshAt, locale);
  const linkedDay = formatDay(status?.linkedAt, locale);

  const key: FaqEntry[] = [
    { id: 'whyKey', icon: '🔑', panel: () => <p>{t('whyKey.text')}</p> },
    {
      id: 'keyUse',
      icon: '🛡️',
      panel: () => (
        <>
          <p>{t.rich('keyUse.use', { strong })}</p>
          <p>{t.rich('keyUse.transparency', { strong })}</p>
          <p>{t.rich('keyUse.cannot', { strong })}</p>
        </>
      ),
    },
    {
      id: 'keepKey',
      icon: '🗑️',
      panel: () => (
        <>
          <p>{t.rich('keepKey.keep', { strong })}</p>
          <p>{t.rich('keepKey.revoke', { strong })}</p>
          {linked && status && (
            <div className="access-live" data-testid="hao-access-live">
              <div className="access-live-head">
                <strong>{t('keepKey.live.title')}</strong>
                <span className="badge badge-success">{t('keepKey.live.works')}</span>
              </div>
              <dl className="kv">
                <dt>{t('keepKey.live.secret')}</dt>
                <dd>
                  <span className="badge badge-success"><span aria-hidden="true">✓ </span>{t('keepKey.live.wiped')}</span>{' '}
                  {wipedDay && wipedTime && (
                    <span className="small muted hao-label-hint">
                      {t('keepKey.live.wipedAt', { date: wipedDay, time: wipedTime })}
                    </span>
                  )}
                </dd>
                <dt>{t('keepKey.live.kept')}</dt>
                <dd className="hao-label-hint">
                  {t('keepKey.live.keptText')}
                  {refreshDay && refreshTime && <> {t('keepKey.live.lastRefresh', { date: refreshDay, time: refreshTime })}</>}
                </dd>
                {status.clientIdMasked && (
                  <>
                    <dt>{t('keepKey.live.clientId')}</dt>
                    <dd className="mono">{status.clientIdMasked}</dd>
                  </>
                )}
                {linkedDay && (
                  <>
                    <dt>{t('keepKey.live.linked')}</dt>
                    <dd>{status.linkedBy ? t('keepKey.live.linkedValue', { date: linkedDay, by: status.linkedBy }) : linkedDay}</dd>
                  </>
                )}
              </dl>
              <div className="row hao-row-spaced">
                <button type="button" className="btn btn-secondary btn-sm" onClick={onReplaceKey}>
                  {t('keepKey.live.replace')}
                </button>
                <span className="small muted">{t('keepKey.live.replaceHint')}</span>
              </div>
            </div>
          )}
        </>
      ),
    },
    {
      id: 'docs',
      icon: '📂',
      panel: () => (
        <>
          <p>{t.rich('docs.law', { strong })}</p>
          <p>{t('docs.files')}</p>
        </>
      ),
    },
    { id: 'refused', icon: '🙋', panel: () => <p>{t('refused.text')}</p> },
    { id: 'real', icon: '🔒', panel: () => <p>{t.rich('real.text', { strong })}</p> },
  ];

  const renderGroup = (label: string, entries: FaqEntry[]) => (
    <div className="faq-group">
      <div className="faq-group-label">{label}</div>
      <div className="acc-list">
        {entries.map((entry) => {
          const isOpen = open[entry.id] === true;
          const panelId = `hao-faq-${entry.id}`;
          return (
            <div className="acc-item" key={entry.id}>
              <button
                type="button"
                className="acc-trigger"
                id={`${panelId}-t`}
                aria-expanded={isOpen}
                aria-controls={panelId}
                onClick={() => setOpen((o) => ({ ...o, [entry.id]: !isOpen }))}
              >
                <span className="acc-ico" aria-hidden="true">{entry.icon}</span>
                <span className="acc-q">{t(`${entry.id}.q`)}</span>
                <span className="acc-chev" aria-hidden="true">▾</span>
              </button>
              <div className="acc-panel" id={panelId} role="region" aria-labelledby={`${panelId}-t`} hidden={!isOpen}>
                {isOpen && entry.panel()}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );

  return (
    <div className="card card-no-hover hao-faq-card">
      <div className="card-header-bar">
        <h3>{beforeStart ? t('titleStart') : t('title')}</h3>
        {beforeStart && <span className="small muted">{t('subStart')}</span>}
      </div>
      <div className="card-body">
        {renderGroup(t('groups.how'), how)}
        {renderGroup(t('groups.key'), key)}
      </div>
    </div>
  );
});

export default HelloAssoFaq;

/** Colour of a mini-figure icon chip (mockup `mini-fig-ico` modifiers). */
const FigTone = {
  TEAL: 'teal',
  INDIGO: 'indigo',
  AMBER: 'amber',
} as const;
type FigTone = typeof FigTone[keyof typeof FigTone];

interface MiniFigProps {
  icon: string;
  tone: FigTone;
  value: string;
  label: string;
}

/** Key figure with an icon chip (mockup `mini-fig`). */
function MiniFig({ icon, tone, value, label }: MiniFigProps) {
  return (
    <div className="mini-fig">
      <span className={`mini-fig-ico ${tone}`} aria-hidden="true">{icon}</span>
      <div>
        <div className="mini-fig-val">{value}</div>
        <div className="mini-fig-lbl">{label}</div>
      </div>
    </div>
  );
}

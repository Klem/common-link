'use client';

import { useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';
import { downloadOwnAssociationDocument } from '@/lib/api/association-documents';
import { MollieStructureVariant } from '@/types/mollie-connect';
import type { MollieOnboardingContext } from '@/types/mollie-connect';
import { KitRowKind, STRUCTURE_KIT, kitProgress } from './documentKit';
import type { KitRow } from './documentKit';

/** Public search page of the French entity directory; a bare SIREN is a valid search term. */
const ANNUAIRE_SEARCH_URL = 'https://annuaire-entreprises.data.gouv.fr/rechercher?terme=';

/** Icon per kit row kind, as in the mockup. */
const ROW_ICON: Record<KitRowKind, string> = {
  [KitRowKind.IDENTITY]: '🪪',
  [KitRowKind.STATUTES]: '📜',
  [KitRowKind.PROOF]: '🏛️',
  [KitRowKind.RIB]: '🏦',
  [KitRowKind.SIREN]: '🔢',
};

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

/** "912345678" → "912 345 678"; anything else is returned unchanged. */
function formatSiren(siren: string): string {
  return /^\d{9}$/.test(siren) ? siren.replace(/(\d{3})(\d{3})(\d{3})/, '$1 $2 $3') : siren;
}

interface DownloadButtonProps {
  documentId: string;
  fallbackName: string;
}

/** "Retrouver mon fichier": re-downloads a document the association already provided (journalled server-side). */
function DownloadButton({ documentId, fallbackName }: DownloadButtonProps) {
  const t = useTranslations('settings.mollie.onboarding.kit');
  const [downloading, setDownloading] = useState(false);
  const [failed, setFailed] = useState(false);

  const handleClick = async () => {
    setDownloading(true);
    setFailed(false);
    try {
      await downloadOwnAssociationDocument(documentId, fallbackName);
    } catch {
      setFailed(true);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <>
      <button type="button" className="btn btn-secondary btn-xs" onClick={handleClick} disabled={downloading}>
        {downloading ? t('downloading') : t('download')}
      </button>
      {failed && <p className="form-hint" role="alert">{t('downloadError')}</p>}
    </>
  );
}

interface KitScreenProps {
  context: MollieOnboardingContext | null;
  rows: KitRow[];
  /** Local "Je l'ai" ticks, by row id — UI state only, never sent anywhere. */
  ticked: Record<string, boolean>;
  onToggle: (rowId: string, checked: boolean) => void;
  /** Association name, quoted in the bank-details row (the account holder must match it exactly). */
  associationName: string | null;
  /** RNA number (`W…`), quoted in the declared-association proof row when known. */
  rna: string | null;
  onBack: () => void;
  /** Opens Mollie (hosted onboarding link, or the connection modal when not connected yet). */
  onOpenMollie: () => void;
  /** Disables the "open Mollie" actions (connected but no hosted onboarding link available). */
  openDisabled?: boolean;
  /** Rendered above the kit when the onboarding context could not be loaded. */
  contextErrorSlot?: ReactNode;
}

/**
 * Screen 2 "Votre kit de pièces": the documents to gather before opening Mollie, personalised from
 * the KYB dossier (one identity row per person, proof of existence per structure variant, documents
 * already provided to CommonLink with a re-download button).
 *
 * The "Je l'ai" ticks and the progress bar are local UI state. "J'ai tout, ouvrir Mollie" never
 * blocks: with missing documents it shows a soft warning offering to open Mollie anyway.
 */
export default function KitScreen({
  context,
  rows,
  ticked,
  onToggle,
  associationName,
  rna,
  onBack,
  onOpenMollie,
  openDisabled = false,
  contextErrorSlot,
}: KitScreenProps) {
  const t = useTranslations('settings.mollie.onboarding.kit');
  const [warningShown, setWarningShown] = useState(false);
  const warningRef = useRef<HTMLDivElement>(null);

  const { ready, total } = kitProgress(rows, ticked);
  const missing = total - ready;
  const percent = total === 0 ? 0 : Math.round((ready / total) * 100);
  const variant = context?.structureVariant ?? MollieStructureVariant.UNKNOWN;
  const proofKey = STRUCTURE_KIT[variant].key;
  const proofLink = STRUCTURE_KIT[variant].link;
  const siren = context?.siren?.trim() || null;

  const handleOpenFromKit = () => {
    if (missing > 0) {
      setWarningShown(true);
      warningRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
      return;
    }
    onOpenMollie();
  };

  const proofWhere = () => {
    const link = (chunks: ReactNode) =>
      proofLink ? (
        <a href={proofLink} target="_blank" rel="noopener noreferrer">
          {chunks}
        </a>
      ) : (
        chunks
      );
    const useRna = variant === MollieStructureVariant.DECLARED && rna;
    return t.rich(`proof.variants.${proofKey}.${useRna ? 'whereWithRna' : 'where'}`, { link, rna: rna ?? '' });
  };

  const renderMeta = (row: KitRow): ReactNode => {
    const provided = row.providedDocumentId !== null;
    switch (row.kind) {
      case KitRowKind.IDENTITY:
        return (
          <>
            <dt>{t('meta.mollieCalls')}</dt>
            <dd>{t('identity.mollieLabel')}</dd>
            <dt>{t('meta.whereToFindIt')}</dt>
            <dd>{provided ? t('identity.whereProvided') : t('identity.whereToPrepare', { name: row.person?.name ?? '' })}</dd>
            <dt>{t('meta.check')}</dt>
            <dd>{provided ? t('identity.checkProvided') : t('identity.checkToPrepare')}</dd>
          </>
        );
      case KitRowKind.STATUTES:
        // TODO(sprint-20 open point): Mollie's wording for statutes ("Statuts") is assumed, not sourced — confirm before go-live.
        return (
          <>
            <dt>{t('meta.mollieCalls')}</dt>
            <dd>{t('statutes.mollieLabel')}</dd>
            <dt>{t('meta.whereToFindThem')}</dt>
            <dd>{provided ? t('statutes.whereProvided') : t('statutes.whereToPrepare')}</dd>
            <dt>{t('meta.check')}</dt>
            <dd>{t('statutes.check')}</dd>
          </>
        );
      case KitRowKind.PROOF:
        return (
          <>
            <dt>{t('meta.mollieCalls')}</dt>
            <dd>{t('proof.mollieLabel')}</dd>
            <dt>{t('meta.whatYouNeed')}</dt>
            <dd>{t(`proof.variants.${proofKey}.what`)}</dd>
            <dt>{t('meta.whereToFindProof')}</dt>
            <dd>{proofWhere()}</dd>
          </>
        );
      case KitRowKind.RIB:
        return (
          <>
            <dt>{t('meta.mollieCalls')}</dt>
            <dd>{t('rib.mollieLabel')}</dd>
            <dt>{t('meta.whereToFindIt')}</dt>
            <dd>{t('rib.where')}</dd>
            <dt>{t('meta.check')}</dt>
            <dd>
              {associationName
                ? t.rich('rib.check', { strong, name: associationName })
                : t.rich('rib.checkWithoutName', { strong })}
            </dd>
          </>
        );
      case KitRowKind.SIREN:
        return (
          <>
            <dt>{t('meta.mollieCalls')}</dt>
            <dd>{t('siren.mollieLabel')}</dd>
            <dt>{t('meta.whoHasOne')}</dt>
            <dd>{t('siren.whoHasOne')}</dd>
            {siren && (
              <>
                <dt>{t('meta.yours')}</dt>
                <dd>
                  {formatSiren(siren)} —{' '}
                  <a href={`${ANNUAIRE_SEARCH_URL}${encodeURIComponent(siren)}`} target="_blank" rel="noopener noreferrer">
                    {t('siren.lookup')}
                  </a>
                </dd>
              </>
            )}
          </>
        );
    }
  };

  const rowTitle = (row: KitRow): string => {
    switch (row.kind) {
      case KitRowKind.IDENTITY:
        return row.person?.role
          ? t('identity.titleWithRole', { name: row.person.name, role: row.person.role })
          : t('identity.title', { name: row.person?.name ?? '' });
      case KitRowKind.STATUTES:
        return t('statutes.title');
      case KitRowKind.PROOF:
        return t('proof.title');
      case KitRowKind.RIB:
        return t('rib.title');
      case KitRowKind.SIREN:
        return t('siren.title');
    }
  };

  return (
    <section className="screen visible" aria-labelledby="mob-s2-title">
      <div className="row-between mob-title-row">
        <h2 id="mob-s2-title" className="mob-kit-title">{t('title')}</h2>
      </div>
      <p className="lead mob-lead-spaced">{t.rich('lead', { strong })}</p>

      {contextErrorSlot}

      <div className="card card-no-hover mob-card-gap">
        <div className="card-body mob-progress-body">
          <div className="row-between mob-progress-head">
            <strong className="small">{t('progress.title')}</strong>
            <span className="small muted" aria-live="polite">{t('progress.label', { ready, total })}</span>
          </div>
          <div
            className="progress-bar"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={total}
            aria-valuenow={ready}
            aria-label={t('progress.aria')}
          >
            <div className="progress-fill teal" style={{ width: `${percent}%` }} />
          </div>
        </div>
      </div>

      <div className="alert alert-info mob-alert-bottom">
        <span className="alert-icon" aria-hidden="true">📱</span>
        <div>{t.rich('phoneAlert', { strong })}</div>
      </div>

      <div className="kit-list">
        {rows.map((row) => {
          const provided = row.providedDocumentId !== null;
          const inputId = `mob-kit-${row.id}`;
          return (
            <div key={row.id} className="expense-card">
              <div className="expense-icon" aria-hidden="true">{ROW_ICON[row.kind]}</div>
              <div className="expense-info">
                <h4>{rowTitle(row)}</h4>
                <dl className="kit-meta">{renderMeta(row)}</dl>
              </div>
              <div className="kit-side">
                {!row.required ? (
                  <span className="badge badge-neutral">{t('badges.optional')}</span>
                ) : provided ? (
                  <span className="badge badge-success">{t('badges.provided')}</span>
                ) : (
                  <span className="badge badge-warning">{t('badges.toPrepare')}</span>
                )}
                {row.providedDocumentId && (
                  <DownloadButton documentId={row.providedDocumentId} fallbackName={`commonlink-${row.kind.toLowerCase()}`} />
                )}
                <label className="form-checkbox" htmlFor={inputId}>
                  <input
                    id={inputId}
                    type="checkbox"
                    checked={ticked[row.id] === true}
                    onChange={(e) => onToggle(row.id, e.target.checked)}
                  />
                  {t('have')}
                </label>
              </div>
            </div>
          );
        })}
      </div>

      <div className="alert alert-warning mob-alert-top">
        <span className="alert-icon" aria-hidden="true">💡</span>
        <div>{t.rich('mistakesAlert', { strong })}</div>
      </div>

      <div ref={warningRef}>
        {warningShown && missing > 0 && (
          <div className="alert alert-warning mob-alert-top" role="alert">
            <span className="alert-icon" aria-hidden="true">🧺</span>
            <div className="mob-warn-body">
              <strong>{t('softWarning.missing', { count: missing })}</strong>
              <div className="small mob-warn-hint">{t('softWarning.hint')}</div>
              <div className="row mob-warn-actions">
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  disabled={openDisabled}
                  onClick={() => {
                    setWarningShown(false);
                    onOpenMollie();
                  }}
                >
                  {t('softWarning.openAnyway')}
                </button>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setWarningShown(false)}>
                  {t('softWarning.keepPreparing')}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="screen-foot">
        <button type="button" className="btn btn-ghost" onClick={onBack}>
          <span aria-hidden="true">←</span> {t('back')}
        </button>
        <button type="button" className="btn btn-primary btn-lg" onClick={handleOpenFromKit} disabled={openDisabled}>
          {t('openMollie')}
        </button>
      </div>
    </section>
  );
}

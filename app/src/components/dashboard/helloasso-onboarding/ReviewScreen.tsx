'use client';

import { useEffect, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { downloadOwnAssociationDocument, listKybDocuments } from '@/lib/api/association-documents';
import { httpStatusOf, refreshEligibility } from '@/lib/api/helloasso';
import type { OnboardingProvidedDocument } from '@/types/mollie-connect';
import { formatClock, verificationUrl, verificationUrlLabel } from './helloAssoFlow';

/** Feedback of "Vérifier mon dossier". */
const RefreshFeedback = {
  TOO_SOON: 'tooSoon',
  ERROR: 'refreshError',
} as const;
type RefreshFeedback = typeof RefreshFeedback[keyof typeof RefreshFeedback];

interface ReviewScreenProps {
  /** Linked HelloAsso organization (verification deep link). */
  organizationSlug: string | null;
  /** Last eligibility read, ISO-8601. */
  lastEligibilityCheckAt: string | null;
  /** Re-reads the payment status after an eligibility check. */
  onRefreshed: () => Promise<void>;
}

/**
 * Screen 5 "Vérification de votre association par HelloAsso" (mockup `screen-5`): linked, HelloAsso has not verified
 * the association yet. One "Mon dossier" card: the HelloAsso verification page of the organization, the KYB documents
 * already given to CommonLink ("Retrouver mes fichiers"), and "Vérifier mon dossier" (rate-limited server-side to once
 * a minute; a 429 is explained here, without a global toast).
 */
export default function ReviewScreen({ organizationSlug, lastEligibilityCheckAt, onRefreshed }: ReviewScreenProps) {
  const t = useTranslations('settings.helloasso.onboarding.review');
  const locale = useLocale();
  const [refreshing, setRefreshing] = useState(false);
  const [feedback, setFeedback] = useState<RefreshFeedback | null>(null);
  const [filesOpen, setFilesOpen] = useState(false);
  const lastCheck = formatClock(lastEligibilityCheckAt, locale);

  const refresh = async () => {
    setRefreshing(true);
    setFeedback(null);
    try {
      await refreshEligibility();
      await onRefreshed();
    } catch (error) {
      setFeedback(httpStatusOf(error) === 429 ? RefreshFeedback.TOO_SOON : RefreshFeedback.ERROR);
    } finally {
      setRefreshing(false);
    }
  };

  return (
    <section className="screen visible" aria-labelledby="hao-s5-title">
      <div className="row-between hao-title-row">
        <h2 id="hao-s5-title" className="hao-screen-title">{t('title')}</h2>
        <span className="badge badge-warning">{t('badge')}</span>
      </div>
      <p className="lead hao-lead-spaced">{t('lead')}</p>

      <div className="card card-no-hover">
        <div className="card-header-bar">
          <h3>{t('cardTitle')}</h3>
          <span className="small muted">{t('cardSub')}</span>
        </div>
        <div className="card-body">
          <p className="small">{t('text')}</p>
          {organizationSlug && (
            <div className="copy-row hao-row-spaced">
              <span className="copy-row-label">{t('pageLabel')}</span>
              <span className="copy-row-value mono">{verificationUrlLabel(organizationSlug)}</span>
            </div>
          )}
          <div className="row-between hao-row-spaced-lg">
            <div className="row">
              {organizationSlug && (
                <a
                  className="btn btn-primary btn-sm"
                  href={verificationUrl(organizationSlug)}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {t('openVerification')}
                </a>
              )}
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                aria-expanded={filesOpen}
                aria-controls="hao-kyb-files"
                onClick={() => setFilesOpen((open) => !open)}
              >
                {t('files')}
              </button>
            </div>
            <div className="row">
              {lastCheck && <span className="small muted">{t('lastCheck', { time: lastCheck })}</span>}
              <button type="button" className="btn btn-secondary btn-sm" onClick={refresh} disabled={refreshing}>
                {refreshing ? t('refreshing') : t('refresh')}
              </button>
            </div>
          </div>
          {feedback && <p className="form-hint" role="status">{t(feedback)}</p>}
          {filesOpen && <KybFiles id="hao-kyb-files" />}
        </div>
      </div>

      <div className="screen-foot">
        <span className="small muted">{t('foot')}</span>
      </div>
    </section>
  );
}

/** Loading state of the KYB document list. */
const FilesState = {
  LOADING: 'LOADING',
  READY: 'READY',
  ERROR: 'ERROR',
} as const;
type FilesState = typeof FilesState[keyof typeof FilesState];

/**
 * "Retrouver mes fichiers": the KYB documents the association gave CommonLink (`GET /api/association/documents/kyb`),
 * each re-downloadable (journaled server-side), so it can upload them itself at HelloAsso.
 */
export function KybFiles({ id }: { id: string }) {
  const t = useTranslations('settings.helloasso.onboarding.files');
  const [state, setState] = useState<FilesState>(FilesState.LOADING);
  const [documents, setDocuments] = useState<OnboardingProvidedDocument[]>([]);

  useEffect(() => {
    let cancelled = false;
    listKybDocuments()
      .then((docs) => {
        if (cancelled) return;
        setDocuments(docs);
        setState(FilesState.READY);
      })
      .catch(() => !cancelled && setState(FilesState.ERROR));
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div id={id} className="hao-files" role="region" aria-label={t('title')}>
      {state === FilesState.LOADING && <p className="small muted" role="status">{t('loading')}</p>}
      {state === FilesState.ERROR && <p className="form-hint" role="alert">{t('error')}</p>}
      {state === FilesState.READY && documents.length === 0 && <p className="small muted">{t('empty')}</p>}
      {state === FilesState.READY && documents.length > 0 && (
        <ul>
          {documents.map((doc) => (
            <li key={doc.id}>
              <span>{t(`types.${doc.type}`)}</span>
              <DownloadButton documentId={doc.id} fallbackName={t(`types.${doc.type}`)} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Re-downloads one document; shows its own failure. */
function DownloadButton({ documentId, fallbackName }: { documentId: string; fallbackName: string }) {
  const t = useTranslations('settings.helloasso.onboarding.files');
  const [downloading, setDownloading] = useState(false);
  const [failed, setFailed] = useState(false);

  const download = async () => {
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
    <span className="row">
      {failed && <span className="form-hint" role="alert">{t('downloadError')}</span>}
      <button type="button" className="btn btn-secondary btn-xs" onClick={download} disabled={downloading}>
        {downloading ? t('downloading') : t('download')}
      </button>
    </span>
  );
}

'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { getLegalDocument } from '@/lib/api/public';
import { LegalDocumentType, type LegalDocumentDto } from '@/types/legal';
import { LegalDocumentRenderer, legalTitle } from './LegalDocumentRenderer';

interface Props {
  documentType: LegalDocumentType;
  /**
   * The text already loaded by the caller — the donation form passes the very version it will send
   * as accepted, so the reader sees exactly that text. Fetched here when absent.
   */
  document?: LegalDocumentDto | null;
  onClose: () => void;
}

/**
 * Read-only viewer for the current CGU/CGV text — opened from the donation form and the
 * campaign-publish checkbox instead of navigating to `/legal/[documentType]` in a new tab.
 * Scrolls inside the modal: the full texts are several screens long.
 *
 * Purely informational: it never checks the acceptance checkbox itself, matching the previous
 * new-tab link's behaviour — accepting stays an explicit, separate action.
 */
export function LegalDocumentModal({ documentType, document: preloaded, onClose }: Props) {
  const t = useTranslations('legal');

  const [fetched, setFetched] = useState<LegalDocumentDto | null>(null);
  const [hasError, setHasError] = useState(false);
  const document = preloaded ?? fetched;

  useEffect(() => {
    if (preloaded) return;
    let cancelled = false;
    setFetched(null);
    setHasError(false);
    getLegalDocument(documentType)
      .then((doc) => { if (!cancelled) setFetched(doc); })
      .catch(() => { if (!cancelled) setHasError(true); });
    return () => { cancelled = true; };
  }, [documentType, preloaded]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="modal modal-scroll modal-wide" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{legalTitle(document, t(`title.${documentType}`))}</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label={t('close')}>×</button>
        </div>
        <div className="modal-body">
          {hasError && <p className="form-error">{t('loadError')}</p>}
          {!hasError && !document && <p>{t('loading')}</p>}
          {document && <LegalDocumentRenderer document={document} />}
        </div>
        <div className="modal-footer">
          <button type="button" onClick={onClose} className="btn btn-primary btn-sm">
            {t('close')}
          </button>
        </div>
      </div>
    </div>
  );
}

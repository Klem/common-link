'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { getLegalDocument } from '@/lib/api/public';
import { LegalDocumentType, type LegalDocumentDto } from '@/types/legal';

interface Props {
  documentType: LegalDocumentType;
  onClose: () => void;
}

/**
 * Read-only viewer for the current CGU/CGV text — opened from the donation form and the
 * campaign-publish checkbox instead of navigating to `/legal/[documentType]` in a new tab.
 *
 * Purely informational: it never checks the acceptance checkbox itself, matching the previous
 * new-tab link's behaviour — accepting stays an explicit, separate action.
 */
export function LegalDocumentModal({ documentType, onClose }: Props) {
  const t = useTranslations('legal');

  const [document, setDocument] = useState<LegalDocumentDto | null>(null);
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setDocument(null);
    setHasError(false);
    getLegalDocument(documentType)
      .then((doc) => { if (!cancelled) setDocument(doc); })
      .catch(() => { if (!cancelled) setHasError(true); });
    return () => { cancelled = true; };
  }, [documentType]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{t(`title.${documentType}`)}</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label={t('close')}>×</button>
        </div>
        <div className="modal-body">
          {hasError && <p className="form-error">{t('loadError')}</p>}
          {!hasError && !document && <p>{t('loading')}</p>}
          {document && (
            <>
              <p className="text-sm text-text-2" style={{ marginBottom: 16 }}>
                {t('version', { version: document.version })}
              </p>
              <div style={{ whiteSpace: 'pre-wrap' }}>{document.content}</div>
            </>
          )}
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

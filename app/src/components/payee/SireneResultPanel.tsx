'use client';

import { useEffect } from 'react';
import { useTranslations } from 'next-intl';
import type { SireneSearchResultDto } from '@/types/payee';

interface SireneResultPanelProps {
  /** The Sirene search result to display. */
  result: SireneSearchResultDto;
  /** Called when the user clicks "Confirmer ce bénéficiaire". */
  onSelect: () => void;
  /** Called when the user closes the panel. */
  onClose: () => void;
  /** When true, the confirm button shows a spinner (creation in progress). */
  isLoading?: boolean;
}

/**
 * Inline result panel displaying key information from a Sirene search result.
 *
 * Shows: name, status badges, SIREN/address info in a DL grid.
 * Confirm adds the payee; Cancel/Escape dismisses the panel.
 */
export function SireneResultPanel({ result, onSelect, onClose, isLoading = false }: SireneResultPanelProps) {
  const t = useTranslations('dashboard');

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const address = [result.city, result.postalCode].filter(Boolean).join(' — ');

  return (
    <div className="card card-no-hover animate-slide-up-step">
      {/* Header bar: name + status badges */}
      <div className="card-h">
        <div className="sirene-head">
          <h3 className="sirene-name">{result.name}</h3>
          <div className="sirene-badges">
            {result.active ? (
              <span className="badge badge-active">● {t('payees.result.active')}</span>
            ) : (
              <span className="badge badge-neutral">● {t('payees.result.closed')}</span>
            )}
            {result.category && <span className="badge badge-neutral">{result.category}</span>}
            {result.isEss && <span className="badge badge-warning">{t('payees.result.ess')}</span>}
            {result.isSiege && <span className="badge badge-info">{t('payees.result.siege')}</span>}
          </div>
        </div>
      </div>

      {/* Body: DL grid + address + actions */}
      <div className="card-b">
        <dl className="frow sirene-grid">
          <div>
            <dt className="sirene-dt">{t('payees.result.siren')}</dt>
            <dd className="sirene-dd mono">{result.siren}</dd>
          </div>
          {result.creationDate && (
            <div>
              <dt className="sirene-dt">{t('payees.result.created')}</dt>
              <dd className="sirene-dd">{result.creationDate}</dd>
            </div>
          )}
          {result.nafCode && (
            <div>
              <dt className="sirene-dt">{t('payees.result.naf')}</dt>
              <dd className="sirene-dd mono">{result.nafCode}</dd>
            </div>
          )}
          {result.employeeRange && (
            <div>
              <dt className="sirene-dt">{t('payees.result.employees')}</dt>
              <dd className="sirene-dd">{result.employeeRange}</dd>
            </div>
          )}
          {address && (
            <div className="sirene-grid-full">
              <dt className="sirene-dt">{t('payees.result.address')}</dt>
              <dd className="sirene-dd">📍 {address}</dd>
            </div>
          )}
        </dl>

        {/* Action buttons */}
        <div className="sirene-actions">
          <button
            onClick={onSelect}
            disabled={isLoading}
            className="btn btn-primary"
            aria-busy={isLoading}
          >
            {isLoading ? <span className="spinner" /> : '✚'}
            {t('payees.result.select')}
          </button>
          <button onClick={onClose} className="btn btn-ghost">
            {t('payees.result.close')}
          </button>
        </div>
      </div>
    </div>
  );
}

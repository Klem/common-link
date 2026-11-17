'use client';

import { useCallback, useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { acceptLegalDocument, getLegalAcceptanceState, isLegalVersionOutdated } from '@/lib/api/legal';
import { getLegalDocument } from '@/lib/api/public';
import { ASSOCIATION_LEGAL_DOCUMENT, type LegalDocumentDto } from '@/types/legal';
import { LegalDocumentRenderer, legalTitle } from '@/components/legal/LegalDocumentRenderer';

/** The document an association accepts — publication gate and this post-login gate alike. */
const GATED_DOCUMENT = ASSOCIATION_LEGAL_DOCUMENT;

/**
 * Post-login gate for associations, on their terms of use (document `CGV`). Rendered once by
 * `DashboardShell`, so the check runs when the dashboard is entered (login, reload) — not on every
 * navigation between dashboard pages.
 *
 * When a version was published since the association's last acceptance, a modal that cannot be
 * dismissed shows the **current** text with a checkbox; versions published and superseded in between
 * are never shown, so several changes in a row cost one acceptance. Accepting sends back the version
 * displayed: a 409 `LEGAL_VERSION_OUTDATED` (newer text published meanwhile) reloads the text and asks
 * again.
 *
 * Fails open on a read error: the gate is a courtesy prompt, the binding check is server-side at campaign
 * publication (`LegalAcceptanceService.requireAssociationAcceptance`), which this gate does not replace.
 */
export function AssociationLegalGate() {
  const t = useTranslations('legal');
  const [document, setDocument] = useState<LegalDocumentDto | null>(null);
  const [checked, setChecked] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const state = await getLegalAcceptanceState(GATED_DOCUMENT);
      if (state.accepted) {
        setDocument(null);
        return;
      }
      setDocument(await getLegalDocument(GATED_DOCUMENT));
    } catch {
      setDocument(null);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (!document) return null;

  const handleAccept = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await acceptLegalDocument(GATED_DOCUMENT, document.version);
      setDocument(null);
    } catch (err) {
      if (isLegalVersionOutdated(err)) {
        setChecked(false);
        setError(t('outdated'));
        await load();
      } else {
        setError(t('gate.error'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop legal-gate-backdrop" role="dialog" aria-modal="true" aria-labelledby="legal-gate-title">
      <div className="modal modal-scroll modal-wide">
        <div className="modal-header">
          <h3 id="legal-gate-title">{t('gate.title')}</h3>
        </div>
        <div className="modal-body">
          <p className="legal-doc-meta">{t('gate.intro')}</p>
          <h3>{legalTitle(document, t(`title.${GATED_DOCUMENT}`))}</h3>
          <LegalDocumentRenderer document={document} idPrefix="legal-gate" />
        </div>
        <div className="modal-footer">
          {error && <p className="form-error">{error}</p>}
          <label className="legal-gate-accept">
            <input type="checkbox" checked={checked} onChange={(e) => setChecked(e.target.checked)} />
            {t('gate.checkbox')}
          </label>
          <button
            type="button"
            className="btn btn-primary btn-sm"
            disabled={!checked || submitting}
            onClick={handleAccept}
          >
            {t('gate.accept')}
          </button>
        </div>
      </div>
    </div>
  );
}

'use client';

import { useCallback, useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { acceptLegalDocument, getLegalAcceptanceState, isLegalVersionOutdated } from '@/lib/api/legal';
import { getLegalDocument } from '@/lib/api/public';
import { ASSOCIATION_LEGAL_DOCUMENTS, type LegalDocumentDto } from '@/types/legal';
import { LegalDocumentRenderer, legalTitle } from '@/components/legal/LegalDocumentRenderer';

/**
 * Post-login gate for associations, on the documents they accept (`ASSOCIATION_LEGAL_DOCUMENTS`: terms of use
 * `CGV`, then privacy policy `PRIVACY`) — the same ones as at campaign publication. Rendered once by
 * `DashboardShell`, so the check runs when the dashboard is entered (login, reload) — not on every navigation
 * between dashboard pages.
 *
 * When a version of a document was published since the association's last acceptance, a modal that cannot be
 * dismissed shows its **current** text with a checkbox; the intro, checkbox and button wording depend on the
 * document (`gate.*.{CGV,PRIVACY}`): the terms of use are accepted, the privacy policy is acknowledged ("J'ai pris
 * connaissance"), though both are recorded the same way. Versions published and superseded in between are never
 * shown, so several changes in a row cost one acceptance. Pending documents are shown one after the other, in
 * the order above. Accepting sends back the version displayed: a 409 `LEGAL_VERSION_OUTDATED` (newer text
 * published meanwhile) reloads the text and asks again.
 *
 * Fails open on a read error: the gate is a courtesy prompt, the binding check is server-side at campaign
 * publication (`LegalAcceptanceService.requireAssociationAcceptances`), which this gate does not replace.
 */
export function AssociationLegalGate() {
  const t = useTranslations('legal');
  const [document, setDocument] = useState<LegalDocumentDto | null>(null);
  const [checked, setChecked] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Shows the first document whose current version is not accepted yet, or nothing. */
  const load = useCallback(async () => {
    try {
      // One request after the other: the API client is never used in parallel.
      for (const type of ASSOCIATION_LEGAL_DOCUMENTS) {
        const state = await getLegalAcceptanceState(type);
        if (!state.accepted) {
          setDocument(await getLegalDocument(type));
          return;
        }
      }
      setDocument(null);
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
      await acceptLegalDocument(document.documentType, document.version);
      setChecked(false);
      await load();
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
          <p className="legal-doc-meta">{t(`gate.intro.${document.documentType}`)}</p>
          <h3>{legalTitle(document, t(`title.${document.documentType}`))}</h3>
          <LegalDocumentRenderer document={document} idPrefix="legal-gate" />
        </div>
        <div className="modal-footer">
          {error && <p className="form-error">{error}</p>}
          <label className="legal-gate-accept">
            <input type="checkbox" checked={checked} onChange={(e) => setChecked(e.target.checked)} />
            {t(`gate.checkbox.${document.documentType}`)}
          </label>
          <button
            type="button"
            className="btn btn-primary btn-sm"
            disabled={!checked || submitting}
            onClick={handleAccept}
          >
            {t(`gate.accept.${document.documentType}`)}
          </button>
        </div>
      </div>
    </div>
  );
}

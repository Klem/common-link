'use client';

import { useTranslations } from 'next-intl';

/**
 * Mollie B.V.'s entry in the Dutch central bank's public register of licensed institutions.
 * `WFTEG` is the electronic-money-institution register, `F0038` Mollie's relation number; the page
 * also lists the cross-border passport under which Mollie operates in France.
 */
const DNB_REGISTER_URL =
  'https://www.dnb.nl/en/public-register/information-detail/?registerCode=WFTEG&relationNumber=F0038';

/** Mollie's own security page — the source of the encryption, PCI-DSS and GDPR statements below. */
const MOLLIE_SECURITY_URL = 'https://www.mollie.com/security';

interface MollieInfoModalProps {
  isOpen: boolean;
  onClose: () => void;
}

/**
 * Explains who Mollie is, why its identity checks are unavoidable, and what becomes of the
 * documents the association uploads there.
 *
 * Purely informational: no connect action, no gating. It exists because asking an association to
 * hand an ID document and a bank statement to a company it has never heard of is, by itself, the
 * largest source of friction in the bank tab — and reads like a scam when left unexplained.
 *
 * Every claim is sourced and deliberately narrow:
 * - the licence and the French passport come from the DNB public register ({@link DNB_REGISTER_URL});
 * - encryption, PCI-DSS and the GDPR controller role come from {@link MOLLIE_SECURITY_URL};
 * - the "documents never reach CommonLink" statement mirrors the audited data flow described in
 *   `docs/legal/E3-non-delegation-diligences-prestataire.md` §4.2 — the OAuth callback writes only
 *   tokens, the organization id, capability flags, the dashboard URL and a sync timestamp;
 * - "CommonLink never holds the funds" reflects `MollieClient.createPayment`, which creates every
 *   payment on the association's own Mollie account using its own access token.
 *
 * Nothing here may drift into claims we cannot back: no fund-segregation promise, no ACPR
 * authorisation (the licence is Dutch, exercised in France under the PSD2 passport), no
 * merchant-count marketing figure.
 */
export default function MollieInfoModal({ isOpen, onClose }: MollieInfoModalProps) {
  const t = useTranslations('settings.mollie.info');

  if (!isOpen) return null;

  const sections = ['who', 'why', 'documents', 'funds'] as const;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-scroll" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2 className="font-semibold text-text">{t('title')}</h2>
          <button className="modal-close" onClick={onClose} aria-label="close">×</button>
        </div>
        <div className="modal-body">
          <p className="mollie-info-intro">{t('intro')}</p>
          {sections.map((section) => (
            <section key={section} className="mollie-info-section">
              <h3 className="mollie-info-heading">{t(`sections.${section}.title`)}</h3>
              <p className="mollie-info-text">{t(`sections.${section}.body`)}</p>
            </section>
          ))}
          <section className="mollie-info-section">
            <h3 className="mollie-info-heading">{t('sections.verify.title')}</h3>
            <p className="mollie-info-text">{t('sections.verify.body')}</p>
            <ul className="mollie-info-links">
              <li>
                <a href={DNB_REGISTER_URL} target="_blank" rel="noopener noreferrer" className="mollie-info-link">
                  {t('sections.verify.registerLink')}
                </a>
              </li>
              <li>
                <a href={MOLLIE_SECURITY_URL} target="_blank" rel="noopener noreferrer" className="mollie-info-link">
                  {t('sections.verify.securityLink')}
                </a>
              </li>
            </ul>
          </section>
        </div>
        <div className="modal-footer">
          <button onClick={onClose} className="btn btn-primary btn-md">
            {t('close')}
          </button>
        </div>
      </div>
    </div>
  );
}

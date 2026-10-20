'use client';

import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';

const strong = (chunks: ReactNode) => <strong>{chunks}</strong>;

interface ActiveScreenProps {
  /** The page's `bankDone` (COMPLETED and payments enabled): success screen, else the "no payments" warning. */
  collecting: boolean;
  /** ISO instant of the self-completion attestation, null when never attested. */
  attestedAt: string | null;
  /** The attestation request is in flight, or its current state is still unknown. */
  attestBusy: boolean;
  attestError: boolean;
  onAttest: () => void;
}

/**
 * Screen 6 "Actif" (mockup `screen-6`): success when the association collects, or the
 * "Encaissement pas encore activé" warning for a COMPLETED account without payments (never
 * "Connecter Mollie" there).
 *
 * The self-completion attestation is journalled server-side and unlocks nothing: the Diffusion
 * tab of the page follows `collecting` (`bankDone`) only. Once recorded it cannot be withdrawn
 * (append-only journal), so the box stays checked and disabled.
 */
export default function ActiveScreen({ collecting, attestedAt, attestBusy, attestError, onAttest }: ActiveScreenProps) {
  const t = useTranslations('settings.mollie.onboarding.active');
  const attested = attestedAt !== null;

  return (
    <section className="screen visible" aria-labelledby={collecting ? 'mob-s6-title' : undefined}>
      {collecting ? (
        <div className="card card-no-hover">
          <div className="card-body">
            <div className="empty-state mob-active-state">
              <div className="empty-state-icon" aria-hidden="true">🎉</div>
              <h4 id="mob-s6-title">{t('title')}</h4>
              <p>{t('text')}</p>
              <span className="badge badge-success">{t('badge')}</span>
            </div>
          </div>
        </div>
      ) : (
        <div className="alert alert-warning" role="status">
          <span className="alert-icon" aria-hidden="true">⏳</span>
          <div>{t.rich('noPayments', { strong })}</div>
        </div>
      )}

      <div className="card card-no-hover mob-card-spaced">
        <div className="card-body">
          <label className="form-checkbox" htmlFor="mob-attest">
            <input
              id="mob-attest"
              type="checkbox"
              checked={attested}
              disabled={attested || attestBusy}
              onChange={(e) => {
                if (e.target.checked) onAttest();
              }}
            />
            {t('attest.label')}
          </label>
          <p className="form-hint mob-attest-hint">{t('attest.hint')}</p>
          {attestError && <p className="form-hint mob-attest-hint" role="alert">{t('attest.error')}</p>}
        </div>
      </div>
    </section>
  );
}

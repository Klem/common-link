'use client';

import { useCallback, useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { createLandingPreviewSession } from '@/lib/api/association';

/** Viewport widths of the two preview devices, in px. */
const DEVICE_WIDTH = { mobile: 375, desktop: 1280 } as const;

type Device = keyof typeof DEVICE_WIDTH;

interface LandingPreviewModalProps {
  widgetToken: string;
  onClose: () => void;
}

/**
 * Modal rendering the association's real landing page in an iframe.
 *
 * Uses the actual `/fr/lp/{token}` route rather than a re-implemented renderer, so the preview can
 * never drift from what a donor sees. A preview token is requested **on every load** — not cached in
 * state: the token lives 10 minutes, and the real usage loop is open → tweak a palette → reopen, so a
 * cached token would eventually render a 409 error page indistinguishable from a genuine failure.
 */
export function LandingPreviewModal({ widgetToken, onClose }: LandingPreviewModalProps) {
  const t = useTranslations('settings.landing.preview');

  const [device, setDevice] = useState<Device>('desktop');
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    setFailed(false);
    setSrc(null);
    try {
      const { previewToken } = await createLandingPreviewSession();
      const origin = window.location.origin;
      setSrc(`${origin}/fr/lp/${widgetToken}?preview=${encodeURIComponent(previewToken)}`);
    } catch {
      setFailed(true);
    }
  }, [widgetToken]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="ov on" onClick={onClose}>
      <div className="mod lp-preview-modal" onClick={(e) => e.stopPropagation()}>
        <div className="mod-h">
          <h3>{t('title')}</h3>
          <div className="lp-preview-devices">
            <button
              type="button"
              className={`btn btn-sm ${device === 'mobile' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setDevice('mobile')}
              aria-pressed={device === 'mobile'}
            >
              {t('mobile')}
            </button>
            <button
              type="button"
              className={`btn btn-sm ${device === 'desktop' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setDevice('desktop')}
              aria-pressed={device === 'desktop'}
            >
              {t('desktop')}
            </button>
            <button type="button" className="btn btn-secondary btn-sm" onClick={load}>
              {t('reload')}
            </button>
          </div>
          <button className="mod-x" onClick={onClose} aria-label={t('close')}>
            ✕
          </button>
        </div>
        <div className="mod-b lp-preview-body">
          {failed && <p className="fhint error">{t('failed')}</p>}
          {!failed && !src && <p className="fhint">{t('loading')}</p>}
          {src && (
            <iframe
              key={src}
              className="lp-preview-frame"
              style={{ width: DEVICE_WIDTH[device] }}
              src={src}
              title={t('title')}
            />
          )}
        </div>
      </div>
    </div>
  );
}

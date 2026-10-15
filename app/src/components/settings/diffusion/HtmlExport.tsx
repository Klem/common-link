'use client';

import { useCallback, useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';

interface Props {
  widgetToken: string;
  /** The export embeds this container: without it there is nothing to generate. */
  gtmContainerId: string | null;
}

/**
 * Standalone HTML export of the landing page, shown only while that method is selected — so it is
 * generated only when the association actually wants it.
 */
export function HtmlExport({ widgetToken, gtmContainerId }: Props) {
  const t = useTranslations('settings.diffusion');
  const [exportHtml, setExportHtml] = useState<string | null>(null);
  const [exportFailed, setExportFailed] = useState(false);
  const [exportCopied, setExportCopied] = useState(false);

  /**
   * Fetches a fresh export from `/api/gtm-export/{widgetToken}` — a snapshot of the real landing page
   * at generation time, not a live view. Re-run on demand ("Régénérer") rather than cached
   * indefinitely, so a later content or GTM ID change doesn't leave a stale download.
   */
  const loadExport = useCallback(async () => {
    if (!gtmContainerId) return;
    setExportFailed(false);
    setExportHtml(null);
    try {
      const res = await fetch(`/api/gtm-export/${widgetToken}?gtmId=${encodeURIComponent(gtmContainerId)}`);
      if (!res.ok) throw new Error('export failed');
      const { html } = await res.json();
      setExportHtml(html);
    } catch {
      setExportFailed(true);
    }
  }, [widgetToken, gtmContainerId]);

  useEffect(() => {
    loadExport();
  }, [loadExport]);

  const copyExport = async (html: string) => {
    await navigator.clipboard.writeText(html);
    setExportCopied(true);
    setTimeout(() => setExportCopied(false), 2000);
  };

  const downloadExport = (html: string) => {
    const blob = new Blob([html], { type: 'text/html' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'landing-gtm.html';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fg">
      <span className="fl">{t('methods.html.label')}</span>
      {!gtmContainerId ? (
        <p className="fhint error">{t('methods.html.gtmMissing')}</p>
      ) : (
        <>
          <div className="dif-file-row">
            {exportFailed ? (
              <span className="dif-file-name fhint error">{t('methods.html.failed')}</span>
            ) : (
              <span className="dif-file-name">{exportHtml ? t('methods.html.file') : t('methods.html.loading')}</span>
            )}
            {exportHtml && (
              <>
                <button type="button" className="btn btn-primary btn-sm" onClick={() => downloadExport(exportHtml)}>
                  {t('methods.html.download')}
                </button>
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => copyExport(exportHtml)}>
                  {exportCopied ? t('methods.copied') : t('methods.html.copy')}
                </button>
              </>
            )}
            <button type="button" className="btn btn-secondary btn-sm" onClick={loadExport}>
              {t('methods.html.reload')}
            </button>
          </div>
          <p className="fhint">{t('methods.html.hint')}</p>
        </>
      )}
    </div>
  );
}

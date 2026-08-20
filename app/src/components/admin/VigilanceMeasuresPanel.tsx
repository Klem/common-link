'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { getVigilanceMeasures } from '@/lib/api/admin';
import type { VigilanceMeasuresDto } from '@/types/admin';

interface Props {
  associationId: string;
}

export function VigilanceMeasuresPanel({ associationId }: Props) {
  const t = useTranslations('curator.dossier');

  const [data, setData] = useState<VigilanceMeasuresDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setFailed(false);
    getVigilanceMeasures(associationId)
      .then((d) => { if (active) setData(d); })
      .catch(() => { if (active) setFailed(true); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [associationId]);

  const containerStyle: React.CSSProperties = {
    border: '1px solid var(--color-border)',
    borderRadius: 8,
    padding: '12px 16px',
    marginBottom: 24,
    fontSize: 13,
  };

  if (loading) {
    return (
      <div style={containerStyle}>
        <p style={{ color: 'var(--color-text-2)', margin: 0 }}>{t('vigilance.loading')}</p>
      </div>
    );
  }

  if (failed || !data) {
    return (
      <div style={containerStyle}>
        <p style={{ color: 'var(--color-error)', margin: 0 }}>{t('vigilance.error')}</p>
      </div>
    );
  }

  return (
    <div style={containerStyle}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <span style={{ fontWeight: 700 }}>{t('vigilance.title')}</span>
        <span style={{ fontSize: 11, color: 'var(--color-text-2)', fontFamily: 'monospace' }}>
          {t('vigilance.version')} {data.classificationVersion}
        </span>
      </div>
      <p style={{ margin: '0 0 6px', color: 'var(--color-text-2)', fontSize: 13 }}>{data.description}</p>
      <p style={{ margin: '0 0 8px', fontSize: 12 }}>
        <strong>{t('vigilance.reviewFrequency')}</strong> {data.reviewFrequency}
      </p>
      {data.requiredDocuments.length > 0 && (
        <>
          <p style={{ margin: '0 0 4px', fontSize: 12, fontWeight: 600 }}>{t('vigilance.requiredDocuments')}</p>
          <ul style={{ margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 4 }}>
            {data.requiredDocuments.map((doc) => (
              <li key={doc} style={{ color: 'var(--color-text)' }}>{doc}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

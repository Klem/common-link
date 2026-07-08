'use client';

import { useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { downloadOptionalDocument } from '@/lib/api/verification';
import type { OptionalDocumentDto, OptionalDocCategory } from '@/types/verification';

const EXT_ICONS: Record<string, string> = {
  pdf: '📄', docx: '📝', xlsx: '📊', jpg: '🖼️', jpeg: '🖼️', png: '🖼️',
};

const CATEGORIES: OptionalDocCategory[] = ['financier', 'rapport', 'justificatif', 'autre'];

const ACCEPT = '.pdf,.jpg,.jpeg,.png,.docx,.xlsx';

function getExt(fileName: string): string {
  return fileName.split('.').pop()?.toLowerCase() ?? '';
}

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} Mo`;
  return `${Math.round(bytes / 1024)} Ko`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

interface OptionalDocsCardProps {
  docs: OptionalDocumentDto[];
  onUpload: (file: File, category: string) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
}

interface PendingUpload {
  file: File;
  category: OptionalDocCategory;
}

export function OptionalDocsCard({ docs, onUpload, onDelete }: OptionalDocsCardProps) {
  const t = useTranslations('dashboard');
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [pending, setPending] = useState<PendingUpload | null>(null);
  const [uploading, setUploading] = useState(false);

  const handleFilePick = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) setPending({ file, category: 'autre' });
    e.target.value = '';
  };

  const handleConfirmUpload = async () => {
    if (!pending) return;
    setUploading(true);
    try {
      await onUpload(pending.file, pending.category);
    } finally {
      setUploading(false);
      setPending(null);
    }
  };

  return (
    <div className="card no-hover">
      <div className="card-h">
        <h3>
          {t('verification.optDocs.title')}{' '}
          <span style={{ fontSize: 11, color: 'var(--slate-lavender)', fontWeight: 500 }}>
            ({t('verification.optDocs.optional')})
          </span>
        </h3>
        <button
          className="btn btn-secondary btn-sm"
          onClick={() => fileInputRef.current?.click()}
          disabled={!!pending}
        >
          + {t('verification.optDocs.add')}
        </button>
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept={ACCEPT}
        style={{ display: 'none' }}
        onChange={handleFilePick}
      />

      <div className="card-b flush">
        {/* Pending upload row */}
        {pending && (
          <div className="od-row" style={{ background: 'var(--soft-cloud)', flexWrap: 'wrap', gap: 10 }}>
            <div className="od-row-ic">{EXT_ICONS[getExt(pending.file.name)] ?? '📁'}</div>
            <div className="od-row-body" style={{ flex: 1 }}>
              <div className="od-row-name">{pending.file.name}</div>
              <div className="od-row-meta">{formatSize(pending.file.size)}</div>
            </div>
            <select
              value={pending.category}
              onChange={(e) => setPending({ ...pending, category: e.target.value as OptionalDocCategory })}
              className="fi"
              style={{ width: 'auto', padding: '4px 8px', fontSize: 13 }}
            >
              {CATEGORIES.map((cat) => (
                <option key={cat} value={cat}>
                  {t(`verification.optDocs.categories.${cat}` as Parameters<typeof t>[0])}
                </option>
              ))}
            </select>
            <div style={{ display: 'flex', gap: 6 }}>
              <button
                className="btn btn-primary btn-sm"
                onClick={handleConfirmUpload}
                disabled={uploading}
              >
                {uploading ? '…' : t('verification.optDocs.upload')}
              </button>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => setPending(null)}
                disabled={uploading}
              >
                {t('verification.optDocs.cancel')}
              </button>
            </div>
          </div>
        )}

        {docs.length === 0 && !pending ? (
          <div className="od-empty">{t('verification.optDocs.empty')}</div>
        ) : (
          docs.map((doc) => {
            const ext = getExt(doc.fileName);
            const icon = EXT_ICONS[ext] ?? '📁';
            return (
              <div key={doc.id} className="od-row">
                <div className="od-row-ic">{icon}</div>
                <div className="od-row-body">
                  <button
                    className="od-row-name"
                    style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', textAlign: 'left', color: 'inherit', fontWeight: 'inherit' }}
                    onClick={() => downloadOptionalDocument(doc.id, doc.fileName)}
                  >
                    {doc.fileName}
                  </button>
                  <div className="od-row-meta">
                    {formatSize(doc.sizeBytes)} · {formatDate(doc.uploadedAt)}
                  </div>
                </div>
                {doc.category && (
                  <span className="od-row-cat">
                    {t(`verification.optDocs.categories.${doc.category}` as Parameters<typeof t>[0])}
                  </span>
                )}
                <button className="vd-btn vd-btn-del" onClick={() => onDelete(doc.id)}>
                  {t('verification.optDocs.delete')}
                </button>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}

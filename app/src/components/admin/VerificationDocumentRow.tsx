'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { downloadVerificationDocument } from '@/lib/api/admin';
import type { DocumentSlotDto } from '@/types/verification';
import type { OptionalDocumentDto } from '@/types/verification';
import { DOC_TYPE_I18N_KEY } from '@/components/admin/adminShared';
import { useToastStore } from '@/stores/toastStore';

type RequiredVariant = {
  variant: 'required';
  associationId: string;
  slot: DocumentSlotDto;
};

type OptionalVariant = {
  variant: 'optional';
  associationId: string;
  doc: OptionalDocumentDto;
};

type Props = RequiredVariant | OptionalVariant;

function formatSize(bytes: number | undefined): string {
  if (!bytes) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const INLINE_MIME_TYPES = ['application/pdf', 'image/jpeg', 'image/png', 'image/gif', 'image/webp'];

export function VerificationDocumentRow(props: Props) {
  const t = useTranslations('admin');
  const addToast = useToastStore((s) => s.addToast);
  const [isDownloading, setIsDownloading] = useState(false);

  const isRequired = props.variant === 'required';
  const slot = isRequired ? props.slot : null;
  const doc = !isRequired ? props.doc : null;

  const docId = isRequired ? slot!.id : doc!.id;
  const isUploaded = isRequired ? slot!.uploaded : true;
  const fileName = isRequired ? slot!.fileName : doc!.fileName;
  const sizeBytes = isRequired ? slot!.sizeBytes : doc!.sizeBytes;
  const uploadedAt = isRequired ? slot!.uploadedAt : doc!.uploadedAt;

  const labelKey = isRequired ? DOC_TYPE_I18N_KEY[slot!.docType] : null;

  const formatDate = (iso: string | undefined) => {
    if (!iso) return '';
    return new Date(iso).toLocaleDateString(undefined, {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  };

  const handleDownload = async (openInline: boolean) => {
    if (!docId) return;
    setIsDownloading(true);
    try {
      const { blob, fileName: name } = await downloadVerificationDocument(props.associationId, docId);
      const url = URL.createObjectURL(blob);
      try {
        if (openInline && INLINE_MIME_TYPES.includes(blob.type)) {
          window.open(url, '_blank', 'noopener,noreferrer');
          // Delay revoke so the new tab has time to load the blob
          setTimeout(() => URL.revokeObjectURL(url), 10_000);
        } else {
          const a = document.createElement('a');
          a.href = url;
          a.download = name;
          a.click();
          URL.revokeObjectURL(url);
        }
      } catch {
        URL.revokeObjectURL(url);
        throw new Error('trigger failed');
      }
    } catch {
      addToast('error', 'admin.verificationDetail.doc.downloadError');
    } finally {
      setIsDownloading(false);
    }
  };

  const canPreviewInline = isUploaded; // MIME type only known after fetch, so always offer both

  return (
    <div
      style={{
        border: '1px solid var(--color-border)',
        borderRadius: 8,
        padding: '10px 14px',
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        background: isUploaded ? 'var(--color-bg)' : 'rgba(98,98,125,0.04)',
      }}
    >
      <span style={{ fontSize: 20, flexShrink: 0 }}>{isUploaded ? '📄' : '⬜'}</span>

      <div style={{ flex: 1, minWidth: 0 }}>
        <p style={{ fontWeight: 600, fontSize: 14, marginBottom: 2 }}>
          {isRequired
            ? t(`${labelKey}` as Parameters<typeof t>[0])
            : (doc!.category ?? fileName ?? '—')}
        </p>
        {isUploaded && fileName && (
          <p
            style={{
              fontSize: 12,
              color: 'var(--color-text-2)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {fileName}
            {sizeBytes ? ` — ${formatSize(sizeBytes)}` : ''}
            {uploadedAt ? ` · ${t('verificationDetail.doc.uploadedOn')} ${formatDate(uploadedAt)}` : ''}
          </p>
        )}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        {!isUploaded && (
          <span className="badge badge-neutral">{t('verificationDetail.doc.missing')}</span>
        )}

        {isUploaded && (
          <>
            {isDownloading ? (
              <span className="rm-spinner" />
            ) : (
              <>
                {canPreviewInline && (
                  <button
                    className="btn btn-sm btn-secondary"
                    onClick={() => handleDownload(true)}
                    title={t('verificationDetail.doc.view')}
                  >
                    {t('verificationDetail.doc.view')}
                  </button>
                )}
                <button
                  className="btn btn-sm btn-secondary"
                  onClick={() => handleDownload(false)}
                  title={t('verificationDetail.doc.download')}
                >
                  ↓
                </button>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}

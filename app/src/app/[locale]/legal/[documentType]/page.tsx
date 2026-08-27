import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { getTranslations } from 'next-intl/server';
import { getLegalDocument } from '@/lib/api/public';
import { LegalDocumentType } from '@/types/legal';

interface Props {
  params: Promise<{ locale: string; documentType: string }>;
}

function isLegalDocumentType(value: string): value is LegalDocumentType {
  return (Object.values(LegalDocumentType) as string[]).includes(value.toUpperCase());
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, documentType } = await params;
  if (!isLegalDocumentType(documentType)) return {};
  const t = await getTranslations({ locale, namespace: 'legal' });
  return { title: t(`title.${documentType.toUpperCase() as LegalDocumentType}`) };
}

/**
 * Standalone public page rendering the current CGU/CGV text — linked from the campaign-publish
 * checkbox (association side) and the donation form (donor side). Neither audience needs an
 * account to read it.
 */
export default async function LegalDocumentPage({ params }: Props) {
  const { documentType } = await params;
  if (!isLegalDocumentType(documentType)) notFound();
  const type = documentType.toUpperCase() as LegalDocumentType;

  let document;
  try {
    document = await getLegalDocument(type);
  } catch {
    notFound();
  }

  const t = await getTranslations('legal');

  return (
    <div className="min-h-screen flex justify-center px-4 py-12">
      <div className="card card-no-hover p-8 max-w-2xl w-full">
        <h1 className="font-display font-bold text-lg mb-1">{t(`title.${type}`)}</h1>
        <p className="text-sm text-text-2 mb-6">{t('version', { version: document.version })}</p>
        <div style={{ whiteSpace: 'pre-wrap' }}>{document.content}</div>
      </div>
    </div>
  );
}

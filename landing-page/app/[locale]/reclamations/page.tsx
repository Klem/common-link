import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

interface ComplaintSection {
  title: string;
  paragraphs?: string[];
  items?: string[];
  callout?: string;
  paragraphsAfter?: string[];
}

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.complaints' });
  return {
    title: t('title'),
    description: t('description'),
    alternates: {
      canonical: 'https://www.common-link.org/reclamations',
      languages: { fr: '/reclamations', en: '/en/reclamations' },
    },
  };
}

export default async function ComplaintsPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.complaints' });
  const sections = t.raw('sections') as ComplaintSection[];

  return (
    <>
      <LegalSubnav active="complaints" />
      <LegalContent title={t('title')} meta={t('meta')}>
        <p>{t('intro')}</p>
        {sections.map((section, i) => (
          <div key={i}>
            <h2>{section.title}</h2>
            {section.paragraphs?.map((p, j) => <p key={j}>{p}</p>)}
            {section.items && (
              <ul>
                {section.items.map((item, j) => <li key={j}>{item}</li>)}
              </ul>
            )}
            {section.callout && <div className="legal-callout">{section.callout}</div>}
            {section.paragraphsAfter?.map((p, j) => <p key={j}>{p}</p>)}
          </div>
        ))}
      </LegalContent>
    </>
  );
}

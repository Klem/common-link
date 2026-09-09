import { getTranslations } from 'next-intl/server';
import { LegalContent } from '@/components/layout/LegalContent';
import { LegalSubnav } from '@/components/layout/LegalSubnav';

interface MentionsSection {
  title: string;
  paragraphs: string[];
}

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.mentionsLegales' });
  return {
    title: t('title'),
    description: t('description'),
  alternates: {
    canonical: 'https://www.common-link.org/mentions-legales',
    languages: { fr: '/mentions-legales', en: '/en/mentions-legales' },
  },
  };
}

export default async function MentionsLegalesPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.mentionsLegales' });
  const sections = t.raw('sections') as MentionsSection[];

  return (
    <>
    <LegalSubnav active="mentions" />
    <LegalContent title={t('title')} meta={t('lastUpdated')}>
      {sections.map((section, i) => (
        <div key={i}>
          <h2>{section.title}</h2>
          {section.paragraphs.map((p, j) => <p key={j}>{p}</p>)}
        </div>
      ))}
    </LegalContent>
    </>
  );
}

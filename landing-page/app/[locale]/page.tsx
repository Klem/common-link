import { getTranslations, setRequestLocale } from 'next-intl/server';
import { Hero } from '@/components/sections/Hero';
import { Why } from '@/components/sections/Why';
import { Campaigns } from '@/components/sections/Campaigns';
import { TransparencySection } from '@/components/sections/TransparencySection';
import { AssoTeaser } from '@/components/sections/AssoTeaser';
import { Trust } from '@/components/sections/Trust';
import { Partners } from '@/components/sections/Partners';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.landing' });
  return {
    title: t('title'),
    description: t('description'),
    keywords: t('keywords'),
    openGraph: {
      title: t('ogTitle'),
      description: t('ogDescription'),
      type: 'website',
    },
    alternates: {
      canonical: 'https://www.common-link.org/',
      languages: { fr: '/', en: '/en' },
    },
  };
}

const organizationJsonLd = {
  '@context': 'https://schema.org',
  '@type': 'Organization',
  name: 'CommonLink',
  url: 'https://www.common-link.org/',
  logo: 'https://www.common-link.org/logo.png',
  description: "Plateforme de dons qui publie chaque paiement effectué par les associations, avec son destinataire et son montant.",
  address: {
    '@type': 'PostalAddress',
    streetAddress: '1047 Chemin des Impiniers',
    postalCode: '06220',
    addressLocality: 'Vallauris',
    addressCountry: 'FR',
  },
  sameAs: [],
};

export default async function HomePage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  setRequestLocale(locale);

  return (
    <main>
      <script
        type="application/ld+json"
        // eslint-disable-next-line react/no-danger
        dangerouslySetInnerHTML={{ __html: JSON.stringify(organizationJsonLd) }}
      />
      <Hero />
      <Why />
      <Campaigns />
      <TransparencySection />
      <AssoTeaser />
      <Trust />
      <Partners />
    </main>
  );
}

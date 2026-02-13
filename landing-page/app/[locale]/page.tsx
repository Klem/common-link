import { getTranslations } from 'next-intl/server';
import { Hero } from '@/components/sections/Hero';
import { Constat } from '@/components/sections/Constat';
import { Features } from '@/components/sections/Features';
import { Steps } from '@/components/sections/Steps';
import { Garantie } from '@/components/sections/Garantie';
import { Status } from '@/components/sections/Status';
import { FAQ } from '@/components/sections/FAQ';

export async function generateMetadata({ params: { locale } }: { params: { locale: string } }) {
  const t = await getTranslations({ locale, namespace: 'metadata.landing' });
  return {
    title: t('title'),
    description: t('description'),
    keywords: t('keywords'),
    openGraph: {
      title: t('title'),
      description: t('description'),
      type: 'website',
    },
    alternates: {
      languages: { fr: '/', en: '/en' },
    },
  };
}

export default function HomePage() {
  return (
    <main>
      <Hero />
      <Constat />
      <Features />
      <Steps />
      <Garantie />
      <Status />
      <FAQ namespace="landing.faq" count={6} />
    </main>
  );
}

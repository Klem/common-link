import { getTranslations } from 'next-intl/server';
import { Link } from '@/i18n/navigation';
import { LegalContent } from '@/components/layout/LegalContent';

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'metadata.faq' });
  return {
    title: t('title'),
    description: t('description'),
  };
}

const FAQ_KEYS = [
  'q0','q1','q2','q3','q4','q5','q6','q7','q8','q9','q10','q11',
  'q12','q13','q14','q15','q16','q17','q18','q19','q20','q21','q22','q23',
] as const;

export default async function FAQPage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'legal.faq' });

  return (
    <LegalContent title={t('title')}>
      <p>{t('intro')}</p>

      <div style={{ marginTop: '2rem' }}>
        {FAQ_KEYS.map((key) => {
          const question = t(`${key}.q`);
          const answer = t(`${key}.a`);

          // Inline links for specific questions
          let answerNode: React.ReactNode = answer;
          if (key === 'q13') {
            answerNode = (
              <>{answer} <Link href="/tarifs">{t('tarifsLinkLabel')}</Link></>
            );
          } else if (key === 'q21') {
            answerNode = (
              <>{answer} <Link href="/politique-cookies">{t('cookiesLinkLabel')}</Link></>
            );
          }

          return (
            <details
              key={key}
              style={{ borderBottom: '1px solid var(--color-border-light)', paddingBottom: '0.5rem', marginBottom: '0.25rem' }}
            >
              <summary
                style={{
                  padding: '1rem 0',
                  cursor: 'pointer',
                  fontFamily: 'var(--font-ui)',
                  fontWeight: 600,
                  fontSize: '0.95rem',
                  color: 'var(--color-text-dark)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  listStyle: 'none',
                }}
              >
                {question}
                <span style={{ color: 'var(--color-secondary)', fontSize: '1.2rem', marginLeft: '1rem', flexShrink: 0 }}>+</span>
              </summary>
              <div style={{ paddingBottom: '1rem' }}>
                <p>{answerNode}</p>
              </div>
            </details>
          );
        })}
      </div>
    </LegalContent>
  );
}

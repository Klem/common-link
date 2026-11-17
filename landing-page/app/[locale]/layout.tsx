import { Nunito_Sans, Inter, Lora } from 'next/font/google';
import { NextIntlClientProvider } from 'next-intl';
import { getMessages, setRequestLocale } from 'next-intl/server';
import { ThemeProvider } from '@/components/providers/ThemeProvider';
import { ThemeSwitcher } from '@/components/dev/ThemeSwitcher';
import { Analytics } from '@vercel/analytics/next';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import { locales } from '@/i18n/config';
import './globals.css';

export function generateStaticParams() {
  return locales.map((locale) => ({ locale }));
}

// Font requests mirror the mockup's Google Fonts links exactly (same weights and styles), so that
// the self-hosted files are the very same instances and glyphs render identically.
const nunitoSans = Nunito_Sans({
  subsets: ['latin'],
  weight: ['700', '800', '900'],
  variable: '--font-nunito-sans',
  display: 'swap',
});

const inter = Inter({
  subsets: ['latin'],
  weight: ['400', '500', '600', '700'],
  variable: '--font-inter',
  display: 'swap',
});

const lora = Lora({
  subsets: ['latin'],
  style: ['italic'],
  weight: ['500'],
  variable: '--font-lora',
  display: 'swap',
});

export default async function LocaleLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  setRequestLocale(locale);
  const messages = await getMessages();

  return (
    <html lang={locale} className={`${nunitoSans.variable} ${inter.variable} ${lora.variable}`}>
      <body>
        <NextIntlClientProvider messages={messages}>
          <ThemeProvider>
            {/* Root wrapper of every mockup template. Legal pages get it too (the mockup leaves their
                header and footer in the browser's default serif, an export bug not reproduced). */}
            <div style={{ fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif', color: '#171744', background: '#FFFFFF' }}>
              <Header />
              {children}
              <Footer />
            </div>
            <ThemeSwitcher />
          </ThemeProvider>
          <Analytics />
        </NextIntlClientProvider>
      </body>
    </html>
  );
}

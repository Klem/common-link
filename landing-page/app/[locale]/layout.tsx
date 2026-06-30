import { Nunito_Sans, Inter, Lora } from 'next/font/google';
import { NextIntlClientProvider } from 'next-intl';
import { getMessages } from 'next-intl/server';
import { ThemeProvider } from '@/components/providers/ThemeProvider';
import { ThemeSwitcher } from '@/components/dev/ThemeSwitcher';
import { Analytics } from '@vercel/analytics/next';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import './globals.css';

const nunitoSans = Nunito_Sans({
  subsets: ['latin'],
  weight: ['400', '600', '700', '800', '900'],
  variable: '--font-nunito-sans',
  display: 'swap',
});

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap',
});

const lora = Lora({
  subsets: ['latin'],
  style: ['normal', 'italic'],
  weight: ['400', '500', '600'],
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
  const messages = await getMessages();

  return (
    <html lang={locale} className={`${nunitoSans.variable} ${inter.variable} ${lora.variable}`}>
      <body>
        <NextIntlClientProvider messages={messages}>
          <ThemeProvider>
            <Header />
            {children}
            <Footer />
            <ThemeSwitcher />
          </ThemeProvider>
          <Analytics />
        </NextIntlClientProvider>
      </body>
    </html>
  );
}

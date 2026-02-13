import { Manrope } from 'next/font/google';
import localFont from 'next/font/local';
import { NextIntlClientProvider } from 'next-intl';
import { getMessages } from 'next-intl/server';
import { ThemeProvider } from '@/components/providers/ThemeProvider';
import { ThemeSwitcher } from '@/components/dev/ThemeSwitcher';
import { Header } from '@/components/layout/Header';
import { Footer } from '@/components/layout/Footer';
import './globals.css';

const manrope = Manrope({
  subsets: ['latin'],
  variable: '--font-manrope',
  display: 'swap',
});

const luciole = localFont({
  src: [
    { path: '../../public/fonts/Luciole-Regular.woff2', weight: '400', style: 'normal' },
    { path: '../../public/fonts/Luciole-Bold.woff2', weight: '700', style: 'normal' },
  ],
  variable: '--font-luciole',
  display: 'swap',
});

export default async function LocaleLayout({
  children,
  params: { locale },
}: {
  children: React.ReactNode;
  params: { locale: string };
}) {
  const messages = await getMessages();

  return (
    <html lang={locale} className={`${manrope.variable} ${luciole.variable}`}>
      <body>
        <NextIntlClientProvider messages={messages}>
          <ThemeProvider>
            <Header />
            {children}
            <Footer />
            <ThemeSwitcher />
          </ThemeProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}

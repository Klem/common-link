import { Manrope } from 'next/font/google';
import { NextIntlClientProvider } from 'next-intl';
import { getMessages } from 'next-intl/server';
import { ThemeProvider } from '@/components/providers/ThemeProvider';
import { ThemeSwitcher } from '@/components/dev/ThemeSwitcher';
import './globals.css';

const manrope = Manrope({
  subsets: ['latin'],
  variable: '--font-manrope',
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
    <html lang={locale} className={manrope.variable}>
      <body>
        <NextIntlClientProvider messages={messages}>
          <ThemeProvider>
            {children}
            <ThemeSwitcher />
          </ThemeProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}

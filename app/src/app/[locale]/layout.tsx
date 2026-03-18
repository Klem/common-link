import type { Metadata } from 'next';
import { NextIntlClientProvider } from 'next-intl';
import { getMessages } from 'next-intl/server';
import { notFound } from 'next/navigation';
import { routing } from '@/i18n/routing';
import { AuthProvider } from '@/providers/AuthProvider';
import { GoogleOAuthProvider } from '@/providers/GoogleOAuthProvider';
import { ThemeProvider } from '@/providers/ThemeProvider';
import { Toast } from '@/components/ui';
import { ThemeSwitcher } from '@/components/dev/ThemeSwitcher';
import '@/styles/globals.css';

export const metadata: Metadata = {
  title: 'CommonLink',
  description: 'Plateforme de philanthropie transparente',
};

interface LocaleLayoutProps {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}

export default async function LocaleLayout({ children, params }: LocaleLayoutProps) {
  const { locale } = await params;

  if (!routing.locales.includes(locale as 'fr' | 'en')) {
    notFound();
  }

  const messages = await getMessages();

  return (
    <html lang={locale}>
      <body>
        <NextIntlClientProvider messages={messages}>
          <ThemeProvider>
            <GoogleOAuthProvider>
              <AuthProvider>
                {children}
                <Toast />
                <ThemeSwitcher />
              </AuthProvider>
            </GoogleOAuthProvider>
          </ThemeProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}

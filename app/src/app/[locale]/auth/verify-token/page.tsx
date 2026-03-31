import { redirect } from 'next/navigation';
import { VerifyTokenScreen } from './VerifyTokenScreen';

interface VerifyTokenPageProps {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ token?: string; role?: string }>;
}

export default async function VerifyTokenPage({ params, searchParams }: VerifyTokenPageProps) {
  const { locale } = await params;
  const { token } = await searchParams;

  if (!token) {
    redirect(`/${locale}/login`);
  }

  return <VerifyTokenScreen token={token} />;
}

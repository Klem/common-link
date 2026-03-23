import { redirect } from 'next/navigation';

interface VerifyTokenPageProps {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ token?: string; role?: string }>;
}

export default async function VerifyTokenPage({ params, searchParams }: VerifyTokenPageProps) {
  const { locale } = await params;
  const { token, role } = await searchParams;

  const query = new URLSearchParams();
  if (token) query.set('token', token);
  if (role) query.set('role', role);
  const qs = query.toString();

  redirect(`/${locale}/login${qs ? `?${qs}` : ''}`);
}

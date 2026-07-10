import { redirect } from 'next/navigation';

interface AdminRootPageProps {
  params: Promise<{ locale: string }>;
}

export default async function AdminRootPage({ params }: AdminRootPageProps) {
  const { locale } = await params;
  redirect(`/${locale}/admin/verifications`);
}

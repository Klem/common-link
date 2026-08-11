import { redirect } from 'next/navigation';

interface ComplianceRootPageProps {
  params: Promise<{ locale: string }>;
}

export default async function ComplianceRootPage({ params }: ComplianceRootPageProps) {
  const { locale } = await params;
  redirect(`/${locale}/compliance/alerts`);
}

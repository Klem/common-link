import { LoginScreen } from './LoginScreen';

interface LoginPageProps {
  searchParams: Promise<{
    view?: string;
    role?: string;
    token?: string;
  }>;
}

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const params = await searchParams;

  const view = params.view === 'signup' ? 'signup' : 'login';
  const role = params.role === 'association' ? 'ASSOCIATION' : 'DONOR';
  const token = params.token ?? null;

  return <LoginScreen initialView={view} initialRole={role} magicLinkToken={token} />;
}

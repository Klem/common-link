import { ComplianceShell } from '@/components/compliance/ComplianceShell';

interface ComplianceLayoutProps {
  children: React.ReactNode;
}

export default function ComplianceLayout({ children }: ComplianceLayoutProps) {
  return <ComplianceShell>{children}</ComplianceShell>;
}

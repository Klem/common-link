'use client';

interface AuthCardProps {
  children: React.ReactNode;
  className?: string;
}

export function AuthCard({ children, className = '' }: AuthCardProps) {
  return (
    <div className={`auth-card ${className}`}>
      <div className="auth-card-gradient" />
      {children}
    </div>
  );
}

'use client';

interface AuthCardProps {
  children: React.ReactNode;
  className?: string;
}

export function AuthCard({ children, className = '' }: AuthCardProps) {
  return (
    <div
      className={`bg-bg-2 border border-border rounded-lg p-[26px_28px] relative overflow-hidden ${className}`}
    >
      {/* Top gradient line */}
      <div
        className="absolute top-0 left-0 right-0 h-px"
        style={{
          background: 'linear-gradient(90deg, transparent, rgba(0,184,154,.25), transparent)',
        }}
      />
      {children}
    </div>
  );
}

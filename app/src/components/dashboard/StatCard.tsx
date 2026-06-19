'use client';

interface StatCardProps {
  icon: string;
  label: React.ReactNode;
  value: string | number;
  variant?: 'teal' | 'coral' | 'amber' | 'indigo';
  subLabel?: string;
  onClick?: () => void;
}

export function StatCard({ icon, label, value, variant = 'teal', subLabel, onClick }: StatCardProps) {
  return (
    <div className="st" onClick={onClick} style={onClick ? { cursor: 'pointer' } : undefined}>
      <div className="st-top">
        <div className={`st-icon ${variant}`}>{icon}</div>
      </div>
      <div className="st-val">{value}</div>
      <div className="st-lbl">{label}</div>
      {subLabel && <div className="st-sub">{subLabel}</div>}
    </div>
  );
}

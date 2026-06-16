'use client';

interface TrendProps {
  value: string;
  direction: 'up' | 'down' | 'neutral';
}

interface StatCardProps {
  icon: string;
  label: string;
  value: string | number;
  trend?: TrendProps;
  variant?: 'teal' | 'coral' | 'amber' | 'indigo';
  /** Optional secondary label shown below the main label (e.g. milestone name). */
  subLabel?: string;
}

export function StatCard({ icon, label, value, trend, variant = 'teal', subLabel }: StatCardProps) {
  return (
    <div className="stat-card">
      <div className="stat-card-header">
        <span className="stat-card-label">{label}</span>
        <span className={`stat-card-icon ${variant}`}>{icon}</span>
      </div>
      <p className="stat-card-value">{value}</p>
      {trend && (
        <span className={`stat-card-trend${trend.direction !== 'neutral' ? ` ${trend.direction}` : ''}`}>
          {trend.value}
        </span>
      )}
      {subLabel && <p className="stat-card-sublabel">{subLabel}</p>}
    </div>
  );
}

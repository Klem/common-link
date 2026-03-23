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
}

const trendClasses: Record<TrendProps['direction'], string> = {
  up: 'bg-green/10 text-green',
  down: 'bg-red/10 text-red',
  neutral: 'bg-muted/20 text-text-2',
};

export function StatCard({ icon, label, value, trend }: StatCardProps) {
  return (
    <div className="bg-bg-2 border border-border rounded-[14px] p-[17px_19px] flex flex-col gap-[10px]">
      <span className="text-[20px] leading-none">{icon}</span>
      <div>
        <p className="font-display font-bold text-[22px] text-text leading-tight">{value}</p>
        <p className="text-[12px] text-text-2 mt-[2px]">{label}</p>
      </div>
      {trend && (
        <span className={`self-start text-[11px] font-display font-bold px-[7px] py-[2px] rounded-full ${trendClasses[trend.direction]}`}>
          {trend.value}
        </span>
      )}
    </div>
  );
}

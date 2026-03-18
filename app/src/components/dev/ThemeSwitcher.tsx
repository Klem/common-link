'use client';

import { useTheme } from '@/providers/ThemeProvider';

export function ThemeSwitcher() {
  if (process.env.NODE_ENV === 'production') return null;

  return <ThemeSwitcherUI />;
}

function ThemeSwitcherUI() {
  const { theme, themes, setTheme } = useTheme();

  return (
    <div className="fixed bottom-4 left-4 z-[9998] flex items-center gap-2 bg-bg-2 border border-border rounded-lg px-3 py-2 shadow-[0_4px_20px_rgba(0,0,0,.5)]">
      <span className="text-[11px] font-display font-bold text-muted uppercase tracking-[0.06em]">
        Theme
      </span>
      <select
        value={theme.name}
        onChange={(e) => setTheme(e.target.value)}
        className="bg-bg-3 border border-border text-text text-[12px] font-body rounded-md px-2 py-1 outline-none cursor-pointer transition-[border-color] duration-200 focus:border-green/40"
        aria-label="Sélectionner un thème"
      >
        {themes.map((t) => (
          <option key={t.name} value={t.name}>
            {t.label}
          </option>
        ))}
      </select>
    </div>
  );
}

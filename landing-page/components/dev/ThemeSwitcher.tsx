'use client';

import { useTheme } from '@/components/providers/ThemeProvider';

export function ThemeSwitcher() {
  const { themeName, setTheme, availableThemes } = useTheme();

  if (process.env.NODE_ENV === 'production') return null;

  return (
    <div className="fixed bottom-4 right-4 z-50 bg-white border border-border rounded-lg shadow-lg p-3">
      <label className="text-xs font-ui text-foreground-muted block mb-2">
        Thème (dev)
      </label>
      <select
        value={themeName}
        onChange={(e) => setTheme(e.target.value)}
        className="text-sm font-ui border border-border rounded-sm px-2 py-1"
      >
        {availableThemes.map((t) => (
          <option key={t.name} value={t.name}>
            {t.label}
          </option>
        ))}
      </select>
    </div>
  );
}

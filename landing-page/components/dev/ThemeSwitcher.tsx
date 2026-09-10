'use client';

import { useTheme } from '@/components/providers/ThemeProvider';

export function ThemeSwitcher() {
  const { themeName, setTheme, availableThemes } = useTheme();

  if (process.env.NODE_ENV === 'production') return null;

  return (
    <div
      style={{
        position: 'fixed',
        bottom: '16px',
        right: '16px',
        zIndex: 50,
        background: 'var(--white)',
        border: '1px solid var(--mist-lavender)',
        borderRadius: 'var(--radius-lg)',
        boxShadow: 'var(--shadow-lg)',
        padding: '12px',
      }}
    >
      <label
        style={{
          display: 'block',
          fontSize: '12px',
          color: 'var(--slate-lavender)',
          marginBottom: '8px',
        }}
      >
        Thème (dev)
      </label>
      <select
        value={themeName}
        onChange={(e) => setTheme(e.target.value)}
        style={{
          fontSize: '14px',
          border: '1px solid var(--mist-lavender)',
          borderRadius: 'var(--radius-sm)',
          padding: '4px 8px',
        }}
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

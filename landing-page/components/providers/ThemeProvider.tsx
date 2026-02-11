'use client';

import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { themes, Theme, defaultThemeName } from '@/lib/themes';

interface ThemeContextType {
  theme: Theme;
  themeName: string;
  setTheme: (name: string) => void;
  availableThemes: { name: string; label: string }[];
}

const ThemeContext = createContext<ThemeContextType | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [themeName, setThemeName] = useState(defaultThemeName);
  const theme = themes[themeName] || themes[defaultThemeName];

  useEffect(() => {
    const root = document.documentElement;
    Object.entries(theme.colors).forEach(([key, value]) => {
      root.style.setProperty(`--color-${key}`, value);
    });
    Object.entries(theme.radius).forEach(([key, value]) => {
      root.style.setProperty(`--radius-${key}`, value);
    });
    root.style.setProperty('--font-ui', theme.fonts.ui);
    root.style.setProperty('--font-body', theme.fonts.body);
  }, [theme]);

  return (
    <ThemeContext.Provider
      value={{
        theme,
        themeName,
        setTheme: setThemeName,
        availableThemes: Object.values(themes).map((t) => ({
          name: t.name,
          label: t.label,
        })),
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export const useTheme = () => {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
};

'use client';

import { createContext, useContext, useEffect, useState } from 'react';
import { type Theme, themes, defaultTheme } from '@/lib/themes';

interface ThemeContextValue {
  theme: Theme;
  themes: Theme[];
  setTheme: (name: string) => void;
}

const ThemeContext = createContext<ThemeContextValue>({
  theme: defaultTheme,
  themes,
  setTheme: () => {},
});

export function useTheme() {
  return useContext(ThemeContext);
}

function applyTheme(theme: Theme) {
  const root = document.documentElement;
  const c = theme.colors;
  root.style.setProperty('--color-bg',            c.bg);
  root.style.setProperty('--color-bg-2',          c.bgTwo);
  root.style.setProperty('--color-bg-3',          c.bgThree);
  root.style.setProperty('--color-border',        c.border);
  root.style.setProperty('--color-green',         c.green);
  root.style.setProperty('--color-green-dim',     c.greenDim);
  root.style.setProperty('--color-green-glow',    c.greenGlow);
  root.style.setProperty('--color-green-glow-dim',c.greenGlowDim);
  root.style.setProperty('--color-yellow',        c.yellow);
  root.style.setProperty('--color-cyan',          c.cyan);
  root.style.setProperty('--color-red',           c.red);
  root.style.setProperty('--color-text',          c.text);
  root.style.setProperty('--color-text-2',        c.textTwo);
  root.style.setProperty('--color-muted',         c.muted);
}

interface ThemeProviderProps {
  children: React.ReactNode;
}

export function ThemeProvider({ children }: ThemeProviderProps) {
  const [theme, setThemeState] = useState<Theme>(defaultTheme);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  const setTheme = (name: string) => {
    const found = themes.find((t) => t.name === name);
    if (found) setThemeState(found);
  };

  return (
    <ThemeContext.Provider value={{ theme, themes, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

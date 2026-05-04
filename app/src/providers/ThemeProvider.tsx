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
  document.documentElement.setAttribute('data-theme', theme.name);
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

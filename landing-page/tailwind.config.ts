import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./app/**/*.{ts,tsx}', './components/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: 'var(--color-primary)',
          dark: 'var(--color-primary-dark)',
          light: 'var(--color-primary-light)',
        },
        secondary: {
          DEFAULT: 'var(--color-secondary)',
          light: 'var(--color-secondary-light)',
          pale: 'var(--color-secondary-pale)',
        },
        accent: {
          DEFAULT: 'var(--color-accent)',
          light: 'var(--color-accent-light)',
        },
        background: {
          DEFAULT: 'var(--color-background)',
          alt: 'var(--color-background-alt)',
        },
        foreground: {
          DEFAULT: 'var(--color-text-body)',
          dark: 'var(--color-text-dark)',
          muted: 'var(--color-text-muted)',
        },
        border: {
          DEFAULT: 'var(--color-border)',
          light: 'var(--color-border-light)',
        },
        success: 'var(--color-success)',
        error: 'var(--color-error)',
      },
      borderRadius: {
        sm: 'var(--radius-sm)',
        md: 'var(--radius-md)',
        lg: 'var(--radius-lg)',
        xl: 'var(--radius-xl)',
      },
      fontFamily: {
        ui: ['var(--font-ui)', 'sans-serif'],
        body: ['var(--font-body)', 'serif'],
      },
      maxWidth: {
        container: '1120px',
        narrow: '780px',
      },
      boxShadow: {
        sm: '0 1px 3px rgba(26, 74, 90, 0.06)',
        md: '0 4px 16px rgba(26, 74, 90, 0.08)',
        lg: '0 8px 32px rgba(26, 74, 90, 0.1)',
        xl: '0 16px 48px rgba(26, 74, 90, 0.12)',
      },
    },
  },
  plugins: [],
};

export default config;

import { Theme } from './index';

export const forestTheme: Theme = {
  name: 'forest',
  label: 'Forest Green',
  colors: {
    primary: '#2d5a27',
    'primary-dark': '#1a3d16',
    'primary-light': '#3e7a36',
    secondary: '#6b9e3a',
    'secondary-light': '#8bbd5a',
    'secondary-pale': '#eef6e5',
    accent: '#c67b2f',
    'accent-light': '#daa24e',
    background: '#f9f7f2',
    'background-alt': '#f0ede5',
    'text-dark': '#1a2e16',
    'text-body': '#3a4a35',
    'text-muted': '#6a7a65',
    border: '#d4d0c6',
    'border-light': '#e6e2da',
    success: '#2d8a4e',
    error: '#c44536',
  },
  fonts: {
    ui: 'var(--font-manrope)',
    body: 'var(--font-luciole)',
  },
  radius: {
    sm: '6px',
    md: '10px',
    lg: '14px',
    xl: '22px',
  },
};

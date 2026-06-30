import { Theme } from './index';

export const lienCommunTheme: Theme = {
  name: 'lien-commun',
  label: 'CommonLink (défaut)',
  colors: {
    primary: '#32327D',
    'primary-dark': '#282866',
    'primary-light': '#3d3d8f',
    secondary: '#4ECDC4',
    'secondary-light': '#2FB3AA',
    'secondary-pale': '#e3f9f8',
    accent: '#FF6B5B',
    'accent-light': '#ff8a7e',
    background: '#FDF8F0',
    'background-alt': '#F5EFE4',
    'text-dark': '#171744',
    'text-body': '#171744',
    'text-muted': '#62627D',
    border: '#E6E4F4',
    'border-light': '#EFEDFA',
    success: '#34C759',
    error: '#E8453C',
  },
  fonts: {
    ui: 'var(--font-nunito-sans)',
    body: 'var(--font-inter)',
  },
  radius: {
    sm: '8px',
    md: '12px',
    lg: '16px',
    xl: '24px',
  },
};

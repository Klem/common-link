# CLAUDE.md — Projet Lien commun (Next.js)

> ## ⚠️ CONVENTION DE STYLE ACTUELLE — LIRE EN PREMIER
>
> **Ce document décrit plus bas une architecture Tailwind + multi-thèmes qui n'est
> plus celle du projet.** Tout ce qui parle de « classes Tailwind thémées »,
> « jamais de couleurs en dur », `tailwind.config.ts`, `bg-primary`, `font-ui`,
> `rounded-md` est **obsolète** et ne doit pas être suivi.
>
> ### Ce qui fait foi aujourd'hui
>
> 1. **La maquette `CommonLink UI V2 Julian.html` (racine du dépôt) est la source
>    de vérité du design.** Son bloc `<style>` complet est copié **verbatim** dans
>    `app/[locale]/globals.css`, entre les balises `MAQUETTE — DÉBUT/FIN DU BLOC
>    VERBATIM`. **Ne jamais retoucher les valeurs à l'intérieur de ce bloc.**
>    Seules adaptations autorisées, déjà appliquées et documentées en tête du
>    fichier : les 3 familles de police pointent vers les variables `next/font`,
>    et les règles `.page{display:none}` du navigateur SPA de la démo ont été
>    retirées.
>
> 2. **Les composants réutilisent le markup et les noms de classes de la
>    maquette** (`.hero-title`, `.why-card`, `.tarif-split`, `.legal-body`…),
>    y compris les `style="…"` inline convertis en `style={{}}`. On ne
>    « simplifie » pas l'arborescence : plusieurs sections sont stylées par des
>    sélecteurs d'éléments imbriqués (`.journey-steps h4`, `.why-card h3`),
>    supprimer un `div` les casse silencieusement.
>
> 3. **Tailwind a été retiré du projet** (`postcss.config.js`, plus de
>    `tailwind.config.ts`, plus de directives `@tailwind`). Ne pas le
>    réintroduire : il a causé deux régressions silencieuses coûteuses —
>    `border-none` dans une classe de base annulait `border-style` et rendait
>    invisibles **toutes** les bordures des boutons ; et les modificateurs
>    d'opacité (`bg-secondary/10`) rendent **transparent** quand la couleur est
>    une variable CSS hexadécimale, sans aucune erreur. Il générait aussi des
>    utilitaires pour des noms de classes absents de la maquette (`mb-20`),
>    écrasant le rendu attendu.
>
> 4. **Les textes restent dans `messages/fr.json`** via `next-intl`. Si un texte
>    de `fr.json` diverge de la maquette, **la maquette gagne**. La maquette
>    n'emploie que l'apostrophe droite `'` et pas la ligature `œ` — `fr.json` a
>    été aligné dessus.
>
> 5. **Le site est monolingue français.** `locales = ['fr']`, il n'y a plus de
>    `messages/en.json` ni de sélecteur de langue.
>
> ### Vérifier une modification
>
> Ne jamais se fier à l'œil : `scripts/compare-maquette.js` confronte la maquette
> et l'implémentation élément par élément (43 propriétés calculées + géométrie +
> texte). Toute PR touchant au rendu doit le laisser à zéro écart, hors écarts
> connus et documentés ci-dessous.
>
> ```bash
> npm run dev                                              # dans un terminal
> npm run compare:maquette p3 http://localhost:3001/associations
> npm run compare:maquette p1 http://localhost:3001/ -- --full   # détail complet
> ```
>
> Correspondance page maquette → route : `p1`→`/` · `p2`→`/donateurs` ·
> `p3`→`/associations` · `p6`→`/transparence` · `p7`→`/tarifs` ·
> `p13`→`/mentions-legales` · `p14`→`/conditions-generales-utilisation` ·
> `p15`→`/conditions-generales-utilisation-associations` · `p16`→`/contrat-type` ·
> `p17`→`/reclamations` · `p18`→`/contact` · `p19`→`/politique-confidentialite` ·
> `p20`→`/politique-cookies`.
>
> Si le comparateur signale soudain des dizaines d'écarts avec des polices
> `Times New Roman` et des liens bleus, ce n'est pas le code : le serveur de dev
> a corrompu son manifeste webpack. `rm -rf .next` puis relancer.
>
> **Écarts connus et assumés :**
> - Le logo de la barre de navigation est un `<a>` (maquette : `<div onclick>`) —
>   accessibilité, zéro différence visuelle.
> - `grid-2 mb-20` de la maquette rendu `grid-2` (page Donateurs) : `mb-20`
>   n'existe pas dans le CSS de la maquette et y vaut donc `margin-bottom:0`.

## GETTING STARTED

### Structure monorepo
```
commonlink/                     ← Racine monorepo
└── landing-page/               ← Tu es ici
    ├── CLAUDE.md               ← Ce fichier
    └── minisite-liencommun/    ← Référence HTML/CSS à reproduire
```

### Comment utiliser ce fichier avec Claude Code

1. **Ouvre Claude Code** dans le dossier `landing-page/`
   ```bash
   cd commonlink/landing-page
   claude
   ```

2. **Premier message à Claude Code :**
   ```
   Lis le fichier CLAUDE.md pour comprendre le projet.
   Le minisite HTML de référence est dans ./minisite-liencommun/
   Commence par le PROMPT 1.
   ```

3. **Exécute les prompts dans l'ordre** (1 → 2 → 3 → ... → 11)
   - Vérifie que chaque étape fonctionne avant de passer à la suivante
   - Teste visuellement avec `npm run dev`

4. **Pour chaque prompt**, Claude Code doit :
   - Lire le HTML/CSS de référence dans `./minisite-liencommun/`
   - Reproduire le design à l'identique
   - Utiliser les classes thémées (pas de couleurs en dur)
   - Utiliser les traductions (pas de texte en dur)

---

## TON RÔLE
Tu es développeur senior Next.js et expert UI/UX. Tu construis le site Lien commun en reproduisant **à l'identique** le minisite HTML/CSS existant, mais avec une architecture Next.js moderne et des composants réutilisables.

---

## LE PROJET

**Lien commun** — Plateforme de crypto-philanthropie qui restaure la confiance des donateurs grâce à la transparence sur l'utilisation des fonds.

### Pitch résumé
- Les associations s'enregistrent sur la plateforme
- Les donateurs donnent en euros
- Chaque don est tracé on-chain (blockchain) avec preuves vérifiables
- Les donateurs reçoivent un reçu fiscal numérique (NFT)
- Les associations dépensent en euros, chaque dépense est enregistrée

### Équipe
- **Julian Maïolino** — CEO
- **Colin Lalouette** — COO  
- **Clément Pignon** — CTO

---

## STACK TECHNIQUE

```
Framework:       Next.js 14+ (App Router)
Styling:         Tailwind CSS + CSS custom properties (design tokens)
Theming:         next-themes + CSS variables (multi-thèmes)
i18n:            next-intl (français par défaut, anglais en option)
Components:      React Server Components par défaut, Client Components si interactivité
Fonts:           Manrope (Google Fonts) + Luciole (self-hosted ou Google)
Deployment:      Vercel
```

---

## CHARTE GRAPHIQUE (STRICTE)

### Couleurs (design tokens)
```css
--teal: #1a4a5a;
--teal-dark: #0f3440;
--teal-light: #245a6c;
--seafoam: #5a9ea6;
--seafoam-light: #7dbac1;
--seafoam-pale: #e8f4f6;
--gold: #b8964e;
--gold-light: #d4b876;
--warm-white: #f8f6f2;
--warm-gray: #f0ede8;
--text-dark: #1a2a30;
--text-body: #3a4a50;
--text-muted: #7a8a90;
--border: #e0ddd6;
--border-light: #ece9e3;
```

### Typographies
- **Manrope** — UI (titres, boutons, navigation, labels)
- **Luciole** — Body text (paragraphes, lecture longue, accessibilité)

### Style
- Clean, minimal, institutionnel, rassurant
- **Pas de jargon blockchain** côté utilisateur
- Coins arrondis doux (radius: 6px, 10px, 16px, 24px)
- Ombres légères et subtiles

---

## SYSTÈME DE THEMING (Multi-thèmes)

L'architecture permet de switcher facilement entre différentes chartes graphiques pour tester des looks alternatifs.

### Principe
- Tous les styles utilisent des **CSS custom properties** (jamais de couleurs en dur)
- Les thèmes sont définis dans des fichiers séparés
- Un ThemeProvider permet de changer de thème à la volée
- Tailwind utilise ces variables via `theme.extend.colors`

### Architecture des thèmes

```
lib/
└── themes/
    ├── index.ts            ← Export des thèmes + types
    ├── lien-commun.ts      ← Thème par défaut (actuel)
    ├── ocean.ts            ← Variante bleue profonde
    ├── forest.ts           ← Variante verte nature
    └── minimal.ts          ← Variante noir/blanc épurée
```

### Définition d'un thème (exemple)

```ts
// lib/themes/lien-commun.ts
export const lienCommunTheme = {
  name: 'lien-commun',
  label: 'Lien Commun (défaut)',
  colors: {
    primary: '#1a4a5a',
    'primary-dark': '#0f3440',
    'primary-light': '#245a6c',
    secondary: '#5a9ea6',
    'secondary-light': '#7dbac1',
    'secondary-pale': '#e8f4f6',
    accent: '#b8964e',
    'accent-light': '#d4b876',
    background: '#f8f6f2',
    'background-alt': '#f0ede8',
    'text-dark': '#1a2a30',
    'text-body': '#3a4a50',
    'text-muted': '#7a8a90',
    border: '#e0ddd6',
    'border-light': '#ece9e3',
    success: '#3a9e6e',
    error: '#d9534f',
  },
  fonts: {
    ui: 'Manrope',
    body: 'Luciole',
  },
  radius: {
    sm: '6px',
    md: '10px',
    lg: '16px',
    xl: '24px',
  },
} as const;

export type Theme = typeof lienCommunTheme;
```

### Exemple de thème alternatif

```ts
// lib/themes/ocean.ts
export const oceanTheme: Theme = {
  name: 'ocean',
  label: 'Ocean Deep',
  colors: {
    primary: '#0c4a6e',
    'primary-dark': '#082f49',
    'primary-light': '#0369a1',
    secondary: '#06b6d4',
    'secondary-light': '#22d3ee',
    'secondary-pale': '#ecfeff',
    accent: '#f59e0b',
    'accent-light': '#fbbf24',
    background: '#f0f9ff',
    'background-alt': '#e0f2fe',
    // ... etc
  },
  fonts: {
    ui: 'Inter',
    body: 'Source Serif Pro',
  },
  radius: {
    sm: '4px',
    md: '8px',
    lg: '12px',
    xl: '20px',
  },
};
```

### ThemeProvider

```tsx
// components/providers/ThemeProvider.tsx
'use client';

import { createContext, useContext, useState, useEffect } from 'react';
import { themes, Theme } from '@/lib/themes';

interface ThemeContextType {
  theme: Theme;
  themeName: string;
  setTheme: (name: string) => void;
  availableThemes: { name: string; label: string }[];
}

const ThemeContext = createContext<ThemeContextType | null>(null);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [themeName, setThemeName] = useState('lien-commun');
  const theme = themes[themeName] || themes['lien-commun'];

  useEffect(() => {
    // Injecter les CSS variables
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
        availableThemes: Object.values(themes).map(t => ({ 
          name: t.name, 
          label: t.label 
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
```

### Configuration Tailwind avec variables CSS

```ts
// tailwind.config.ts
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
    },
  },
  plugins: [],
};

export default config;
```

### Composant ThemeSwitcher (dev/test)

```tsx
// components/dev/ThemeSwitcher.tsx
'use client';

import { useTheme } from '@/components/providers/ThemeProvider';

export function ThemeSwitcher() {
  const { themeName, setTheme, availableThemes } = useTheme();

  // Afficher uniquement en développement
  if (process.env.NODE_ENV === 'production') return null;

  return (
    <div className="fixed bottom-4 right-4 z-50 bg-white border border-border rounded-lg shadow-lg p-3">
      <label className="text-xs font-ui text-foreground-muted block mb-2">
        🎨 Thème (dev)
      </label>
      <select
        value={themeName}
        onChange={(e) => setTheme(e.target.value)}
        className="text-sm font-ui border border-border rounded px-2 py-1"
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
```

### Usage dans les composants

```tsx
// Utiliser les classes Tailwind avec les variables
<button className="bg-primary text-white hover:bg-primary-light rounded-md">
  Click me
</button>

<div className="bg-background-alt border border-border rounded-lg">
  <h2 className="font-ui text-foreground-dark">Titre</h2>
  <p className="font-body text-foreground">Contenu</p>
</div>
```

### Règles importantes

1. **Jamais de couleurs en dur** dans les composants — toujours `bg-primary`, `text-foreground`, etc.
2. **Jamais de font-family en dur** — toujours `font-ui` ou `font-body`
3. **Jamais de border-radius en dur** — toujours `rounded-sm`, `rounded-md`, etc.
4. Le ThemeSwitcher n'apparaît qu'en mode développement
5. Le thème par défaut (`lien-commun`) est chargé si aucun thème n'est sélectionné

---

## ARCHITECTURE NEXT.JS (Monorepo)

Le projet est dans un monorepo. Le CLAUDE.md est à la racine de `landing-page/`.

```
commonlink/                         ← Racine monorepo
└── landing-page/                   ← CE PROJET (Next.js)
    ├── CLAUDE.md                   ← Ce fichier
    ├── minisite-liencommun/        ← Référence HTML/CSS
    ├── app/
    │   └── [locale]/
    │       ├── layout.tsx          ← Layout avec locale + ThemeProvider
    │       ├── page.tsx            ← Landing page (index)
    │       ├── partenaires/        ← Route FR
    │       │   └── page.tsx
    │       ├── partners/           ← Route EN (même composant)
    │       │   └── page.tsx
    │       ├── associations/
    │       │   └── page.tsx
    │       ├── donateurs/          ← Route FR
    │       │   └── page.tsx
    │       ├── donors/             ← Route EN
    │       │   └── page.tsx
    │       └── globals.css
    ├── components/
    │   ├── layout/
    │   │   ├── Header.tsx          ← Header + nav + LanguageSwitcher
    │   │   └── Footer.tsx
    │   ├── ui/
    │   │   ├── Button.tsx
    │   │   ├── Badge.tsx
    │   │   ├── Card.tsx
    │   │   ├── SectionTitle.tsx
    │   │   ├── FormInput.tsx
    │   │   └── LanguageSwitcher.tsx
    │   ├── sections/
    │   │   ├── Hero.tsx
    │   │   ├── PageHero.tsx
    │   │   ├── Features.tsx
    │   │   ├── Steps.tsx
    │   │   ├── FAQ.tsx
    │   │   └── ContactForm.tsx
    │   ├── associations/
    │   │   ├── SearchBox.tsx
    │   │   ├── AssociationCard.tsx
    │   │   └── RegistrationForm.tsx
    │   ├── providers/
    │   │   └── ThemeProvider.tsx
    │   └── dev/
    │       └── ThemeSwitcher.tsx
    ├── i18n/
    │   ├── config.ts
    │   ├── request.ts
    │   └── navigation.ts
    ├── messages/
    │   ├── fr.json
    │   └── en.json
    ├── lib/
    │   ├── themes/
    │   │   ├── index.ts
    │   │   ├── lien-commun.ts
    │   │   ├── ocean.ts
    │   │   └── forest.ts
    │   ├── api.ts
    │   └── utils.ts
    ├── public/
    │   └── fonts/
    ├── middleware.ts
    ├── tailwind.config.ts
    ├── next.config.js
    └── package.json
```

**Important :** Tous les chemins dans ce document sont relatifs à `landing-page/`.

---

## PHILOSOPHIE DE CODE

### 1. Composants réutilisables
Chaque élément visuel répété = un composant. Exemples :
- `<Button variant="primary" size="lg">` 
- `<Card variant="feature">` ou `<Card variant="preview">`
- `<SectionTitle>Titre ici</SectionTitle>`

### 2. Props typées (TypeScript)
```tsx
interface ButtonProps {
  variant: 'primary' | 'secondary' | 'accent';
  size?: 'default' | 'lg';
  children: React.ReactNode;
  onClick?: () => void;
  href?: string;
}
```

### 3. Server Components par défaut
Utiliser `'use client'` uniquement si :
- useState, useEffect nécessaires
- Event handlers (onClick, onChange)
- Browser APIs

### 4. Design tokens via Tailwind
Étendre Tailwind avec les couleurs de la charte dans `tailwind.config.ts` :
```ts
colors: {
  teal: { DEFAULT: '#1a4a5a', dark: '#0f3440', light: '#245a6c' },
  seafoam: { DEFAULT: '#5a9ea6', light: '#7dbac1', pale: '#e8f4f6' },
  gold: { DEFAULT: '#b8964e', light: '#d4b876' },
  // etc.
}
```

### 5. Accessibilité
- `aria-label` sur navigation et boutons icônes
- Sémantique HTML5 (header, main, section, nav, footer)
- Focus visible sur tous les éléments interactifs

---

## PAGES À REPRODUIRE

### 1. Landing (`/`)
- Hero avec badge, titre, sous-titre, CTA, visuel "journal des dépenses"
- Section "Un constat partagé" (2 cards)
- Section "Ce que Lien commun apporte" (3 feature cards)
- Section "Comment ça marche" (3 steps)
- Section "Garantie traçabilité"
- Section "Où en sommes-nous" (3 status cards)
- FAQ (6 questions)
- Footer

### 2. Partenaires (`/partenaires`)
- Page hero
- Section problème marché (stats 44%, 53%)
- Section vision/mission
- Section architecture (schéma)
- Section rôle SG Forge
- Section équipe (3 membres)
- Section roadmap
- Formulaire de contact

### 3. Associations (`/associations`)
- Page hero
- Section bénéfices (3 cards)
- Moteur de recherche SIRENE
  - Input avec debounce 300ms
  - Autocomplete dropdown
  - Grid de résultats (AssociationCard)
  - État "sélectionné" avec formulaire d'inscription
- API : `https://recherche-entreprises.api.gouv.fr/search`
- Filtre `nature_juridique`: `9210,9220,9221,9222,9223,9224,9230,9240,9260,9300`

### 4. Donateurs (`/donateurs`)
- Page hero
- Section "Ce que vous verrez" (3 preview cards avec icônes)
- Dashboard mockup preview
- Formulaire inscription (OAuth Google/Apple + email/mdp)
- FAQ donateurs (5 questions)
- CTA final

---

## RÉFÉRENCE : MINISITE HTML EXISTANT

Le minisite statique complet est dans le dossier `./minisite-liencommun/` :
- `index.html` — Landing
- `partenaires.html` — Page partenaires
- `associations.html` — Page associations + recherche SIRENE
- `donateurs.html` — Page donateurs
- `styles.css` — Design system complet (RÉFÉRENCE pour les styles)
- `main.js` — Logique header/menu mobile

**Le CSS existant est la source de vérité** pour toutes les valeurs (spacing, radius, shadows, etc.).

---

## SEO (chaque page)

```tsx
export const metadata: Metadata = {
  title: 'Titre — Lien commun',
  description: 'Description...',
  keywords: ['transparence', 'dons', 'associations', ...],
  openGraph: {
    title: '...',
    description: '...',
    type: 'website',
  },
};
```

---

## DOCUMENTS DE RÉFÉRENCE

- `SGForge_-_CommonLink.pdf` — Pitch deck complet
- `minisite-liencommun/` — Code HTML/CSS/JS de référence
- `nexus_associations_search.html` — Logique API SIRENE originale

---

# PROMPTS CLAUDE CODE — PAS À PAS

Utilise ces prompts dans l'ordre pour construire le site avec Claude Code en local.

---

## PROMPT 1 — Initialisation du projet + Theming + i18n

```
Initialise un projet Next.js 14 avec App Router dans le dossier courant (landing-page/).

Context : On est dans un monorepo. Ce dossier landing-page/ contient le site vitrine.
Le minisite HTML de référence est dans ./minisite-liencommun/

Stack :
- Next.js 14+ (App Router, TypeScript)
- Tailwind CSS
- next-intl (internationalisation)
- Google Fonts : Manrope + Luciole

---

## 1. SYSTÈME DE THEMING

Crée une architecture de theming flexible permettant de changer de charte graphique facilement.

### Crée lib/themes/index.ts avec le type Theme :
```ts
export interface Theme {
  name: string;
  label: string;
  colors: {
    primary: string;
    'primary-dark': string;
    'primary-light': string;
    secondary: string;
    'secondary-light': string;
    'secondary-pale': string;
    accent: string;
    'accent-light': string;
    background: string;
    'background-alt': string;
    'text-dark': string;
    'text-body': string;
    'text-muted': string;
    border: string;
    'border-light': string;
    success: string;
    error: string;
  };
  fonts: { ui: string; body: string };
  radius: { sm: string; md: string; lg: string; xl: string };
}
```

### Crée lib/themes/lien-commun.ts (thème par défaut) :
- primary: #1a4a5a, secondary: #5a9ea6, accent: #b8964e
- Fonts: Manrope / Luciole
- Radius: 6px, 10px, 16px, 24px

### Crée 2 thèmes alternatifs : ocean.ts et forest.ts

### Crée components/providers/ThemeProvider.tsx :
- Context avec theme, setTheme, availableThemes
- useEffect qui injecte les CSS variables

### Crée components/dev/ThemeSwitcher.tsx :
- Dropdown fixé en bas à droite (dev only)

### Configure Tailwind avec CSS variables :
- bg-primary → var(--color-primary)
- font-ui → var(--font-ui)
- rounded-md → var(--radius-md)

---

## 2. SYSTÈME i18n (next-intl)

### Installe next-intl :
```bash
npm install next-intl
```

### Crée i18n/config.ts :
```ts
export const locales = ['fr', 'en'] as const;
export const defaultLocale = 'fr' as const;
export type Locale = (typeof locales)[number];

export const pathnames = {
  '/': '/',
  '/partenaires': { fr: '/partenaires', en: '/partners' },
  '/associations': { fr: '/associations', en: '/associations' },
  '/donateurs': { fr: '/donateurs', en: '/donors' },
};
```

### Crée i18n/request.ts :
```ts
import { getRequestConfig } from 'next-intl/server';
export default getRequestConfig(async ({ locale }) => ({
  messages: (await import(`../messages/${locale}.json`)).default,
}));
```

### Crée i18n/navigation.ts :
- Export Link, useRouter, usePathname localisés

### Crée middleware.ts :
- Détection de langue avec localePrefix: 'as-needed'

### Crée messages/fr.json et messages/en.json :
- Structure : metadata, nav, hero, features, steps, faq, footer, common
- FR = complet, EN = traduction

### Crée components/ui/LanguageSwitcher.tsx :
- Boutons FR / EN
- Style : actif = bg-primary, inactif = text-muted

### Configure next.config.js avec le plugin next-intl

### Structure app/ avec [locale] :
```
app/[locale]/
├── layout.tsx
├── page.tsx
├── partenaires/page.tsx
├── partners/page.tsx
├── associations/page.tsx
├── donateurs/page.tsx
└── donors/page.tsx
```

---

## 3. GLOBALS.CSS

- CSS variables par défaut (thème lien-commun)
- Reset basique
- Styles de base

---

## 4. LAYOUT RACINE

app/[locale]/layout.tsx :
- Wrap avec ThemeProvider
- Configure les fonts Google
- Inclut ThemeSwitcher (dev) dans le body
- Reçoit `locale` en param et configure next-intl

---

IMPORTANT pour tous les composants :
- Classes Tailwind thémées uniquement (pas de couleurs en dur)
- Textes via useTranslations() (pas de texte en dur)
```

---

## PROMPT 2 — Composants UI de base

```
Crée les composants UI de base dans components/ui/ en te basant sur le fichier styles.css du minisite (je te le fournis).

IMPORTANT : Utilise UNIQUEMENT les classes Tailwind thémées (pas de couleurs en dur).

Composants à créer :

1. Button.tsx
   - Variants : primary (bg-primary), secondary (bg-white border-border), accent (bg-secondary)
   - Sizes : default, lg
   - Peut être un <button> ou un <Link>
   - Hover states avec primary-light, secondary-light, etc.

2. Badge.tsx
   - Variants : hero (bg-secondary-pale text-secondary), status-verified (bg-success/10 text-success), status-pending (bg-accent/15 text-accent), status-upcoming (bg-background-alt text-foreground-muted)

3. SectionTitle.tsx
   - Titre h2 centré avec font-ui text-foreground-dark
   - Underline avec bg-accent (48px width, 3px height)

4. Card.tsx
   - Variants : default, feature (avec icon slot), preview (avec top gradient bar primary→secondary), status
   - Utilise bg-white, border-border, rounded-lg, etc.

5. FormInput.tsx
   - Input avec label
   - Border, focus states, placeholder colors via variables

Exemple de syntaxe correcte :
```tsx
// ✅ Correct
<button className="bg-primary hover:bg-primary-light text-white rounded-md font-ui">

// ❌ Incorrect (couleur en dur)
<button className="bg-[#1a4a5a] hover:bg-[#245a6c]">
```

Voici le CSS de référence : [COLLER styles.css]
```

---

## PROMPT 3 — Layout (Header + Footer)

```
Crée le Header et Footer dans components/layout/ en reproduisant exactement le minisite.

Header.tsx (Client Component) :
- Sticky avec backdrop-blur
- Logo SVG (chaîne primary/secondary) + texte "Lien commun" → lien vers /
- Navigation desktop : utilise useTranslations('nav') pour les labels
  - t('partners') → lien localisé vers /partenaires ou /partners
  - t('associations') → /associations
  - t('donors') → /donateurs ou /donors
- LanguageSwitcher (boutons FR/EN) à droite de la nav
- Menu hamburger mobile (state open/close avec animation)
- Classe .scrolled ajoutée au scroll (shadow)
- Le lien actif a un underline accent

Footer.tsx :
- 3 colonnes : Brand, Navigation, Légal
- Tous les textes via useTranslations('footer')
- Copyright dynamique

IMPORTANT :
- Utiliser Link de '@/i18n/navigation' (pas de next/link direct)
- Tous les textes traduits via useTranslations()
- Classes Tailwind thémées uniquement

Voici le HTML de référence : [COLLER section header de index.html]
```

---

## PROMPT 4 — Page Landing (index)

```
Crée la page d'accueil app/page.tsx en reproduisant index.html du minisite.

Sections à créer (dans components/sections/) :

1. Hero.tsx — Badge "Traçabilité vérifiable", titre, sous-titre, CTA, visuel "Journal des dépenses"

2. Constat.tsx — "Un constat partagé" avec 2 cards (donateurs / associations)

3. Features.tsx — "Ce que Lien commun apporte" avec 3 feature cards (Transparence, Preuves, Simplicité)

4. Steps.tsx — "Comment ça marche" avec 3 étapes numérotées

5. Garantie.tsx — Section texte centré sur la traçabilité

6. Status.tsx — "Où en sommes-nous" avec 3 status cards

7. FAQ.tsx — 6 questions avec <details> accordion

Utilise les composants UI créés précédemment.

Voici le HTML de référence : [COLLER index.html]
```

---

## PROMPT 5 — Page Partenaires

```
Crée la page app/partenaires/page.tsx en reproduisant partenaires.html.

Sections :
1. PageHero — Badge "Partenaires & Investisseurs", titre, description
2. Problème marché — 2 stat cards (44% transparence, 53% difficultés financières)
3. Vision/Mission — 2 colonnes
4. Architecture — Schéma du flux (peut être une image ou un composant)
5. Rôle SG Forge — 3 cards (Conformité, Gestion fonds, Transparence)
6. Équipe — 3 cards membres avec photos placeholder
7. Roadmap — Timeline
8. ContactForm — Formulaire avec nom, email, organisation, type partenariat (select), message

Le formulaire est un Client Component.

Voici le HTML de référence : [COLLER partenaires.html]
```

---

## PROMPT 6 — Page Associations (partie 1 : structure)

```
Crée la page app/associations/page.tsx avec la structure de base.

Sections :
1. PageHero — "Offrez la transparence à vos donateurs"
2. Bénéfices — 3 feature cards (Attirer donateurs, Simplifier reporting, Zéro technique)
3. Section inscription avec placeholder pour le moteur de recherche

Ne code pas encore le moteur de recherche, juste la structure de la page.

Voici le HTML de référence : [COLLER début de associations.html jusqu'à la section recherche]
```

---

## PROMPT 7 — Page Associations (partie 2 : recherche SIRENE)

```
Crée le moteur de recherche SIRENE dans components/associations/.

Fichiers à créer :

1. lib/api.ts
   - Fonction searchAssociations(query: string) qui appelle :
     https://recherche-entreprises.api.gouv.fr/search?q={query}&per_page=12&nature_juridique=9210,9220,9221,9222,9223,9224,9230,9240,9260,9300

2. components/associations/SearchBox.tsx (Client Component)
   - Input avec debounce 300ms
   - Bouton "Rechercher"
   - Gère le state de recherche

3. components/associations/AssociationCard.tsx
   - Avatar avec initiale
   - Nom, statut (Active/Cessée), ville, CP, SIREN, date création
   - Bouton "Sélectionner" (disabled si cessée)

4. components/associations/AssociationSearch.tsx (Client Component)
   - Orchestre SearchBox + résultats
   - État : idle, loading, results, selected
   - Quand association sélectionnée → affiche RegistrationForm

5. components/associations/RegistrationForm.tsx
   - Affiche l'association sélectionnée
   - Champs : nom responsable, email, description
   - Bouton "Changer" pour revenir à la recherche
   - Submit → message de succès

Voici le JS de référence : [COLLER le script de associations.html]
```

---

## PROMPT 8 — Page Donateurs

```
Crée la page app/donateurs/page.tsx en reproduisant donateurs.html.

Sections :

1. PageHero — "Donnez en toute confiance"

2. ValuePreview — 3 preview cards avec icônes SVG :
   - Suivi en temps réel (icône pulse)
   - Reçu fiscal numérique (icône document + check)
   - Historique vérifié (icône horloge)

3. DashboardMockup — Aperçu visuel du tableau de bord
   - Header avec avatar "CL", "Bonjour, Claire"
   - 3 stats (1 250 €, 100%, 3 reçus)
   - Liste activités avec badges Vérifié/En cours

4. SignupForm (Client Component)
   - Boutons OAuth (Google, Apple) — placeholder onclick
   - Séparateur "ou par email"
   - Formulaire : prénom, email, mot de passe
   - Note sur infos fiscales
   - Submit → message succès

5. FAQ — 5 questions donateurs

6. CTA final

Voici le HTML de référence : [COLLER donateurs.html]
```

---

## PROMPT 9 — SEO et Metadata

```
Ajoute les metadata SEO à chaque page en utilisant les traductions.

Crée une fonction generateMetadata qui utilise getTranslations :

```tsx
import { getTranslations } from 'next-intl/server';

export async function generateMetadata({ params: { locale } }) {
  const t = await getTranslations({ locale, namespace: 'metadata' });
  return {
    title: t('title'),
    description: t('description'),
    // ...
  };
}
```

Pages à configurer :
- app/[locale]/page.tsx (landing)
- app/[locale]/partenaires/page.tsx et partners/page.tsx
- app/[locale]/associations/page.tsx
- app/[locale]/donateurs/page.tsx et donors/page.tsx

Inclus pour chaque : title, description, openGraph, keywords.
Ajoute les balises hreflang pour le SEO multilingue.
```

---

## PROMPT 9.5 — Traductions complètes

```
Complète les fichiers de traduction messages/fr.json et messages/en.json.

Assure-toi que TOUTES les sections sont traduites :

1. metadata (title, description pour chaque page)
2. nav (partners, associations, donors)
3. hero (landing page)
4. constat (landing page)
5. features (landing page)
6. steps (landing page)
7. guarantee (landing page)
8. status (landing page)
9. faq.landing (6 questions landing)
10. partnersPage (toute la page partenaires)
11. associationsPage (toute la page associations)
    - hero, benefits, search placeholders, form labels, success messages
12. donorsPage (toute la page donateurs)
    - hero, valuePreview, dashboard mockup, signup form, faq
13. footer
14. common (boutons, labels réutilisables)

Structure recommandée :
```json
{
  "metadata": { ... },
  "nav": { ... },
  "landing": {
    "hero": { ... },
    "constat": { ... },
    "features": { ... },
    "steps": { ... },
    "guarantee": { ... },
    "status": { ... },
    "faq": { ... }
  },
  "partners": {
    "hero": { ... },
    "problem": { ... },
    "vision": { ... },
    "architecture": { ... },
    "sgforge": { ... },
    "team": { ... },
    "roadmap": { ... },
    "contact": { ... }
  },
  "associations": {
    "hero": { ... },
    "benefits": { ... },
    "search": { ... },
    "form": { ... }
  },
  "donors": {
    "hero": { ... },
    "preview": { ... },
    "dashboard": { ... },
    "signup": { ... },
    "faq": { ... }
  },
  "footer": { ... },
  "common": { ... }
}
```

Le fichier FR est la source de vérité. 
Traduis ensuite EN en gardant la même structure.
Vérifie qu'aucun texte n'est en dur dans les composants.
```

---

## PROMPT 10 — Revue finale et polish

```
Fais une revue complète du site :

1. Vérifie que toutes les pages correspondent visuellement au minisite HTML de référence
2. Vérifie la responsivité mobile (breakpoint 768px)
3. Vérifie les hover states sur tous les éléments interactifs
4. Vérifie l'accessibilité (aria-labels, focus visible, sémantique HTML)
5. Vérifie que les liens de navigation fonctionnent
6. Vérifie les animations (menu mobile, hover cards)
7. Vérifie qu'AUCUNE couleur n'est en dur dans les composants (tout doit utiliser les classes thémées)
8. Teste le ThemeSwitcher en dev : le site doit s'adapter correctement aux différents thèmes

Liste-moi les différences trouvées et corrige-les.
```

---

## PROMPT 11 — Créer un nouveau thème (optionnel)

```
Crée un nouveau thème pour tester un look alternatif.

Nom du thème : [NOM]
Description : [DESCRIPTION DU STYLE VOULU]

Crée le fichier lib/themes/[nom].ts avec :
1. Une palette de couleurs cohérente (primary, secondary, accent, backgrounds, texts, borders)
2. Des fonts appropriées au style (disponibles sur Google Fonts)
3. Des radius adaptés (plus arrondis = friendly, plus carrés = corporate)

Ajoute-le à l'export dans lib/themes/index.ts.

Teste le rendu sur toutes les pages et ajuste si nécessaire.

Exemples de thèmes possibles :
- "corporate" : bleu marine, gris, rouge accent, Inter/Source Serif, radius carrés
- "playful" : violet/rose, jaune accent, Nunito/Quicksand, radius très arrondis
- "eco" : vert forêt, beige, orange terre, DM Sans/Lora, radius moyens
- "luxury" : noir/or, crème, Playfair Display/Montserrat, radius subtils
- "tech" : bleu électrique, gris foncé, cyan accent, Space Grotesk/IBM Plex, radius nets
```

---

## NOTES POUR CLAUDE CODE

### Règles de theming (CRITIQUES)

1. **JAMAIS de couleurs en dur** dans les composants
   ```tsx
   // ❌ INTERDIT
   className="bg-[#1a4a5a] text-[#3a4a50]"
   
   // ✅ CORRECT
   className="bg-primary text-foreground"
   ```

2. **JAMAIS de font-family en dur**
   ```tsx
   // ❌ INTERDIT
   className="font-['Manrope']"
   
   // ✅ CORRECT
   className="font-ui"
   ```

3. **JAMAIS de border-radius en dur**
   ```tsx
   // ❌ INTERDIT
   className="rounded-[10px]"
   
   // ✅ CORRECT
   className="rounded-md"
   ```

4. **Les ombres peuvent rester en dur** (elles sont moins impactantes pour le theming)

### Règles i18n (CRITIQUES)

1. **JAMAIS de texte en dur** dans les composants
   ```tsx
   // ❌ INTERDIT
   <h1>La transparence sur l'usage des dons</h1>
   
   // ✅ CORRECT
   const t = useTranslations('hero');
   <h1>{t('title')}</h1>
   ```

2. **Utiliser Link de i18n/navigation** (pas next/link)
   ```tsx
   // ❌ INTERDIT
   import Link from 'next/link';
   <Link href="/partenaires">
   
   // ✅ CORRECT
   import { Link } from '@/i18n/navigation';
   <Link href="/partenaires">  // sera traduit automatiquement
   ```

3. **Clés de traduction descriptives**
   ```json
   // ❌ INTERDIT
   { "t1": "Bienvenue", "t2": "Cliquez ici" }
   
   // ✅ CORRECT
   { "hero": { "title": "Bienvenue" }, "cta": { "primary": "Cliquez ici" } }
   ```

4. **Tester les 2 langues** — les textes EN sont souvent plus longs/courts

### Workflow recommandé

1. **Toujours se référer au CSS du minisite** pour les valeurs exactes (spacing, shadows)
2. **Tester visuellement** chaque composant avec le ThemeSwitcher
3. **Tester les 2 langues** avec le LanguageSwitcher
4. **Garder le code DRY** : si un pattern se répète, créer un composant
5. **TypeScript strict** : typer toutes les props
6. **Commits atomiques** : un commit par composant/section

### Checklist avant chaque commit

- [ ] Aucune couleur en dur (tout via classes thémées)
- [ ] Aucune font en dur (tout via font-ui / font-body)
- [ ] Aucun texte en dur (tout via useTranslations)
- [ ] Testé avec ThemeSwitcher (au moins 2 thèmes)
- [ ] Testé avec LanguageSwitcher (FR et EN)
- [ ] Responsive OK (mobile 768px)
- [ ] Accessibilité (aria-labels, focus visible)


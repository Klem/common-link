/**
 * Google Consent Mode v2 — compliance layer for `/lp/[widgetToken]`, the only surface currently
 * covered (see `.tasks/todo.md`: the embed iframe and its return page still load `gtmContainerId`
 * unconditionally, left out of scope on purpose).
 *
 * Required by Google's EU User Consent Policy for any Google Ads / Google Analytics tag reachable
 * through an association's `gtmContainerId` — Ad Grants accounts are ordinary Google Ads accounts,
 * bound by the same policy. Non-compliance escalates to suspension of remarketing and conversion
 * measurement for that account, not just a CNIL risk.
 */

const CONSENT_STORAGE_PREFIX = 'cl-consent-';

/** CNIL guidance: re-ask for consent at most every 13 months. */
const CONSENT_MAX_AGE_MS = 13 * 30 * 24 * 60 * 60 * 1000;

export type ConsentChoice = 'granted' | 'denied';

interface StoredConsent {
  choice: ConsentChoice;
  timestamp: number;
}

function consentStorageKey(widgetToken: string): string {
  return `${CONSENT_STORAGE_PREFIX}${widgetToken}`;
}

/** Reads a still-valid stored choice for this widgetToken, or null if none/expired/unavailable. */
export function readStoredConsent(widgetToken: string): ConsentChoice | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(consentStorageKey(widgetToken));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<StoredConsent>;
    if (parsed.choice !== 'granted' && parsed.choice !== 'denied') return null;
    if (typeof parsed.timestamp !== 'number' || Date.now() - parsed.timestamp > CONSENT_MAX_AGE_MS) {
      return null;
    }
    return parsed.choice;
  } catch {
    return null;
  }
}

export function writeStoredConsent(widgetToken: string, choice: ConsentChoice): void {
  if (typeof window === 'undefined') return;
  try {
    const value: StoredConsent = { choice, timestamp: Date.now() };
    window.localStorage.setItem(consentStorageKey(widgetToken), JSON.stringify(value));
  } catch {
    // Storage unavailable (private mode, quota) — the banner just reappears next visit.
  }
}

/** Pushes the user's choice to Google via `gtag('consent','update', ...)`. No-op if gtag isn't defined (no gtmId configured). */
export function updateConsent(choice: ConsentChoice): void {
  if (typeof window === 'undefined' || !window.gtag) return;
  window.gtag('consent', 'update', {
    ad_storage: choice,
    ad_user_data: choice,
    ad_personalization: choice,
    analytics_storage: choice,
  });
}

/**
 * Inline bootstrap script, returned raw (like `gtmHeadScript`/`gtmNoscriptIframe` in `gtm.ts`) for
 * `dangerouslySetInnerHTML`. MUST render before `<GtmSnippet>` in the page — Google requires the
 * Consent Mode default to be set before `gtm.js` loads.
 *
 * Sets the default to fully denied, then restores an already-stored choice for this widgetToken
 * synchronously, in this same script — not in the React banner. If restoration waited for React to
 * hydrate, GTM would have already evaluated its tags under 'denied' for a returning visitor who
 * previously accepted, silently dropping their conversion/remarketing tags before the banner logic
 * ever runs (same class of timing bug as the GTM `purchase` miss fixed on 2026-09-01).
 */
export function consentBootstrapScript(widgetToken: string): string {
  const key = JSON.stringify(consentStorageKey(widgetToken));
  return `window.dataLayer=window.dataLayer||[];function gtag(){dataLayer.push(arguments);}window.gtag=gtag;gtag('consent','default',{ad_storage:'denied',ad_user_data:'denied',ad_personalization:'denied',analytics_storage:'denied',wait_for_update:500});try{var raw=localStorage.getItem(${key});if(raw){var stored=JSON.parse(raw);if(stored&&stored.choice==='granted'&&(Date.now()-stored.timestamp)<=${CONSENT_MAX_AGE_MS}){gtag('consent','update',{ad_storage:'granted',ad_user_data:'granted',ad_personalization:'granted',analytics_storage:'granted'});}}}catch(e){}`;
}

/**
 * Self-contained cookie consent banner (markup + vanilla JS) for surfaces with no client-side
 * hydration to run `CookieConsentBanner.tsx`'s `useEffect` — namely the standalone `/api/gtm-export`
 * artifact, which deliberately strips every script from the fetched `/lp/[widgetToken]` page (see
 * that route's doc comment). Without this, a pasted export would load `gtm.js` with no consent gate
 * at all — the exact Google EU User Consent Policy risk {@link consentBootstrapScript} exists to
 * avoid on the live page.
 *
 * Same storage key/shape and `gtag('consent','update', ...)` call as the React banner, reimplemented
 * without React. Must be placed after a `consentBootstrapScript` tag (so `window.gtag` already
 * exists) and anywhere after this file's markup renders. French-only, like the rest of this export —
 * see `route.ts`'s `lang="fr"`.
 */
export function consentBannerStandaloneHtml(widgetToken: string): string {
  const key = JSON.stringify(consentStorageKey(widgetToken));
  const message =
    "Nous utilisons des cookies de mesure d'audience et de suivi publicitaire (Google) pour évaluer l'efficacité de nos campagnes. Vous pouvez accepter ou refuser leur dépôt à tout moment.";
  const markup =
    '<div id="cl-cookie-consent" class="lp-cookie-consent" role="dialog" aria-live="polite" style="display:none">' +
    `<p class="lp-cookie-consent-message">${message}</p>` +
    '<div class="lp-cookie-consent-actions">' +
    '<button type="button" class="lp-cookie-consent-btn lp-cookie-consent-btn--refuse" onclick="clConsentChoose(\'denied\')">Refuser</button>' +
    '<button type="button" class="lp-cookie-consent-btn lp-cookie-consent-btn--accept" onclick="clConsentChoose(\'granted\')">Accepter</button>' +
    '</div></div>';
  const script =
    `(function(){var KEY=${key},MAXAGE=${CONSENT_MAX_AGE_MS},valid=false;` +
    'try{var raw=localStorage.getItem(KEY);if(raw){var p=JSON.parse(raw);' +
    "if(p&&(p.choice==='granted'||p.choice==='denied')&&typeof p.timestamp==='number'&&(Date.now()-p.timestamp)<=MAXAGE)valid=true;}}catch(e){}" +
    "if(!valid){var el=document.getElementById('cl-cookie-consent');if(el)el.style.display='';}})();" +
    'window.clConsentChoose=function(choice){' +
    "if(window.gtag){window.gtag('consent','update',{ad_storage:choice,ad_user_data:choice,ad_personalization:choice,analytics_storage:choice});}" +
    `try{localStorage.setItem(${key},JSON.stringify({choice:choice,timestamp:Date.now()}));}catch(e){}` +
    "var el=document.getElementById('cl-cookie-consent');if(el)el.style.display='none';};";
  return `${markup}<script>${script}</script>`;
}

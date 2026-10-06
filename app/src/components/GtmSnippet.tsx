import { headers } from 'next/headers';
import { gtmHeadScript, gtmNoscriptIframe } from '@/lib/gtm';

interface Props {
  id: string | null;
}

/**
 * Renders the official GTM head script and noscript fallback as raw HTML.
 *
 * Must stay a Server Component: a `<script>` injected via `dangerouslySetInnerHTML` from a Client
 * Component never executes after hydration (browsers only run scripts present in the initial,
 * server-rendered markup or inserted by `document.createElement`).
 *
 * `nonce` comes from `middleware.ts`'s per-request CSP (security audit 2026-10-06, finding #1) —
 * without it this script is blocked outright under the nonce-based `script-src`.
 */
export async function GtmSnippet({ id }: Props) {
  if (!id) return null;
  const nonce = (await headers()).get('x-nonce') ?? undefined;
  return (
    <>
      <script nonce={nonce} dangerouslySetInnerHTML={{ __html: gtmHeadScript(id) }} />
      <noscript dangerouslySetInnerHTML={{ __html: gtmNoscriptIframe(id) }} />
    </>
  );
}

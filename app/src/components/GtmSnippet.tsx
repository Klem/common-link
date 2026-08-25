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
 */
export function GtmSnippet({ id }: Props) {
  if (!id) return null;
  return (
    <>
      <script dangerouslySetInnerHTML={{ __html: gtmHeadScript(id) }} />
      <noscript dangerouslySetInnerHTML={{ __html: gtmNoscriptIframe(id) }} />
    </>
  );
}

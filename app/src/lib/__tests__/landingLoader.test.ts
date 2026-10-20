import { describe, it, expect, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { GTM_EVENT_MESSAGE_TYPE } from '../gtm';

/**
 * `public/landing.js` and `public/widget.js` are standalone browser scripts (no module system):
 * each is evaluated here the way a host page would run it, with `document.currentScript` pointing
 * at its own `<script>` tag.
 */
const FRONT = 'https://app.example.org';
const REF = '8e8a8e77-2c48-4634-a306-9cb86f630de8';

const LOADERS = ['landing.js', 'widget.js'] as const;

function load(
  file: (typeof LOADERS)[number],
  pageUrl = '/campagne',
  attributes: Record<string, string> = {},
): HTMLIFrameElement {
  history.pushState({}, '', pageUrl);
  const script = document.createElement('script');
  script.src = `${FRONT}/${file}`;
  script.setAttribute('data-widget-token', 'clk_abc');
  Object.entries(attributes).forEach(([name, value]) => script.setAttribute(name, value));
  document.body.appendChild(script);
  Object.defineProperty(document, 'currentScript', { value: script, configurable: true });
  new Function(readFileSync(resolve(__dirname, `../../../public/${file}`), 'utf-8'))();
  return document.querySelector('iframe') as HTMLIFrameElement;
}

const post = (origin: string, source: Window | null, data: unknown) =>
  window.dispatchEvent(new MessageEvent('message', { origin, source, data }));

afterEach(() => {
  document.body.innerHTML = '';
  delete window.dataLayer;
  Reflect.deleteProperty(document, 'currentScript');
  sessionStorage.clear();
  history.pushState({}, '', '/');
});

describe('embed type passed to the iframe (clEmbed)', () => {
  it('reports script for landing.js', () => {
    expect(new URL(load('landing.js').src).searchParams.get('clEmbed')).toBe('script');
  });

  it('reports widget for widget.js', () => {
    expect(new URL(load('widget.js').src).searchParams.get('clEmbed')).toBe('widget');
  });

  it('reports html for the widget.js tag of the standalone export', () => {
    const iframe = load('widget.js', '/campagne', { 'data-embed': 'html' });
    expect(new URL(iframe.src).searchParams.get('clEmbed')).toBe('html');
  });

  it('falls back to widget for any other data-embed value', () => {
    const iframe = load('widget.js', '/campagne', { 'data-embed': 'direct' });
    expect(new URL(iframe.src).searchParams.get('clEmbed')).toBe('widget');
  });
});

describe.each(LOADERS)('%s', (file) => {
  it('pushes the events sent by its own iframe', () => {
    window.dataLayer = [];
    const iframe = load(file);
    // Pushed whole: params outside `ecommerce` (amount choice, anonymous) must survive the relay.
    const payload = {
      event: 'begin_checkout',
      ecommerce: { value: 30 },
      anonymous: false,
      amount_source: 'custom',
      first_amount: 10,
      amount_changes: 3,
    };
    post(FRONT, iframe.contentWindow, { type: GTM_EVENT_MESSAGE_TYPE, payload });
    expect(window.dataLayer).toEqual([payload]);
  });

  it('ignores foreign origins and foreign sources', () => {
    window.dataLayer = [];
    const iframe = load(file);
    const msg = { type: GTM_EVENT_MESSAGE_TYPE, payload: { event: 'begin_checkout' } };
    post('https://evil.example', iframe.contentWindow, msg);
    post(FRONT, window, msg);
    expect(window.dataLayer).toEqual([]);
  });

  it('on the way back from a confirmed payment, hands the ref to an eager iframe and cleans the URL', () => {
    const iframe = load(file, `/campagne?utm_source=ads&cl_donation=success&cl_ref=${REF}`);
    expect(new URL(iframe.src).searchParams.get('clRef')).toBe(REF);
    expect(iframe.getAttribute('loading')).toBeNull();
    expect(window.location.search).toBe('?utm_source=ads');
  });

  it('never hands over a malformed ref', () => {
    const iframe = load(file, '/campagne?cl_donation=success&cl_ref=../../admin');
    expect(new URL(iframe.src).searchParams.get('clRef')).toBeNull();
    expect(iframe.getAttribute('loading')).toBe('lazy');
    expect(window.location.search).toBe('');
  });

  it('remembers a relayed purchase so a reload never sends it again', () => {
    window.dataLayer = [];
    const iframe = load(file, `/campagne?cl_donation=success&cl_ref=${REF}`);
    const payload = { event: 'purchase', ecommerce: { transaction_id: REF } };
    post(FRONT, iframe.contentWindow, { type: GTM_EVENT_MESSAGE_TYPE, payload });
    expect(window.dataLayer).toEqual([payload]);

    document.body.innerHTML = '';
    const again = load(file, `/campagne?cl_donation=success&cl_ref=${REF}`);
    expect(new URL(again.src).searchParams.get('clRef')).toBeNull();
  });
});

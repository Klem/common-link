import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { gtmIframeBridgeScript, relayDonationEvent, GTM_EVENT_MESSAGE_TYPE } from '../gtm';

const FRONT = 'https://app.example.org';
const TOKEN = 'clk_abc';

describe('gtmIframeBridgeScript', () => {
  let iframe: HTMLIFrameElement;

  beforeEach(() => {
    window.dataLayer = [];
    iframe = document.createElement('iframe');
    iframe.src = `${FRONT}/fr/lp/${TOKEN}`;
    document.body.appendChild(iframe);
  });

  afterEach(() => {
    iframe.remove();
    delete window.dataLayer;
  });

  const run = () => new Function(gtmIframeBridgeScript(FRONT, TOKEN))();

  const post = (origin: string, source: Window | null, data: unknown) =>
    window.dispatchEvent(new MessageEvent('message', { origin, source, data }));

  it('adds the host origin as parentOrigin to the iframe src', () => {
    run();
    expect(new URL(iframe.src).searchParams.get('parentOrigin')).toBe(window.location.origin);
  });

  it('pushes the payload of a message from our iframe and origin', () => {
    run();
    const payload = { event: 'begin_checkout', ecommerce: { value: 20 } };
    post(FRONT, iframe.contentWindow, { type: GTM_EVENT_MESSAGE_TYPE, payload });
    expect(window.dataLayer).toEqual([payload]);
  });

  it('ignores a message from a foreign origin, a foreign source or another type', () => {
    run();
    const msg = { type: GTM_EVENT_MESSAGE_TYPE, payload: { event: 'begin_checkout' } };
    post('https://evil.example', iframe.contentWindow, msg);
    post(FRONT, window, msg);
    post(FRONT, iframe.contentWindow, { type: 'other', payload: { event: 'x' } });
    expect(window.dataLayer).toEqual([]);
  });

  it('keeps the debug flag in sessionStorage across navigations until ?clDebug=0', () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {});
    try {
      history.pushState({}, '', '/?clDebug=1');
      run();
      expect(sessionStorage.getItem('clDebug')).toBe('1');
      expect(log).toHaveBeenCalled();

      // Back from Mollie: the URL no longer carries the flag, the tab still remembers it.
      log.mockClear();
      window.dataLayer = [];
      history.pushState({}, '', '/');
      run();
      expect(log).toHaveBeenCalled();

      history.pushState({}, '', '/?clDebug=0');
      run();
      expect(sessionStorage.getItem('clDebug')).toBeNull();
    } finally {
      log.mockRestore();
      sessionStorage.clear();
      history.pushState({}, '', '/');
    }
  });

  it('does nothing when the iframe is not on the page', () => {
    iframe.remove();
    expect(run).not.toThrow();
    expect(window.dataLayer).toEqual([]);
  });

  describe('back from a confirmed payment', () => {
    const REF = '8e8a8e77-2c48-4634-a306-9cb86f630de8';

    afterEach(() => {
      sessionStorage.clear();
      history.pushState({}, '', '/');
    });

    it('hands the ref to the iframe and removes the flag from the address bar', () => {
      history.pushState({}, '', `/campagne?utm_source=ads&cl_donation=success&cl_ref=${REF}`);
      run();
      expect(new URL(iframe.src).searchParams.get('clRef')).toBe(REF);
      expect(window.location.search).toBe('?utm_source=ads');
    });

    it('does not hand over a ref already relayed in this tab', () => {
      history.pushState({}, '', `/campagne?cl_donation=success&cl_ref=${REF}`);
      run();
      post(FRONT, iframe.contentWindow, {
        type: GTM_EVENT_MESSAGE_TYPE,
        payload: { event: 'purchase', ecommerce: { transaction_id: REF } },
      });

      iframe.src = `${FRONT}/fr/lp/${TOKEN}`;
      history.pushState({}, '', `/campagne?cl_donation=success&cl_ref=${REF}`);
      run();
      expect(new URL(iframe.src).searchParams.get('clRef')).toBeNull();
    });
  });
});

describe('relayDonationEvent', () => {
  it('posts to the parent only, never into the local dataLayer', () => {
    window.dataLayer = [];
    const parent = { postMessage: vi.fn() };
    vi.stubGlobal('parent', parent);
    try {
      relayDonationEvent(
        'purchase',
        { transaction_id: 'r', value: 5, currency: 'EUR', items: [{ item_id: 'c', item_name: 'n' }], affiliation: 'a' },
        { anonymous: false, paymentMethod: 'creditcard' },
        'https://asso.example',
      );
      expect(window.dataLayer).toEqual([]);
      expect(parent.postMessage).toHaveBeenCalledWith(
        {
          type: GTM_EVENT_MESSAGE_TYPE,
          payload: {
            event: 'purchase',
            ecommerce: { transaction_id: 'r', value: 5, currency: 'EUR', items: [{ item_id: 'c', item_name: 'n' }], affiliation: 'a' },
            anonymous: false,
            payment_method: 'creditcard',
          },
        },
        'https://asso.example',
      );
    } finally {
      vi.unstubAllGlobals();
      delete window.dataLayer;
    }
  });
});

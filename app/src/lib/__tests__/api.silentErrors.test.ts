import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { AxiosError } from 'axios';
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import api from '../api';
import { useToastStore } from '@/stores/toastStore';

/** Adapter answering every request with the given HTTP status, as an Axios error. */
const failingAdapter = (status: number): AxiosAdapter => (config: InternalAxiosRequestConfig) =>
  Promise.reject(
    new AxiosError('failed', 'ERR_BAD_RESPONSE', config, null, {
      status,
      statusText: 'error',
      headers: {},
      config,
      data: null,
    }),
  );

describe('lib/api — silentErrors opt-in (R8)', () => {
  const originalAdapter = api.defaults.adapter;

  beforeEach(() => useToastStore.setState({ toasts: [] }));
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('toasts a server error by default (behaviour unchanged)', async () => {
    api.defaults.adapter = failingAdapter(500);

    await expect(api.get('/api/anything')).rejects.toBeInstanceOf(AxiosError);
    expect(useToastStore.getState().toasts.map((t) => t.messageKey)).toEqual(['errors.serverError']);
  });

  it('rejects without any toast when the request opts in', async () => {
    api.defaults.adapter = failingAdapter(500);

    await expect(api.get('/api/anything', { silentErrors: true })).rejects.toBeInstanceOf(AxiosError);
    api.defaults.adapter = failingAdapter(409);
    await expect(api.post('/api/anything', undefined, { silentErrors: true })).rejects.toBeInstanceOf(AxiosError);
    expect(useToastStore.getState().toasts).toEqual([]);
  });
});

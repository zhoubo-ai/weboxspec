import { afterEach, expect, it, vi } from 'vitest';
import { api, resetApiSession } from './client';
import * as client from './client';
afterEach(() => { resetApiSession(); vi.unstubAllGlobals(); });
it('opens POST event streams with CSRF and ties the body to cancellation of the session', async () => {
  const fetch=vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({token:'masked-token',headerName:'X-XSRF-TOKEN'}))).mockResolvedValueOnce(new Response('event: meta\ndata: {}\n\n',{headers:{'Content-Type':'text/event-stream'}}));vi.stubGlobal('fetch',fetch);
  const controller=new AbortController(), body=await client.postStream('/recommendations/stream',{prompt:'Lunch'},controller.signal);
  expect(body).toBeInstanceOf(ReadableStream);const init=fetch.mock.calls[1][1];expect(init.method).toBe('POST');expect(init.credentials).toBe('same-origin');expect(new Headers(init.headers).get('Accept')).toBe('text/event-stream');expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('masked-token');resetApiSession();expect(init.signal.aborted).toBe(true);
});
it('uploads multipart data with CSRF and lets the browser set the boundary', async () => {
  const form = new FormData();form.append('file', new Blob(['fixture'], { type: 'image/png' }), 'fixture.png');
  const fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ token: 'masked-token', headerName: 'X-XSRF-TOKEN' }))).mockResolvedValueOnce(new Response(JSON.stringify({ mediaId: 'example' })));
  vi.stubGlobal('fetch', fetch);await api('/admin/media', { method: 'POST', form });
  const request = fetch.mock.calls[1][1];expect(request.body).toBe(form);expect(new Headers(request.headers).get('Content-Type')).toBeNull();expect(new Headers(request.headers).get('X-XSRF-TOKEN')).toBe('masked-token');
});
it('sends session cookies and the current CSRF header for writes', async () => {
  const fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ token: 'masked-token', headerName: 'X-XSRF-TOKEN' })))
    .mockResolvedValueOnce(new Response(JSON.stringify({ accepted: true })));
  vi.stubGlobal('fetch', fetch);
  expect(await api('/auth/register', { method: 'POST', json: { email: 'user@example.test' } })).toEqual({ accepted: true });
  expect(fetch.mock.calls[0][0]).toBe('/api/v1/auth/csrf');
  const request = fetch.mock.calls[1][1];
  expect(request.credentials).toBe('same-origin');expect(request.cache).toBe('no-store');
  expect(new Headers(request.headers).get('X-XSRF-TOKEN')).toBe('masked-token');
});
it('keeps safe structured error details and hides non-JSON server text', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ code: 'VERSION_CONFLICT', message: 'Reload current values.', requestId: 'r', retryable: false }), { status: 409 }))
    .mockResolvedValueOnce(new Response('<html>private stack trace</html>', { status: 500 })));
  await expect(api('/orders')).rejects.toMatchObject({ status: 409, code: 'VERSION_CONFLICT', message: 'Reload current values.' });
  await expect(api('/orders')).rejects.toMatchObject({ message: 'The service returned an invalid response. Please try again.' });
});
it('cancels outstanding requests when the account changes and rejects external paths', async () => {
  let sent: AbortSignal | undefined;
  vi.stubGlobal('fetch', vi.fn((_url, init) => {
    sent = init.signal;
    return new Promise((_resolve, reject) => sent!.addEventListener('abort', () => reject(sent!.reason)));
  }));
  const result = api('/auth/me');resetApiSession();
  await expect(result).rejects.toMatchObject({ name: 'AbortError' });
  expect(sent?.aborted).toBe(true);
  await expect(api('https://example.com/')).rejects.toThrow('API path');
});

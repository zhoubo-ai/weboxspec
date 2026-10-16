import type { ApiErrorBody, Csrf } from './types';
const unauthorized = new Set<() => void>();
export function onUnauthorized(listener: () => void): () => void {
  unauthorized.add(listener); return () => { unauthorized.delete(listener); };
}
let session = new AbortController();
let csrf: Promise<Csrf> | undefined;
export class ApiError extends Error {
  readonly code: string;
  readonly retryable: boolean;
  constructor(readonly status: number, readonly details: ApiErrorBody) {
    super(details.message); this.name = 'ApiError'; this.code = details.code; this.retryable = details.retryable;
  }
}
export function resetApiSession(): void {
  session.abort(); session = new AbortController(); csrf = undefined;
}
async function decode<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T;
  let body: unknown;
  try { body = await response.json(); }
  catch { throw new Error('The service returned an invalid response. Please try again.'); }
  if (!response.ok) {
    if (body && typeof body === 'object' && 'code' in body && typeof body.code === 'string'
      && 'message' in body && typeof body.message === 'string' && 'retryable' in body && typeof body.retryable === 'boolean') {
      throw new ApiError(response.status, body as ApiErrorBody);
    }
    throw new Error('The service returned an invalid response. Please try again.');
  }
  return body as T;
}
export function getCsrf(): Promise<Csrf> {
  if (!csrf) {
    const activeSession = session;
    csrf = fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store', redirect: 'error', signal: activeSession.signal })
      .then(decode<Csrf>).then(value => {
        activeSession.signal.throwIfAborted();
        if (value.headerName !== 'X-XSRF-TOKEN' || !value.token) throw new Error('The service returned an invalid security token.');
        return value;
      }).catch(error => { if (session === activeSession) csrf = undefined; throw error; });
  }
  return csrf;
}
export async function api<T>(path: string, options: { method?: string; json?: unknown; form?: FormData; signal?: AbortSignal; headers?: HeadersInit } = {}): Promise<T> {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('\\') || /[\r\n]/.test(path)) throw new Error('API path must be relative to this application.');
  const method = (options.method ?? 'GET').toUpperCase();
  if (options.form && options.json !== undefined) throw new Error('Choose one request body.');
  const signal = options.signal ? AbortSignal.any([session.signal, options.signal]) : session.signal;
  signal.throwIfAborted();
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.json !== undefined) headers.set('Content-Type', 'application/json');
  if (options.form) headers.delete('Content-Type');
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = await getCsrf(); signal.throwIfAborted(); headers.set(token.headerName, token.token);
  }
  try {
    const response = await fetch(`/api/v1${path}`, { method, headers, credentials: 'same-origin', cache: 'no-store', redirect: 'error',
      body: options.form ?? (options.json === undefined ? undefined : JSON.stringify(options.json)), signal });
    signal.throwIfAborted();
    const value = await decode<T>(response); signal.throwIfAborted(); return value;
  } catch (error) {
    if (error instanceof ApiError && error.code === 'CSRF_INVALID') csrf = undefined;
    if (error instanceof ApiError && error.code === 'UNAUTHENTICATED') for (const listener of unauthorized) listener();
    throw error;
  }
}
export async function postStream(path:string, json:unknown, cancellation:AbortSignal):Promise<ReadableStream<Uint8Array>> {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('\\') || /[\r\n]/.test(path)) throw new Error('API path must be relative to this application.');
  const signal=AbortSignal.any([session.signal,cancellation]);signal.throwIfAborted();const token=await getCsrf();signal.throwIfAborted();
  try {
    const response=await fetch(`/api/v1${path}`,{method:'POST',credentials:'same-origin',cache:'no-store',redirect:'error',signal,headers:{'Content-Type':'application/json',Accept:'text/event-stream',[token.headerName]:token.token},body:JSON.stringify(json)});signal.throwIfAborted();
    if(!response.ok)await decode(response);
    if(!response.body || !response.headers.get('Content-Type')?.toLowerCase().startsWith('text/event-stream'))throw new Error('The service returned an invalid recommendation stream.');
    return response.body;
  } catch(error) {
    if(error instanceof ApiError && error.code==='CSRF_INVALID')csrf=undefined;
    if(error instanceof ApiError && error.code==='UNAUTHENTICATED')for(const listener of unauthorized)listener();
    throw error;
  }
}

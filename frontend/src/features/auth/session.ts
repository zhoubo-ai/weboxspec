import { createContext, createElement, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, ApiError, onUnauthorized, resetApiSession } from '../../shared/api/client';
import type { User } from '../../shared/api/types';
const cleanup = new Set<() => void>();
export function onSessionEnd(callback: () => void): () => void { cleanup.add(callback); return () => { cleanup.delete(callback); }; }
type Session = { user: User | null; loading: boolean; error: string | null; refresh: () => Promise<void>; login: (email: string, password: string) => Promise<User>; logout: () => Promise<void>; expire: () => void };
const Context = createContext<Session | null>(null);
export function SessionProvider({ children }: { children: ReactNode }) {
  const queries = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const generation = useRef(0);
  const clear = useCallback(() => {
    generation.current++; resetApiSession(); void queries.cancelQueries(); queries.clear();
    for (const close of cleanup) close();
    setUser(null); setLoading(false); setError(null);
  }, [queries]);
  const refresh = useCallback(async () => {
    const active = generation.current;
    try {
      const restored = await api<User>('/auth/me');
      if (active === generation.current) { setUser(restored); setError(null); }
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) { if (active === generation.current) setUser(null); }
      else if (!(e instanceof DOMException && e.name === 'AbortError') && active === generation.current) setError('Could not check your session. Please try again.');
    } finally { if (active === generation.current) setLoading(false); }
  }, []);
  useEffect(() => {
    const unsubscribe = onUnauthorized(clear);
    void refresh();
    const focus = () => { void refresh(); };
    window.addEventListener('focus', focus);
    return () => { unsubscribe(); window.removeEventListener('focus', focus); generation.current++; resetApiSession(); };
  }, [clear, refresh]);
  const login = useCallback(async (email: string, password: string) => {
    const authenticated = await api<User>('/auth/login', { method: 'POST', json: { email, password } });
    clear();setUser(authenticated);return authenticated;
  }, [clear]);
  const logout = useCallback(async () => {
    try { await api<void>('/auth/logout', { method: 'POST' }); }
    catch(e) { if (!(e instanceof ApiError && e.status === 401)) throw e; }
    clear();
  }, [clear]);
  return createElement(Context.Provider, { value: { user, loading, error, refresh, login, logout, expire: clear } }, children);
}
export function useSession(): Session {
  const value = useContext(Context);if (!value) throw new Error('SessionProvider is required.');return value;
}
export function homeFor(user: User): string { return user.role === 'Admin' ? '/console/dishes' : '/menu'; }

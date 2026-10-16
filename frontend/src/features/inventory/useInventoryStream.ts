import { useCallback, useEffect, useRef } from 'react';
import { skipToken, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import type { InventorySnapshot, InventoryUpdated, MenuChanged, MenuPage, User } from '../../shared/api/types';
import { onSessionEnd, useSession } from '../auth/session';
import { beginInventory, inventoryState, type InventoryAction, type InventoryState } from './inventoryState';
export const inventoryKey = (userId: string, date: string) => ['inventory', userId, date] as const;
export function useCurrentInventory(date: string) { const { user } = useSession();return useQuery<InventoryState>({ queryKey: inventoryKey(user?.userId ?? '', date), enabled: false, queryFn: skipToken }).data; }
const integer = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const id = (v: unknown): v is string => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v);
function valid(value: unknown, event: string): boolean {
  if (!value || typeof value !== 'object') return false;const v = value as Record<string, unknown>;
  if (typeof v.deliveryDate !== 'string' || !integer(v.menuRevision)) return false;
  const row = (value: unknown) => { const r = value as Record<string, unknown>;return Boolean(r) && id(r.dishId) && integer(r.remaining) && integer(r.version); };
  return event === 'menu.changed' || (event === 'inventory.updated' ? row(v) && typeof v.occurredAt === 'string' : Array.isArray(v.items) && v.items.every(row) && typeof v.serverTime === 'string');
}
let connectionSequence = 0;
/** Mounted only on the menu. Other screens can read the last stock state without keeping a socket open. */
export function useInventoryStream(date: string) {
  const { user, expire } = useSession(), queries = useQueryClient(), receive = useRef<(action: InventoryAction) => void>(() => {});const state = useCurrentInventory(date);
  const mergeMenu = useCallback((value: MenuPage, epoch: number) => receive.current({ type: 'menu', value, epoch }), []);
  useEffect(() => {
    if (!user) return;const owner = user.userId, key = inventoryKey(owner, date), abort = new AbortController();
    let alive = true, source: EventSource | null = null, epoch = 0, retry: ReturnType<typeof setTimeout> | undefined, attempts = 0;
    const current = (generation: number) => alive && generation === epoch && !abort.signal.aborted;
    const stop = () => { source?.close();source = null;if (retry) clearTimeout(retry);retry = undefined; };
    const offline = () => queries.setQueryData<InventoryState>(key, old => old && inventoryState(old, { type: 'offline', epoch }));
    function reconnect() {
      if (!alive) return;stop();offline();const generation = epoch;
      retry = setTimeout(async () => {
        if (!current(generation)) return;
        try { const account = await api<User>('/auth/me', { signal: abort.signal });if (current(generation)) { if (account.userId === owner) connect();else expire(); } }
        catch (failure) { if (current(generation) && !(failure instanceof ApiError && failure.status === 401)) reconnect(); }
      }, Math.min(10_000, 500 * 2 ** Math.min(attempts++, 5)));
    }
    function connect() {
      if (!alive) return;stop();epoch = ++connectionSequence;const generation = epoch;let refreshBusy = false, refreshAgain = false;
      queries.setQueryData(key, beginInventory(date, generation));if (typeof EventSource === 'undefined') { offline();receive.current = action => { if (current(generation) && action.type === 'menu') queries.setQueryData<InventoryState>(key, old => old && inventoryState(old, action)); };return; }source = new EventSource(`/api/v1/menus/${date}/events`);const active = source;const connected = () => current(generation) && source === active;
      async function refreshDate() {
        if (!connected()) return;if (refreshBusy) { refreshAgain = true;return; }refreshBusy = true;
        try { const snapshot = await api<InventorySnapshot>(`/menus/${date}/inventory`, { signal: abort.signal });if (connected()) { apply({ type: 'snapshot', value: snapshot, epoch: generation });void queries.invalidateQueries({ queryKey: ['menu', date] }); } }
        catch (failure) { if (connected() && !(failure instanceof ApiError && failure.status === 401)) reconnect(); }
        finally { refreshBusy = false;if (refreshAgain && connected()) { refreshAgain = false;void refreshDate(); } }
      }
      function apply(action: InventoryAction) {
        if (!current(generation) || (action.type !== 'menu' && !connected())) return;let refresh = false;
        queries.setQueryData<InventoryState>(key, old => { const prior = old ?? beginInventory(date, generation), next = inventoryState(prior, action);refresh = next.refresh > prior.refresh;return next; });
        if (refresh) void refreshDate();
      }
      receive.current = apply;
      for (const event of ['inventory.snapshot', 'inventory.updated', 'menu.changed']) active.addEventListener(event, message => {
        if (!current(generation) || source !== active) return;
        try {
          const value: unknown = JSON.parse((message as MessageEvent).data);if (!valid(value, event)) throw new Error('Invalid inventory event.');
          if (event === 'inventory.snapshot') { attempts = 0;apply({ type: 'snapshot', epoch: generation, value: value as InventorySnapshot }); }
          else if (event === 'inventory.updated') apply({ type: 'updated', epoch: generation, value: value as InventoryUpdated });
          else apply({ type: 'changed', epoch: generation, value: value as MenuChanged });
        } catch { reconnect(); }
      });
      active.addEventListener('session.expired', () => { if (current(generation) && source === active) { apply({ type: 'expired', epoch: generation });alive = false;stop();abort.abort();expire(); } });
      active.onerror = () => { if (current(generation) && source === active) reconnect(); };
    }
    const disconnected = () => { if (alive) reconnect(); };
    const resume = () => { if (document.visibilityState === 'visible' && alive) { attempts = 0;reconnect(); } };
    connect();window.addEventListener('offline', disconnected);window.addEventListener('online', resume);window.addEventListener('focus', resume);document.addEventListener('visibilitychange', resume);
    const unsubscribe = onSessionEnd(() => { alive = false;stop();abort.abort(); });
    return () => { alive = false;stop();abort.abort();unsubscribe();receive.current = () => {};window.removeEventListener('offline', disconnected);window.removeEventListener('online', resume);window.removeEventListener('focus', resume);document.removeEventListener('visibilitychange', resume); };
  }, [date, user?.userId, expire, queries]);
  return { state, mergeMenu };
}

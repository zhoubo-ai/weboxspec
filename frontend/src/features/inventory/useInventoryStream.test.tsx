import { act, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { useInventoryStream, inventoryKey } from './useInventoryStream';
import type { InventoryState } from './inventoryState';
import { api } from '../../shared/api/client';
import { pasta } from '../cart/cartFixtures';
const session = vi.hoisted(() => ({ user: { userId: '10000000-0000-4000-8000-000000000001' }, expire: vi.fn() }));
vi.mock('../auth/session', () => ({ useSession: () => session, onSessionEnd: () => () => {} }));
vi.mock('../../shared/api/client', async original => ({ ...await original<typeof import('../../shared/api/client')>(), api: vi.fn() }));
class Source extends EventTarget {
  static opened: Source[] = [];closed = false;onerror: (() => void) | null = null;
  constructor(readonly url: string) { super();Source.opened.push(this); }
  close() { this.closed = true; }
  emit(name: string, value: unknown) { this.dispatchEvent(new MessageEvent(name, { data: JSON.stringify(value) })); }
}
const date = '2032-01-01';
const snapshot = (remaining = 20, version = 0, revision = 1) => ({ deliveryDate: date, menuRevision: revision, serverTime: '2026-10-16T01:00:00Z', items: [{ dishId: pasta.dishId, remaining, version }] });
function Harness({ date }: { date: string }) { const { state } = useInventoryStream(date);return <p>{state?.status}</p>; }
let queries: QueryClient;
beforeEach(() => { vi.useFakeTimers();Source.opened = [];session.expire.mockClear();vi.stubGlobal('EventSource', Source);vi.mocked(api).mockReset();queries = new QueryClient({ defaultOptions: { queries: { retry: false } } }); });
afterEach(() => { queries.clear();vi.useRealTimers();vi.unstubAllGlobals(); });
const renderStream = () => render(<QueryClientProvider client={queries}><Harness date={date} /></QueryClientProvider>);
const current = () => queries.getQueryData<InventoryState>(inventoryKey(session.user.userId, date))!;
it('disconnect rejects an in-flight refresh and reconnect checks the session before accepting a new snapshot', async () => {
  let resolve!: (value: unknown) => void;vi.mocked(api).mockImplementationOnce(() => new Promise(done => { resolve = done; })).mockResolvedValue(session.user);
  renderStream();const first = Source.opened[0];expect(first.url).toBe(`/api/v1/menus/${date}/events`);
  await act(async () => { first.emit('inventory.snapshot', snapshot());first.emit('menu.changed', { deliveryDate: date, menuRevision: 2 }); });
  expect(api).toHaveBeenCalledTimes(1);await act(async () => first.onerror?.());expect(first.closed).toBe(true);expect(current().status).toBe('offline');
  await act(async () => resolve(snapshot(0, 99, 2)));expect(current().status).toBe('offline');expect(current().entries[pasta.dishId].remaining).toBe(20);
  await act(async () => vi.advanceTimersByTimeAsync(500));expect(api).toHaveBeenLastCalledWith('/auth/me', expect.objectContaining({ signal: expect.any(AbortSignal) }));expect(Source.opened).toHaveLength(2);
  await act(async () => { Source.opened[1].emit('inventory.snapshot', snapshot(17, 3, 2));first.emit('inventory.updated', { ...snapshot(), dishId: pasta.dishId, remaining: 0, version: 100, occurredAt: snapshot().serverTime }); });
  expect(current().status).toBe('live');expect(current().entries[pasta.dishId].remaining).toBe(17);
});
it('session.expired closes the socket and stops every retry', async () => {
  renderStream();await act(async () => { Source.opened[0].emit('inventory.snapshot', snapshot());Source.opened[0].emit('session.expired', { message: 'Your session has expired. Please sign in again.' }); });
  expect(session.expire).toHaveBeenCalledOnce();expect(Source.opened[0].closed).toBe(true);expect(current().status).toBe('expired');
  await act(async () => { Source.opened[0].onerror?.();window.dispatchEvent(new Event('online'));await vi.advanceTimersByTimeAsync(60_000); });expect(Source.opened).toHaveLength(1);expect(api).not.toHaveBeenCalled();
});
it('date changes and unmount close their sources and ignore late frames', async () => {
  const view=renderStream();const first=Source.opened[0];await act(async () => first.emit('inventory.snapshot', snapshot()));
  view.rerender(<QueryClientProvider client={queries}><Harness date="2032-01-02" /></QueryClientProvider>);expect(first.closed).toBe(true);
  await act(async () => { first.emit('inventory.updated', { ...snapshot(), dishId: pasta.dishId, remaining: 0, version: 100, occurredAt: snapshot().serverTime });Source.opened[1].emit('inventory.snapshot', { ...snapshot(), deliveryDate: '2032-01-02' }); });
  expect(current().entries[pasta.dishId].remaining).toBe(20);await act(async () => vi.advanceTimersByTimeAsync(10));expect(screen.getByText('live')).toBeVisible();view.unmount();expect(Source.opened[1].closed).toBe(true);
});

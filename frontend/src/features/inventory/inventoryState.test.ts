import { expect, it } from 'vitest';
import { beginInventory, inventoryState, inventoryDish, stockLabel } from './inventoryState';
import { pasta } from '../cart/cartFixtures';
const date = '2032-01-01', epoch = 1, other = '10000000-0000-4000-8000-000000000099';
const snapshot = (revision = 1, remaining = 20, version = 0) => ({ deliveryDate: date, menuRevision: revision, serverTime: '2026-10-16T01:00:00Z', items: [{ dishId: pasta.dishId, remaining, version }] });
const updated = (remaining: number, version: number, revision = 1) => ({ deliveryDate: date, menuRevision: revision, dishId: pasta.dishId, remaining, version, occurredAt: '2026-10-16T01:00:00Z' });
const live = () => inventoryState(beginInventory(date, epoch), { type: 'snapshot', epoch, value: snapshot() });
it('a complete snapshot replaces membership, including an empty date', () => {
  const state = live();expect(state.status).toBe('live');expect(state.entries[pasta.dishId].remaining).toBe(20);
  const cleared = inventoryState(state, { type: 'snapshot', epoch, value: { ...snapshot(2), items: [] } });expect(cleared.entries).toEqual({});expect(inventoryDish(cleared, pasta)).toBeNull();
});
it('absolute stock versions deduplicate repeats and reject older events and HTTP responses', () => {
  let state = inventoryState(live(), { type: 'updated', epoch, value: updated(18, 2) });expect(state.entries[pasta.dishId].remaining).toBe(18);
  state = inventoryState(state, { type: 'updated', epoch, value: updated(19, 1) });state = inventoryState(state, { type: 'updated', epoch, value: updated(18, 2) });
  state = inventoryState(state, { type: 'menu', epoch, value: { deliveryDate: date, menuRevision: 1, serverTime: snapshot().serverTime, businessTimezone: 'Asia/Shanghai', items: [{ ...pasta, remaining: 20, inventoryVersion: 0 }], page: 0, size: 24, totalElements: 1 } });expect(inventoryDish(state, pasta)?.remaining).toBe(18);expect(state.entries[pasta.dishId].version).toBe(2);
  state = inventoryState(state, { type: 'snapshot', epoch, value: snapshot(1, 19, 1) });expect(state.entries[pasta.dishId].remaining).toBe(18);
});
it('new revisions and unknown members request a refetch but old events cannot resurrect removed dishes', () => {
  let state = live();state = inventoryState(state, { type: 'updated', epoch, value: { ...updated(3, 1), dishId: other } });expect(state.refresh).toBeGreaterThan(0);expect(state.entries[other]).toBeUndefined();
  state = inventoryState(state, { type: 'changed', epoch, value: { deliveryDate: date, menuRevision: 2 } });expect(state.revision).toBe(2);expect(state.refresh).toBeGreaterThan(1);
  state = inventoryState(state, { type: 'snapshot', epoch, value: { ...snapshot(2), items: [] } });const before = state;state = inventoryState(state, { type: 'updated', epoch, value: updated(20, 99, 1) });expect(state).toBe(before);expect(state.entries).toEqual({});
});
it('old dates, prior connections, expired sessions and stale snapshot responses cannot mutate live state', () => {
  let state = live();expect(inventoryState(state, { type: 'updated', epoch: 0, value: updated(0, 9) })).toBe(state);expect(inventoryState(state, { type: 'snapshot', epoch, value: { ...snapshot(), deliveryDate: '2032-01-02' } })).toBe(state);
  state = inventoryState(state, { type: 'changed', epoch, value: { deliveryDate: date, menuRevision: 5 } });expect(inventoryState(state, { type: 'snapshot', epoch, value: snapshot(2) })).toBe(state);
  state = inventoryState(state, { type: 'offline', epoch });expect(state.status).toBe('offline');state = inventoryState(state, { type: 'expired', epoch });expect(state.status).toBe('expired');expect(state.entries).toEqual({});expect(inventoryState(state, { type: 'snapshot', epoch, value: snapshot(6) })).toBe(state);
});
it('labels zero as Sold Out, one through three as Low Stock and other quantities normally', () => {
  expect(stockLabel(0)).toBe('Sold Out');for (const count of [1, 2, 3]) expect(stockLabel(count)).toBe(`Low Stock · ${count} remaining`);expect(stockLabel(4)).toBe('4 remaining');
});

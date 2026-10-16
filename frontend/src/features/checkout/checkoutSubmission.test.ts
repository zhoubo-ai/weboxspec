import { expect, it, vi } from 'vitest';
import { CheckoutSubmission } from './checkoutSubmission';
import { ApiError } from '../../shared/api/client';
import type { Order, Quote } from '../../shared/api/types';
import { pasta, spaghetti } from '../cart/cartFixtures';
import { cartReducer, initialCart, totalQuantity } from '../cart/cartReducer';
const quote: Quote = { quoteId: '10000000-0000-4000-8000-000000000020', deliveryDate: '2032-01-01', meal: 'Lunch', totalQuantity: 1, totalCents: 2650, serverTime: '2026-10-16T01:00:00Z', expiresAt: '2026-10-16T01:05:00Z', warnings: [], items: [{ dishId: pasta.dishId, dishName: pasta.name, dishVersion: 1, quantity: 1, basePriceCents: 2650, unitPriceCents: 2650, subtotalCents: 2650, effectiveAllergens: ['Gluten'], selectedOptions: [{ optionId: spaghetti, groupId: pasta.optionGroups[0].groupId, groupName: 'Pasta type', optionName: 'Spaghetti', extraCents: 0, allergens: ['Gluten'] }] }] };
const order: Order = { orderId: '10000000-0000-4000-8000-000000000021', orderNumber: 'WB-10000000-0000-4000-8000-000000000021', deliveryDate: quote.deliveryDate, meal: quote.meal, addressText: '上海市 A 楼 3 层', items: quote.items, totalCents: quote.totalCents, totalQuantity: 1, status: 'Pending', version: 1, createdAt: quote.serverTime, updatedAt: quote.serverTime };
it('freezes the quote, key and trimmed Unicode address and coalesces repeated clicks', async () => {
  const flow = new CheckoutSubmission();const frozen = flow.prepare(quote, { addressText: '  上海市 A 楼 3 层  ' });
  expect(frozen.request).toEqual({ quoteId: quote.quoteId, addressText: '上海市 A 楼 3 层' });expect(frozen.key).toMatch(/^[0-9a-f-]{36}$/);expect(Object.isFrozen(frozen.request)).toBe(true);
  let resolve!: (value: Order) => void;const send = vi.fn(() => new Promise<Order>(done => { resolve = done; })), success = vi.fn();
  const first = flow.submit(send, success), second = flow.submit(send, success);expect(send).toHaveBeenCalledTimes(1);expect(flow.phase).toBe('submitting');resolve(order);
  expect(await first).toEqual(order);expect(await second).toEqual(order);expect(success).toHaveBeenCalledTimes(1);expect(flow.phase).toBe('succeeded');await flow.submit(send, success);expect(send).toHaveBeenCalledTimes(1);
});
it('reuses the exact frozen request after an unknown network result and blocks a changed intent', async () => {
  const flow = new CheckoutSubmission();const original = flow.prepare(quote, { addressText: 'Office A' });
  await expect(flow.submit(async () => { throw new TypeError('Connection lost'); }, vi.fn())).rejects.toThrow();expect(flow.phase).toBe('unknown');expect(() => flow.prepare(quote, { addressText: 'Office B' })).toThrow();
  const retry = vi.fn(async () => order);await flow.submit(retry, vi.fn());expect(retry).toHaveBeenCalledWith(original);
});
it('creates a new key only after a definitive rejection and an explicit corrected preparation', async () => {
  const flow = new CheckoutSubmission();const old = flow.prepare(quote, { addressText: 'Office' });
  await expect(flow.submit(async () => { throw new ApiError(409, { code: 'QUOTE_EXPIRED', message: 'Refresh quote.', requestId: crypto.randomUUID(), retryable: false }); }, vi.fn())).rejects.toThrow();expect(flow.phase).toBe('rejected');
  const corrected = flow.prepare({ ...quote, quoteId: crypto.randomUUID() }, { addressText: 'Office' });expect(corrected.key).not.toBe(old.key);expect(corrected.request.quoteId).not.toBe(old.request.quoteId);
});
it('removes only submitted portions and retains new additions while the response is pending', async () => {
  let cart = cartReducer(initialCart, { type: 'add', dish: pasta, optionIds: [spaghetti] });const flow = new CheckoutSubmission();flow.prepare(quote, { addressText: 'Office' });
  let resolve!: (value: Order) => void;const pending = flow.submit(() => new Promise<Order>(done => { resolve = done; }), (_order, lines) => { cart = cartReducer(cart, { type: 'submitted', lines }); });
  cart = cartReducer(cart, { type: 'add', dish: pasta, optionIds: [spaghetti] });resolve(order);await pending;expect(totalQuantity(cart)).toBe(1);
});

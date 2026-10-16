import type { Order, OrderCreate, Quote } from '../../shared/api/types';
import { ApiError } from '../../shared/api/client';
import { requiredText } from '../../shared/values';
export type AddressSelection = { addressId: string; addressText?: never } | { addressText: string; addressId?: never };
export type FrozenSubmission = { key: string; request: OrderCreate; lines: { key: string; quantity: number }[] };
export class CheckoutSubmission {
  phase: 'idle' | 'ready' | 'submitting' | 'unknown' | 'rejected' | 'succeeded' = 'idle';
  frozen: FrozenSubmission | null = null;
  quote: Quote | null = null;
  result: Order | null = null;
  private flight: Promise<Order> | null = null;
  private listeners = new Set<() => void>();
  subscribe = (listener: () => void) => { this.listeners.add(listener);return () => { this.listeners.delete(listener); }; };
  private update(phase: CheckoutSubmission['phase']) { this.phase = phase;for (const listener of this.listeners) listener(); }
  prepare(quote: Quote, address: AddressSelection): FrozenSubmission {
    if (this.phase === 'submitting' || this.phase === 'unknown') throw new Error('Resolve the existing order request before changing it.');
    const normalizeId = (id: string) => { if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) throw new Error('Invalid identifier.');return id.toLowerCase(); };
    if ((address.addressId === undefined) === (address.addressText === undefined)) throw new Error('Choose a saved address or enter a new one.');
    const request: OrderCreate = { quoteId: normalizeId(quote.quoteId), ...(address.addressId !== undefined ? { addressId: normalizeId(address.addressId) } : { addressText: requiredText(address.addressText!, 200) }) };
    const lines = quote.items.map(line => Object.freeze({ key: JSON.stringify([line.dishId, ...line.selectedOptions.map(option => option.optionId).sort()]), quantity: line.quantity }));
    Object.freeze(request);Object.freeze(lines);
    this.frozen = Object.freeze({ key: crypto.randomUUID(), request, lines });this.quote = structuredClone(quote);this.result = null;this.update('ready');return this.frozen;
  }
  submit(send: (submission: FrozenSubmission) => Promise<Order>, onSuccess: (order: Order, lines: FrozenSubmission['lines']) => void): Promise<Order> {
    if (this.flight) return this.flight;
    if (this.phase === 'succeeded' && this.result) return Promise.resolve(this.result);
    if (!this.frozen || !['ready', 'unknown'].includes(this.phase)) return Promise.reject(new Error('Review a new quote before placing your order.'));
    const frozen = this.frozen;this.update('submitting');let response: Promise<Order>;
    try { response = send(frozen); } catch (error) { response = Promise.reject(error); }
    this.flight = response.then(order => { this.result = order;this.update('succeeded');onSuccess(order, frozen.lines);return order; }, error => {
      this.update(error instanceof ApiError && !error.retryable && error.status >= 400 && error.status < 500 ? 'rejected' : 'unknown');throw error;
    }).finally(() => { this.flight = null; });return this.flight;
  }
}

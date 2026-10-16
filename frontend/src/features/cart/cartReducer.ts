import type { MenuDish } from '../../shared/api/types';
import { configuration } from './configuration';
import { safeInteger } from '../../shared/values';
import type { StoredCartLine } from './cartStorage';
import { purchaseAvailability } from './purchaseAvailability';
export type CartLine = { key: string; dish: MenuDish; optionIds: string[]; quantity: number; unitPriceCents: number; issue?: 'changed' | 'unavailable'; original?: StoredCartLine };
export type CartState = { lines: CartLine[]; error: string | null };
export const initialCart: CartState = { lines: [], error: null };
export type CartAction = { type: 'add'; dish: MenuDish; optionIds: string[] } | { type: 'edit'; key: string; dish: MenuDish; optionIds: string[] } | { type: 'decrease' | 'remove' | 'confirm'; key: string } | { type: 'submitted'; lines: { key: string; quantity: number }[] };
export function cartReducer(state: CartState, action: CartAction): CartState {
  try {
    if (action.type === 'edit') {
      const original = state.lines.find(line => line.key === action.key);
      if (!original || original.dish.dishId !== action.dish.dishId) throw new Error('This cart item is no longer available.');
      if (original.dish.dishVersion !== action.dish.dishVersion) throw new Error('This dish changed while you were editing. Reopen it to review the current options.');
      const config = configuration(action.dish, action.optionIds), rest = state.lines.filter(line => line.key !== action.key);
      const available = purchaseAvailability({ lines: rest, error: null }, action.dish);if (!available.canAdd) throw new Error(available.reason!);
      const demand = rest.filter(line => line.dish.dishId === action.dish.dishId).reduce((sum, line) => sum + line.quantity, original.quantity);
      if (demand > action.dish.remaining) throw new Error(`Only ${action.dish.remaining} portions are available for this dish.`);
      const existing = rest.find(line => line.key === config.key);
      const lines = existing ? rest.map(line => line.key === config.key ? { ...line, quantity: line.quantity + original.quantity } : line) : [...rest, { ...config, dish: action.dish, quantity: original.quantity }];
      const next = { lines, error: null };totalCents(next);return next;
    }
    if (action.type === 'add') {
      const config = configuration(action.dish, action.optionIds);
      const available = purchaseAvailability(state, action.dish);if (!available.canAdd) throw new Error(available.reason!);
      const existing = state.lines.find(line => line.key === config.key);
      const lines = existing ? state.lines.map(line => line.key === config.key ? { ...line, quantity: line.quantity + 1 } : line) : [...state.lines, { ...config, dish: action.dish, quantity: 1 }];
      const next = { lines, error: null }; totalCents(next); return next;
    }
    if (action.type === 'submitted') {
      const quantities = new Map(action.lines.map(line => [line.key, safeInteger(line.quantity)]));
      return { error: null, lines: state.lines.map(line => ({ ...line, quantity: line.quantity - (quantities.get(line.key) ?? 0) })).filter(line => line.quantity > 0) };
    }
    if (action.type === 'confirm') { const next: CartState = { error: null, lines: state.lines.map(line => {
      if (line.key !== action.key || line.issue !== 'changed') return line;
      const config = configuration(line.dish, line.optionIds);
      return { ...config, dish: line.dish, quantity: line.quantity };
    }) };totalCents(next);return next; }
    return { error: null, lines: state.lines.flatMap(line => line.key !== action.key ? [line] : action.type === 'remove' || line.quantity === 1 ? [] : [{ ...line, quantity: line.quantity - 1 }]) };
  } catch (error) { return { ...state, error: error instanceof Error ? error.message : 'Could not update your cart.' }; }
}
export function totalQuantity(state: CartState): number { return state.lines.reduce((sum, line) => sum + line.quantity, 0); }
export function totalCents(state: CartState): number {
  const total = state.lines.reduce((sum, line) => sum + BigInt(safeInteger(line.unitPriceCents)) * BigInt(safeInteger(line.quantity)), 0n);
  if (total > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('The cart total exceeds the supported limit.');
  return Number(total);
}

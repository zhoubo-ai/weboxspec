import type { CartState, CartLine } from './cartReducer';
import type { MenuDish } from '../../shared/api/types';
import { configuration } from './configuration';
import { totalCents } from './cartReducer';
export type StoredCartLine = { dishId: string; dishVersion: number; selectedOptionIds: string[]; quantity: number; snapshot: { name: string; imageUrl: string; unitPriceCents: number; optionNames: string[] } };
export type Draft = { version: 1; lines: StoredCartLine[] };
const empty = (): Draft => ({ version: 1, lines: [] });
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const safe = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const key = (id: string) => { if (!uuid.test(id)) throw new Error('A confirmed account is required.'); return `webox.cart.v1:${id}`; };
export function draftFromState(state: CartState): Draft {
  return { version: 1, lines: state.lines.map(line => {
    const source = line.original;
    return source ? { dishId: source.dishId, dishVersion: source.dishVersion, selectedOptionIds: [...source.selectedOptionIds], quantity: line.quantity, snapshot: { name: source.snapshot.name, imageUrl: source.snapshot.imageUrl, unitPriceCents: source.snapshot.unitPriceCents, optionNames: [...source.snapshot.optionNames] } } : {
      dishId: line.dish.dishId, dishVersion: line.dish.dishVersion, selectedOptionIds: [...line.optionIds].sort(), quantity: line.quantity,
      snapshot: { name: line.dish.name, imageUrl: line.dish.imageUrl, unitPriceCents: line.unitPriceCents, optionNames: line.dish.optionGroups.flatMap(group => group.options.filter(option => line.optionIds.includes(option.optionId)).map(option => option.name)) },
    };
  }) };
}
export function saveDraft(userId: string, state: CartState): string | null {
  try { localStorage.setItem(key(userId), JSON.stringify(draftFromState(state))); return null; }
  catch { return 'Your cart could not be saved on this device. Keep this page open and try again.'; }
}
export function loadDraft(userId: string): { draft: Draft; error: string | null } {
  try {
    const raw = localStorage.getItem(key(userId)); if (!raw) return { draft: empty(), error: null };
    if (raw.length > 5_242_880) throw new Error('Oversized draft');
    const draft: Draft = JSON.parse(raw);
    if (draft.version !== 1 || !Array.isArray(draft.lines) || draft.lines.length > 5) throw new Error('Unknown draft');
    let count = 0; const configurations = new Set<string>();
    for (const line of draft.lines) {
      if (!uuid.test(line.dishId) || !safe(line.dishVersion) || !safe(line.quantity) || line.quantity < 1 || line.quantity > 5 || !Array.isArray(line.selectedOptionIds) || line.selectedOptionIds.some(id => typeof id !== 'string' || !uuid.test(id))) throw new Error('Invalid item');
      const snapshot = line.snapshot;
      if (!snapshot || typeof snapshot.name !== 'string' || !snapshot.name.trim() || snapshot.name.length > 400 || typeof snapshot.imageUrl !== 'string' || !/^\/media\/[0-9a-f-]{36}$/.test(snapshot.imageUrl) || !safe(snapshot.unitPriceCents) || !Array.isArray(snapshot.optionNames) || snapshot.optionNames.some(name => typeof name !== 'string' || name.length > 400)) throw new Error('Invalid snapshot');
      line.selectedOptionIds = [...new Set(line.selectedOptionIds)].sort();
      const id = JSON.stringify([line.dishId, ...line.selectedOptionIds]);if (configurations.has(id)) throw new Error('Duplicate configuration'); configurations.add(id);count += line.quantity;
    }
    if (count > 5 || draft.lines.reduce((sum, line) => sum + BigInt(line.snapshot.unitPriceCents) * BigInt(line.quantity), 0n) > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('Invalid totals');
    return { draft, error: null };
  } catch { return { draft: empty(), error: 'The saved cart could not be restored. Please rebuild your cart.' }; }
}
export async function reconcileDraft(draft: Draft, lookup: (dishId: string) => Promise<MenuDish | null>): Promise<{ changed: string[]; unavailable: string[]; state: CartState }> {
  const dishes = new Map(await Promise.all([...new Set(draft.lines.map(line => line.dishId))].map(async id => [id, await lookup(id)] as const)));
  const changed: string[] = [], unavailable: string[] = [];
  const lines: CartLine[] = draft.lines.map(original => {
    const dish = dishes.get(original.dishId);
    const base = { key: JSON.stringify([original.dishId, ...original.selectedOptionIds]), optionIds: original.selectedOptionIds, quantity: original.quantity, unitPriceCents: original.snapshot.unitPriceCents, original };
    if (dish) {
      try {
        const config = configuration(dish, original.selectedOptionIds);
        const required = draft.lines.filter(line => line.dishId === dish.dishId).reduce((sum, line) => sum + line.quantity, 0);
        if (required > dish.remaining) { unavailable.push(dish.dishId); return { ...base, dish, issue: 'unavailable' }; }
        if (dish.dishVersion !== original.dishVersion || config.unitPriceCents !== original.snapshot.unitPriceCents) { changed.push(dish.dishId); return { ...base, dish, issue: 'changed' }; }
        return { ...config, dish, quantity: original.quantity };
      } catch { unavailable.push(dish.dishId); return { ...base, dish, issue: 'unavailable' }; }
    }
    unavailable.push(original.dishId);
    return { ...base, issue: 'unavailable', dish: { dishId: original.dishId, dishVersion: original.dishVersion, name: original.snapshot.name, imageUrl: original.snapshot.imageUrl, basePriceCents: original.snapshot.unitPriceCents, description: 'This item is unavailable on the selected date.', category: 'Light Meal', protein: 'Unknown', spiceLevel: 'None', allergens: [], optionGroups: [], remaining: 0, inventoryVersion: 0, preferenceMatch: { cuisine: false, spice: false } } };
  });
  const state: CartState = { lines, error: null };totalCents(state);return { changed, unavailable, state };
}

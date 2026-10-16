import type { InventoryEntry, InventorySnapshot, InventoryUpdated, MenuChanged, MenuDish, MenuPage } from '../../shared/api/types';
export type InventoryState = { date: string; epoch: number; revision: number; entries: Record<string, InventoryEntry>; snapshot: boolean; status: 'connecting' | 'live' | 'offline' | 'expired'; refresh: number };
export type InventoryAction = { epoch: number } & ({ type: 'snapshot'; value: InventorySnapshot } | { type: 'updated'; value: InventoryUpdated } | { type: 'changed'; value: MenuChanged } | { type: 'menu'; value: MenuPage } | { type: 'offline' } | { type: 'expired' });
export function beginInventory(date: string, epoch: number): InventoryState { return { date, epoch, revision: 0, entries: {}, snapshot: false, status: 'connecting', refresh: 0 }; }
export function inventoryState(state: InventoryState, action: InventoryAction): InventoryState {
  if (action.epoch !== state.epoch || state.status === 'expired') return state;
  if (action.type === 'expired') return { ...state, entries: {}, status: 'expired', snapshot: false };
  if (action.type === 'offline') return { ...state, status: 'offline' };
  const value = action.value;
  if (value.deliveryDate !== state.date || value.menuRevision < state.revision) return state;
  const revision = value.menuRevision, newer = revision > state.revision;
  if (action.type === 'snapshot') {
    const entries: InventoryState['entries'] = {};
    for (const row of action.value.items) { const known = state.entries[row.dishId];entries[row.dishId] = known && known.version > row.version ? known : row; }
    return { ...state, entries, revision, snapshot: true, status: 'live', refresh: state.refresh + (state.snapshot && newer ? 1 : 0) };
  }
  if (action.type === 'changed') return newer ? { ...state, revision, refresh: state.refresh + 1 } : state;
  if (action.type === 'updated') {
    const update = action.value, known = state.entries[update.dishId];
    if (!state.snapshot || !known) return { ...state, revision, refresh: state.refresh + 1 };
    if (!newer && update.version <= known.version) return state;
    const entries = update.version > known.version ? { ...state.entries, [update.dishId]: { dishId: update.dishId, remaining: update.remaining, version: update.version } } : state.entries;
    return { ...state, entries, revision, refresh: state.refresh + (newer ? 1 : 0) };
  }
  const entries = { ...state.entries };
  for (const dish of action.value.items) {
    const known = entries[dish.dishId];
    // A same-revision full snapshot is authoritative about membership, even for delayed page responses.
    if (state.snapshot && !newer && !known) continue;
    if (!known || dish.inventoryVersion > known.version) entries[dish.dishId] = { dishId: dish.dishId, remaining: dish.remaining, version: dish.inventoryVersion };
  }
  return { ...state, entries, revision, refresh: state.refresh + (newer && state.snapshot ? 1 : 0) };
}
export function inventoryDish(state: InventoryState, dish: MenuDish): MenuDish | null {
  const stock = state.entries[dish.dishId];
  if (state.snapshot && !stock) return null;
  return stock && stock.version >= dish.inventoryVersion ? { ...dish, remaining: stock.remaining, inventoryVersion: stock.version } : dish;
}
export function stockLabel(remaining: number): string { return remaining === 0 ? 'Sold Out' : remaining <= 3 ? `Low Stock · ${remaining} remaining` : `${remaining} remaining`; }

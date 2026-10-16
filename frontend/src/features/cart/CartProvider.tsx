import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useSession, onSessionEnd } from '../auth/session';
import { menuDish, shanghaiDate } from '../menu/menuQuery';
import { ApiError } from '../../shared/api/client';
import { cartReducer, initialCart, type CartAction, type CartState } from './cartReducer';
import { draftFromState, loadDraft, reconcileDraft, saveDraft } from './cartStorage';
import type { Allergen, MenuDish, Meal } from '../../shared/api/types';
import { usePreferences } from '../preferences/usePreferences';
import { matchedAllergens, newAllergens } from '../preferences/preferenceRules';
import { AllergenConfirmation } from '../preferences/AllergenConfirmation';
import { useQueryClient } from '@tanstack/react-query';
import { configuration } from './configuration';
import { useCurrentInventory } from '../inventory/useInventoryStream';
import { inventoryDish, type InventoryState } from '../inventory/inventoryState';
import { CheckoutSubmission } from '../checkout/checkoutSubmission';
type Cart = { state: CartState; date: string; meal: Meal; setMeal: (value: Meal) => void; setDate: (value: string) => void; busy: boolean; notice: string | null; dispatch: (action: CartAction) => Promise<boolean>; refresh: () => void; checkout: CheckoutSubmission };
function mergeStock(state: CartState, inventory?: InventoryState): CartState {
  if (!inventory) return state;let changed = false;
  const lines = state.lines.map(line => {
    const dish = inventoryDish(inventory, line.dish);
    if (!dish) { if (line.issue === 'unavailable') return line;changed = true;return { ...line, issue: 'unavailable' as const }; }
    if (dish.remaining === line.dish.remaining && dish.inventoryVersion === line.dish.inventoryVersion) return line;
    changed = true;return { ...line, dish };
  });return changed ? { ...state, lines } : state;
}
const Context = createContext<Cart | null>(null);
export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useSession();const preferences = usePreferences(), latestPreferences = useRef(preferences.data);latestPreferences.current = preferences.data;
  const queries = useQueryClient(), previousPreferenceVersion = useRef<number | undefined>(undefined), refetchPreferences = preferences.refetch;
  useEffect(() => { const version = preferences.data?.version;if (version === undefined) return;if (previousPreferenceVersion.current !== undefined && previousPreferenceVersion.current !== version) void queries.invalidateQueries({ queryKey: ['menu'] });previousPreferenceVersion.current = version; }, [preferences.data?.version, queries]);
  type Confirmation = { name: string; matches: Allergen[]; resolve: (accepted: boolean) => void };
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null), pending = useRef<Confirmation | null>(null), acknowledged = useRef(new Map<string, Allergen[]>());
  const resolveConfirmation = (accepted: boolean) => { const active = pending.current;pending.current = null;setConfirmation(null);active?.resolve(accepted); };
  useEffect(() => () => { pending.current?.resolve(false);pending.current = null; }, []);
  const [meal, setMeal] = useState<Meal>('Lunch');
  const [state, setState] = useState(initialCart), [date, setDate] = useState(shanghaiDate), [busy, setBusy] = useState(true), [notice, setNotice] = useState<string | null>(null);
  const current = useRef(state), owner = useRef<string | null>(null), epoch = useRef(0), request = useRef<AbortController | null>(null);
  const inventory = useCurrentInventory(date), latestInventory = useRef(inventory);latestInventory.current = inventory;
  useEffect(() => { const next = mergeStock(current.current, inventory);if (next !== current.current) { current.current = next;setState(next); } }, [inventory]);
  const [checkout] = useState(() => new CheckoutSubmission());
  const refresh = useCallback(async () => {
    if (!user) return;
    request.current?.abort();const controller = new AbortController();request.current = controller;
    const generation = ++epoch.current;setBusy(true);
    const restored = owner.current === user.userId ? { draft: draftFromState(current.current), error: null } : loadDraft(user.userId);
    try {
      const result = await reconcileDraft(restored.draft, async id => {
        try { return await menuDish(date, id, controller.signal); } catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error; }
      });
      if (generation !== epoch.current || controller.signal.aborted) return;
      owner.current = user.userId;current.current = mergeStock(result.state, latestInventory.current);setState(current.current);setNotice(restored.error);setBusy(false);
    } catch {
      if (generation === epoch.current && !controller.signal.aborted) setNotice('Could not check the saved cart. Try again before adding items or checking out.');
    }
  }, [user?.userId, date]);
  useEffect(() => {
    void refresh();const focus = () => { void refresh();void refetchPreferences(); };window.addEventListener('focus', focus);
    const unsubscribe = onSessionEnd(() => { epoch.current++;request.current?.abort();owner.current = null;setMeal('Lunch');resolveConfirmation(false);acknowledged.current.clear();current.current = initialCart;setState(initialCart); });
    return () => { epoch.current++;request.current?.abort();window.removeEventListener('focus', focus);unsubscribe(); };
  }, [refresh, refetchPreferences]);
  const dispatch = async (action: CartAction): Promise<boolean> => {
    if (!user || (busy && action.type !== 'submitted') || owner.current !== user.userId || (pending.current && action.type !== 'submitted')) return false;
    const stockAction = (value: CartAction): CartAction | null => {
      current.current = mergeStock(current.current, latestInventory.current);
      if (value.type !== 'add' && value.type !== 'edit') return value;
      const currentDish = latestInventory.current ? inventoryDish(latestInventory.current, value.dish) : value.dish;
      if (!currentDish) { setNotice('This dish is no longer available on this date.');return null; }
      return { ...value, dish: currentDish };
    };
    const prepared = stockAction(action);if (!prepared) return false;action = prepared;
    let dish: MenuDish | undefined, optionIds: string[] = [], sourceKey: string | undefined;
    if (action.type === 'add' || action.type === 'edit') { dish = action.dish;optionIds = action.optionIds;if (action.type === 'edit') sourceKey = action.key; }
    if (action.type === 'confirm') { const confirmedKey = action.key;const line = current.current.lines.find(item => item.key === confirmedKey);dish = line?.dish;optionIds = line?.optionIds ?? [];sourceKey = action.key; }
    let matches: Allergen[] = [], targetKey: string | undefined;
    if (dish) {
      if (!preferences.data || preferences.isError) { setNotice('Your preferences could not be checked. Retry before changing this selection.');return false; }
      try { targetKey = configuration(dish, optionIds).key; } catch { const rejected = cartReducer(current.current, action);current.current = rejected;setState(rejected);return false; }
      const source = sourceKey ?? targetKey, previous = current.current.lines.some(line => line.key === source) ? acknowledged.current.get(source) ?? [] : [];
      matches = matchedAllergens(dish, optionIds, preferences.data.allergens);
      const newlyMatched = newAllergens(matches, previous), generation = epoch.current, preferenceVersion = preferences.data.version;
      if (newlyMatched.length) {
        const accepted = await new Promise<boolean>(resolve => { const prompt = { name: dish!.name, matches: newlyMatched, resolve };pending.current = prompt;setConfirmation(prompt); });
        if (!accepted) return false;
        if (generation !== epoch.current || latestPreferences.current?.version !== preferenceVersion) { setNotice('Your cart or preferences changed. Review the selection and try again.');return false; }
      }
    }
    const updated = stockAction(action);if (!updated) return false;action = updated;
    if (action.type === 'submitted') { resolveConfirmation(false);request.current?.abort();setBusy(false); }
    epoch.current++;const next = cartReducer(current.current, action);current.current = next;setState(next);
    if (next.error) return false;
    if (targetKey) acknowledged.current.set(targetKey, matches);
    for (const key of acknowledged.current.keys()) if (!next.lines.some(line => line.key === key)) acknowledged.current.delete(key);
    const error = saveDraft(user.userId, next);setNotice(error);return true;
  };
  const checking = busy || preferences.isPending || preferences.isError;
  return <Context.Provider value={{ state, date, meal, setMeal, setDate: value => { if (value === date) return;request.current?.abort();epoch.current++;setBusy(true);setDate(value); }, busy: checking, notice: preferences.isError ? 'Your preferences could not be loaded. Retry before adding items.' : notice, dispatch, refresh: () => { void refresh();if (preferences.isError) void preferences.refetch(); }, checkout }}>{children}{confirmation && <AllergenConfirmation name={confirmation.name} matches={confirmation.matches} resolve={resolveConfirmation} />}</Context.Provider>;
}
export function useCart(): Cart { const value = useContext(Context);if (!value) throw new Error('CartProvider is required.');return value; }

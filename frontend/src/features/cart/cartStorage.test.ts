import { beforeEach, expect, it } from 'vitest';
import { loadDraft, reconcileDraft, saveDraft } from './cartStorage';
import { cartReducer, initialCart } from './cartReducer';
import { pasta, spaghetti } from './cartFixtures';
const employeeA = '10000000-0000-4000-8000-000000000011', employeeB = '10000000-0000-4000-8000-000000000012';
beforeEach(() => localStorage.clear());
it('restores valid large option sets without imposing an undocumented 100-option limit', async () => {
  const options = Array.from({ length: 1001 }, (_, i) => ({ optionId: `20000000-0000-4000-8000-${String(i).padStart(12, '0')}`, name: `Extra ${String(i).padStart(4, '0')} ${'a'.repeat(88)}`, extraCents: 0, allergens: [] }));
  const dish = { ...pasta, optionGroups: [{ groupId: '30000000-0000-4000-8000-000000000001', name: 'Extras', required: false, selectionMode: 'Multiple' as const, options }] };
  const state = cartReducer(initialCart, { type: 'add', dish, optionIds: options.map(o => o.optionId) });expect(state.error).toBeNull();expect(saveDraft(employeeA, state)).toBeNull();expect(localStorage.getItem(`webox.cart.v1:${employeeA}`)!.length).toBeGreaterThan(100_000);
  const restored = loadDraft(employeeA);expect(restored.error).toBeNull();expect(restored.draft.lines[0].selectedOptionIds).toHaveLength(1001);expect((await reconcileDraft(restored.draft, async () => dish)).state.lines[0].issue).toBeUndefined();
});
it('keeps drafts by confirmed account, survives reload and stores no credentials or checkout context', () => {
  const state = cartReducer(initialCart, { type: 'add', dish: pasta, optionIds: [spaghetti] });
  expect(saveDraft(employeeA, state)).toBeNull();expect(loadDraft(employeeA).draft.lines[0].quantity).toBe(1);
  expect(loadDraft(employeeB).draft.lines).toEqual([]);
  const persisted = JSON.parse(localStorage.getItem(`webox.cart.v1:${employeeA}`)!);
  expect(Object.keys(persisted).sort()).toEqual(['lines', 'version']);
  expect(Object.keys(persisted.lines[0]).sort()).toEqual(['dishId', 'dishVersion', 'quantity', 'selectedOptionIds', 'snapshot']);
  expect(JSON.stringify(persisted)).not.toMatch(/password|token|address|quote|preferenceMatch/);
});
it('rejects malformed, unsupported and impossible drafts with a visible recovery reason', () => {
  for (const value of ['{', JSON.stringify({ version: 99, lines: [] }), JSON.stringify({ version: 1, lines: [{ quantity: 6 }] })]) {
    localStorage.setItem(`webox.cart.v1:${employeeA}`, value);expect(loadDraft(employeeA).draft.lines).toEqual([]);expect(loadDraft(employeeA).error).toBeTruthy();
  }
});
it('revalidates current dishes and flags changed or unavailable items without submitting anything', async () => {
  saveDraft(employeeA, cartReducer(initialCart, { type: 'add', dish: pasta, optionIds: [spaghetti] }));
  const draft = loadDraft(employeeA).draft;
  expect((await reconcileDraft(draft, async () => ({ ...pasta, dishVersion: 2, basePriceCents: 3000 }))).changed).toContain(pasta.dishId);
  expect((await reconcileDraft(draft, async () => null)).unavailable).toContain(pasta.dishId);
});
it('keeps unconfirmed prices across reload, strips unknown fields and confirms explicitly', async () => {
  saveDraft(employeeA, cartReducer(initialCart, { type: 'add', dish: pasta, optionIds: [spaghetti] }));
  const draft = loadDraft(employeeA).draft;
  const changed = await reconcileDraft(draft, async () => ({ ...pasta, dishVersion: 2, basePriceCents: 3000 }));
  Object.assign(changed.state.lines[0].original!.snapshot, { token: 'must-not-persist' });
  saveDraft(employeeA, changed.state);expect(localStorage.getItem(`webox.cart.v1:${employeeA}`)).not.toContain('token');
  expect(loadDraft(employeeA).draft.lines[0].dishVersion).toBe(1);expect(loadDraft(employeeA).draft.lines[0].snapshot.unitPriceCents).toBe(2650);
  const confirmed = cartReducer(changed.state, { type: 'confirm', key: changed.state.lines[0].key });saveDraft(employeeA, confirmed);
  expect(loadDraft(employeeA).draft.lines[0].dishVersion).toBe(2);expect(loadDraft(employeeA).draft.lines[0].snapshot.unitPriceCents).toBe(3000);
});

import { expect, it } from 'vitest';
import { cartReducer, initialCart, totalCents, totalQuantity } from './cartReducer';
import { configuration } from './configuration';
import { pasta, spaghetti, fusilli, cheese } from './cartFixtures';
it('normalizes option order, merges matching configurations and totals two pasta at 5900 cents', () => {
  let state = cartReducer(initialCart, { type: 'add', dish: pasta, optionIds: [cheese, spaghetti] });
  state = cartReducer(state, { type: 'add', dish: pasta, optionIds: [spaghetti, cheese, cheese] });
  expect(state.lines).toHaveLength(1);expect(totalQuantity(state)).toBe(2);expect(totalCents(state)).toBe(5900);
  state = cartReducer(state, { type: 'add', dish: pasta, optionIds: [fusilli] });
  expect(state.lines).toHaveLength(2);expect(totalCents(state)).toBe(8550);
});
it('rejects missing required, multiple single choices and foreign options', () => {
  for (const optionIds of [[], [spaghetti, fusilli], [spaghetti, 'foreign']]) expect(() => configuration(pasta, optionIds)).toThrow();
  expect(configuration(pasta, [cheese], false).unitPriceCents).toBe(2950);
});
it('processes fast actions exactly once and caps all increases at five or stock', () => {
  let state = initialCart;
  for (let i = 0; i < 6; i++) state = cartReducer(state, { type: 'add', dish: pasta, optionIds: [spaghetti] });
  expect(totalQuantity(state)).toBe(5);expect(state.error).toMatch(/5/);
  state = cartReducer(state, { type: 'decrease', key: state.lines[0].key });expect(totalQuantity(state)).toBe(4);
  state = cartReducer(state, { type: 'add', dish: pasta, optionIds: [spaghetti] });expect(totalQuantity(state)).toBe(5);
  state = cartReducer(state, { type: 'remove', key: state.lines[0].key });expect(totalQuantity(state)).toBe(0);
  state = cartReducer(state, { type: 'add', dish: { ...pasta, remaining: 0 }, optionIds: [spaghetti] });expect(totalQuantity(state)).toBe(0);expect(state.error).toBeTruthy();
});
it('removes only submitted quantities while keeping additions made during submission', () => {
  let state = cartReducer(initialCart, { type: 'add', dish: pasta, optionIds: [spaghetti] });const submitted = [{ key: state.lines[0].key, quantity: 1 }];
  state = cartReducer(state, { type: 'add', dish: pasta, optionIds: [spaghetti] });
  state = cartReducer(state, { type: 'submitted', lines: submitted });expect(totalQuantity(state)).toBe(1);
});
it('rejects unsafe total arithmetic', () => {
  expect(() => configuration({ ...pasta, basePriceCents: Number.MAX_SAFE_INTEGER }, [spaghetti, cheese])).toThrow();
  const expensive = { ...pasta, basePriceCents: Number.MAX_SAFE_INTEGER };
  const state = cartReducer(initialCart, { type: 'add', dish: expensive, optionIds: [spaghetti] });
  const rejected = cartReducer(state, { type: 'add', dish: expensive, optionIds: [spaghetti] });
  expect(totalQuantity(rejected)).toBe(1);expect(rejected.error).toMatch(/limit/);
});
it('applies the stock limit across different configurations without changing existing prices', () => {
  const limited = { ...pasta, remaining: 1 };
  const first = cartReducer(initialCart, { type: 'add', dish: limited, optionIds: [spaghetti] });
  const blocked = cartReducer(first, { type: 'add', dish: limited, optionIds: [fusilli] });
  expect(totalQuantity(blocked)).toBe(1);expect(blocked.error).toMatch(/Only 1/);
  const changed = cartReducer(first, { type: 'add', dish: { ...pasta, dishVersion: 2, basePriceCents: 3000 }, optionIds: [spaghetti] });
  expect(changed.error).toMatch(/Review/);expect(totalCents(changed)).toBe(2650);
});
it('edits a complete configuration atomically, retaining its quantity and merging into an existing configuration', () => {
  let state = cartReducer(initialCart, { type: 'add', dish: pasta, optionIds: [spaghetti] });state = cartReducer(state, { type: 'add', dish: pasta, optionIds: [spaghetti] });
  state = cartReducer(state, { type: 'add', dish: pasta, optionIds: [fusilli, cheese] });const key = state.lines[0].key;
  const invalid = cartReducer(state, { type: 'edit', key, dish: pasta, optionIds: [] });expect(invalid.lines).toEqual(state.lines);expect(invalid.error).toBeTruthy();
  const updated = cartReducer(state, { type: 'edit', key, dish: pasta, optionIds: [fusilli, cheese] });expect(updated.lines).toHaveLength(1);expect(updated.lines[0].quantity).toBe(3);expect(totalCents(updated)).toBe(8850);
});

import { afterEach, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from '../../app/App';
import { pasta, spaghetti, cheese } from '../cart/cartFixtures';
import { budgetRange, matchedAllergens, newAllergens } from './preferenceRules';
afterEach(() => vi.unstubAllGlobals());
it('warns only about the base and selected options, and only newly matched allergens need confirmation', () => {
  expect(matchedAllergens(pasta, [spaghetti], ['Dairy', 'Egg'])).toEqual([]);
  expect(matchedAllergens(pasta, [spaghetti, cheese], ['Dairy', 'Egg'])).toEqual(['Dairy']);
  expect(matchedAllergens({ ...pasta, allergens: ['Egg'] }, [spaghetti, cheese], ['Dairy', 'Egg'])).toEqual(['Dairy', 'Egg']);
  expect(newAllergens(['Dairy', 'Egg'], ['Dairy'])).toEqual(['Egg']);expect(newAllergens(['Dairy'], ['Dairy'])).toEqual([]);
});
it('accepts paired exact-cent budgets or a full clear, rejecting partial, reversed and unsafe values', () => {
  expect(budgetRange('', '')).toEqual({ budgetMinCents: null, budgetMaxCents: null });expect(budgetRange('0', '30.01')).toEqual({ budgetMinCents: 0, budgetMaxCents: 3001 });
  for (const [min, max] of [['', '30'], ['30', ''], ['31', '30'], ['-1', '30'], ['0', '30.001'], ['0', '90071992547410']]) expect(() => budgetRange(min, max)).toThrow();
});
it('loads the signed-in user preferences into an editable settings form', async () => {
  history.replaceState(null, '', '/settings');localStorage.clear();
  vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(url.endsWith('/auth/me') ? { userId: 'd65a8dfd-51f2-4009-88b0-ddd16a2c14de', email: 'employee@example.com', role: 'Employee' } : { allergens: ['Egg'], cuisines: ['Japanese'], spiceLevel: 'None', flavor: 'Light', budgetMinCents: 1500, budgetMaxCents: 3000, version: 1 }), { status: 200 })));
  render(<App />);expect(await screen.findByLabelText('Egg')).toBeChecked();expect(screen.getByLabelText('Maximum budget (CNY)')).toHaveValue('30.00');expect(screen.getByRole('button', { name: 'Save preferences' })).toBeEnabled();
});

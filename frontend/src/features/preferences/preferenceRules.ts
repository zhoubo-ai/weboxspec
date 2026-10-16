import type { Allergen, MenuDish } from '../../shared/api/types';
import { parseMoney } from '../../shared/values';
export function matchedAllergens(dish: MenuDish, options: string[], flagged: Allergen[]): Allergen[] {
  const declared = new Set([...dish.allergens, ...dish.optionGroups.flatMap(group => group.options.filter(option => options.includes(option.optionId)).flatMap(option => option.allergens))]);
  return [...new Set(flagged)].filter(allergen => declared.has(allergen)).sort();
}
export function newAllergens(matches: Allergen[], acknowledged: Allergen[]): Allergen[] { return matches.filter(allergen => !acknowledged.includes(allergen)); }
export function budgetRange(min: string, max: string): { budgetMinCents: number | null; budgetMaxCents: number | null } {
  if (!min.trim() && !max.trim()) return { budgetMinCents: null, budgetMaxCents: null };
  if (!min.trim() || !max.trim()) throw new Error('Set both budget limits, or clear both.');
  const budgetMinCents = parseMoney(min.trim()), budgetMaxCents = parseMoney(max.trim());
  if (budgetMinCents > budgetMaxCents) throw new Error('Maximum budget must be at least the minimum budget.');
  return { budgetMinCents, budgetMaxCents };
}

import type { MenuDish } from '../../shared/api/types';
import { safeInteger } from '../../shared/values';
export function configuration(dish: MenuDish, selected: string[], requireComplete = true): { optionIds: string[]; unitPriceCents: number; key: string } {
  const optionIds = [...new Set(selected.map(id => id.toLowerCase()))].sort();
  const remaining = new Set(optionIds);
  let price = BigInt(safeInteger(dish.basePriceCents));
  for (const group of dish.optionGroups) {
    const options = group.options.filter(option => remaining.has(option.optionId));
    if (requireComplete && group.required && !options.length) throw new Error(`Choose an option for ${group.name}.`);
    if (group.selectionMode === 'Single' && options.length > 1) throw new Error(`Choose only one option for ${group.name}.`);
    for (const option of options) { remaining.delete(option.optionId); price += BigInt(safeInteger(option.extraCents)); }
  }
  if (remaining.size) throw new Error('An option is no longer available. Choose your options again.');
  if (price > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('This amount exceeds the supported limit.');
  return { optionIds, unitPriceCents: Number(price), key: JSON.stringify([dish.dishId, ...optionIds]) };
}

import type { MenuDish } from '../../shared/api/types';
import type { CartState } from './cartReducer';
export function purchaseAvailability(state: CartState, dish: MenuDish): { canAdd: boolean; reason: string | null } {
  let reason: string | null = null;
  const quantity = state.lines.reduce((sum, line) => sum + line.quantity, 0);
  const sameDish = state.lines.filter(line => line.dish.dishId === dish.dishId), count = sameDish.reduce((sum, line) => sum + line.quantity, 0);
  if (quantity >= 5) reason = 'You can add up to 5 portions per order.';
  else if (sameDish.some(line => line.issue || line.dish.dishVersion !== dish.dishVersion)) reason = 'Review the changed item in your cart before adding more.';
  else if (dish.remaining === 0) reason = 'Sold Out. Choose another dish.';
  else if (count >= dish.remaining) reason = `Only ${dish.remaining} portions are available for this dish.`;
  return { canAdd: reason === null, reason };
}

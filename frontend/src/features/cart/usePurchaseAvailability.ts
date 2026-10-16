import type { MenuDish } from '../../shared/api/types';
import { useCurrentInventory } from '../inventory/useInventoryStream';
import { inventoryDish } from '../inventory/inventoryState';
import { useCart } from './CartProvider';
import { purchaseAvailability } from './purchaseAvailability';
export function usePurchaseAvailability(dish: MenuDish, editingKey?: string) { const cart = useCart();const inventory = useCurrentInventory(cart.date), current = inventory ? inventoryDish(inventory, dish) : dish;const state = editingKey ? { ...cart.state, lines: cart.state.lines.filter(line => line.key !== editingKey) } : cart.state;const availability = current ? purchaseAvailability(state, current) : { canAdd: false, reason: 'This dish is no longer available on this date.' };return cart.busy ? { canAdd: false, reason: 'Checking current prices and availability…' } : availability; }

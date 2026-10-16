import { useEffect, useRef, useState } from 'react';
import type { MenuDish } from '../../shared/api/types';
import { money } from '../../shared/values';
import { configuration } from '../cart/configuration';
import { usePurchaseAvailability } from '../cart/usePurchaseAvailability';
import { useCart } from '../cart/CartProvider';
import { StockBadge } from '../inventory/StockBadge';
import { DishImage } from './DishImage';
import type { CartLine } from '../cart/cartReducer';
export function DishDialog({ dish, close, restoreFocus, editing }: { dish: MenuDish; close: () => void; restoreFocus?: HTMLElement | null; editing?: CartLine }) {
  const dialog = useRef<HTMLDialogElement>(null), cart = useCart();
  const availability = usePurchaseAvailability(dish, editing?.key);
  const [selected, setSelected] = useState<string[]>(editing?.optionIds ?? []), [added, setAdded] = useState(false);
  useEffect(() => { const previous = restoreFocus ?? document.activeElement, element = dialog.current;element?.showModal();return () => { element?.close();if (previous instanceof HTMLElement) previous.focus(); }; }, [restoreFocus]);
  let price = dish.basePriceCents, reason = '';
  try { price = configuration(dish, selected, false).unitPriceCents;configuration(dish, selected); } catch (error) { reason = (error as Error).message; }
  if (!availability.canAdd) reason = availability.reason!;
  return <dialog ref={dialog} className="dish-dialog" aria-labelledby="dish-title" onCancel={close}>
    <button type="button" className="secondary dialog-close" onClick={close} aria-label="Close dish details">Close</button>
    <DishImage src={dish.imageUrl} name={dish.name} /><p className="eyebrow">{dish.category}</p><h2 id="dish-title">{dish.name}</h2><p>{dish.description}</p>
    <dl className="dish-facts"><div><dt>Protein</dt><dd>{dish.protein}</dd></div><div><dt>Spice</dt><dd>{dish.spiceLevel}</dd></div><div><dt>Declared allergens</dt><dd>{dish.allergens.join(', ') || 'None declared'}</dd></div></dl>
    <p className="form-hint">Allergen information is provided for your reference. Unmarked ingredients are not a guarantee of safety.</p>
    {dish.optionGroups.map(group => <fieldset key={group.groupId}><legend>{group.name} · {group.required ? 'Required' : 'Optional'}{group.selectionMode === 'Multiple' ? ' · Choose multiple' : ' · Choose one'}</legend>{group.options.map(option => <label className="option-row" key={option.optionId}><input type={group.selectionMode === 'Single' ? 'radio' : 'checkbox'} name={group.groupId} checked={selected.includes(option.optionId)} onChange={event => {
      setAdded(false);setSelected(previous => { const others = group.selectionMode === 'Single' ? previous.filter(id => !group.options.some(item => item.optionId === id)) : previous.filter(id => id !== option.optionId);return event.target.checked ? [...others, option.optionId] : others; });
    }} /><span>{option.name}<small>{option.allergens.length ? `Contains: ${option.allergens.join(', ')}` : 'No allergens declared'}</small></span><strong>+¥{money(option.extraCents)}</strong></label>)}{!group.required && group.selectionMode === 'Single' && <button type="button" className="secondary" onClick={() => setSelected(previous => previous.filter(id => !group.options.some(option => option.optionId === id)))}>Clear {group.name}</button>}</fieldset>)}
    {editing && <p>Editing {editing.quantity} {editing.quantity === 1 ? 'portion' : 'portions'}. Your quantity will stay the same.</p>}
    <div className="dish-actions"><strong aria-live="polite">Unit price: ¥{money(price)}</strong><StockBadge remaining={dish.remaining} /><button type="button" disabled={Boolean(reason) || cart.busy} onClick={async () => { const changed = await cart.dispatch(editing ? { type: 'edit', key: editing.key, dish, optionIds: selected } : { type: 'add', dish, optionIds: selected });setAdded(changed);if (changed && editing) close(); }}>{editing ? 'Save changes' : 'Add to cart'}</button></div>
    {reason && <p className="form-hint" role="status">{reason}</p>}{cart.state.error && <p role="alert">{cart.state.error}</p>}{added && !cart.state.error && <p role="status">Added to cart.</p>}
  </dialog>;
}
